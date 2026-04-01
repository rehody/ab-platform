-- Добавляем тип варианта. Сначала nullable, чтобы спокойно заполнить старые данные.
ALTER TABLE experiment_variants
    ADD COLUMN variant_type VARCHAR(16);

-- Приводим значения к одной форме, чтобы STRING, BOOL и NUMBER сравнивались одинаково.
CREATE FUNCTION normalize_feature_value(raw_value VARCHAR, raw_type VARCHAR)
    RETURNS TEXT
    LANGUAGE SQL
    IMMUTABLE
    RETURNS NULL ON NULL INPUT
AS
$$
SELECT CASE raw_type
           WHEN 'STRING' THEN raw_value
           WHEN 'BOOL' THEN (raw_value::boolean)::text
           WHEN 'NUMBER' THEN trim(trailing '.' FROM trim(trailing '0' FROM (raw_value::numeric)::text))
           END
$$;

-- Подтягиваем default value флага для каждого experiment.
CREATE TEMP TABLE flag_defaults ON COMMIT DROP AS
SELECT e.id AS experiment_id,
       ff.default_value,
       ff.default_value_type
FROM experiments e
         JOIN feature_flags ff
              ON ff.feature_key = e.flag_key;

-- Ищем варианты, которые уже совпадают с default value и могут стать CONTROL.
CREATE TEMP TABLE control_matches ON COMMIT DROP AS
SELECT fd.experiment_id,
       ev.id AS variant_id
FROM flag_defaults fd
         JOIN experiment_variants ev
              ON ev.experiment_id = fd.experiment_id
                  AND ev.value_type = fd.default_value_type
                  AND normalize_feature_value(ev.value, ev.value_type) =
                      normalize_feature_value(fd.default_value, fd.default_value_type);

-- Готовим данные для автозаполнения: куда ставить новый CONTROL и какой дать ему вес.
CREATE TEMP TABLE variant_stats ON COMMIT DROP AS
SELECT fd.experiment_id,
       COUNT(ev.id)                           AS variant_count,
       COALESCE(MAX(ev.position), -1) + 1     AS next_position,
       CAST(AVG(ev.weight) AS NUMERIC(10, 4)) AS average_weight
FROM flag_defaults fd
         LEFT JOIN experiment_variants ev
                   ON ev.experiment_id = fd.experiment_id
GROUP BY fd.experiment_id;

-- Ловим дубли по значению внутри одного experiment.
DO
$$
    DECLARE
        conflicting_experiment_id UUID;
    BEGIN
        SELECT duplicate_values.experiment_id
        INTO conflicting_experiment_id
        FROM (SELECT experiment_id
              FROM experiment_variants
              GROUP BY experiment_id, value_type, normalize_feature_value(value, value_type)
              HAVING COUNT(*) > 1) duplicate_values
        LIMIT 1;

        IF conflicting_experiment_id IS NOT NULL THEN
            RAISE EXCEPTION 'Experiment % contains duplicate variant values', conflicting_experiment_id;
        END IF;
    END
$$;

-- Ловим случаи, когда под default value подходит сразу несколько вариантов.
DO
$$
    DECLARE
        conflicting_experiment_id UUID;
    BEGIN
        SELECT experiment_id
        INTO conflicting_experiment_id
        FROM control_matches
        GROUP BY experiment_id
        HAVING COUNT(*) > 1
        LIMIT 1;

        IF conflicting_experiment_id IS NOT NULL THEN
            RAISE EXCEPTION
                'Experiment % has multiple variants matching default flag value',
                conflicting_experiment_id;
        END IF;
    END
$$;

-- Если key=control уже занят не тем вариантом, который должен стать CONTROL, падаем сразу.
DO
$$
    DECLARE
        conflicting_experiment_id UUID;
    BEGIN
        SELECT ev.experiment_id
        INTO conflicting_experiment_id
        FROM experiment_variants ev
                 LEFT JOIN control_matches cm
                           ON cm.experiment_id = ev.experiment_id
        WHERE ev.key = 'control'
          AND (cm.variant_id IS NULL OR ev.id <> cm.variant_id)
        LIMIT 1;

        IF conflicting_experiment_id IS NOT NULL THEN
            RAISE EXCEPTION
                'Experiment % already contains key=control on non-control variant',
                conflicting_experiment_id;
        END IF;
    END
$$;

-- Автозаполнение возможно только там, где уже есть хотя бы один вариант.
DO
$$
    DECLARE
        conflicting_experiment_id UUID;
    BEGIN
        SELECT vs.experiment_id
        INTO conflicting_experiment_id
        FROM variant_stats vs
                 LEFT JOIN control_matches cm
                           ON cm.experiment_id = vs.experiment_id
        WHERE cm.variant_id IS NULL
          AND vs.variant_count = 0
        LIMIT 1;

        IF conflicting_experiment_id IS NOT NULL THEN
            RAISE EXCEPTION
                'Experiment % has no variants and cannot be migrated to CONTROL/REGULAR',
                conflicting_experiment_id;
        END IF;
    END
$$;

-- Сначала помечаем все существующие варианты как REGULAR.
-- noinspection SqlWithoutWhere
UPDATE experiment_variants
SET variant_type = 'REGULAR';

-- Совпавшие с default value варианты переводим в CONTROL и приводим key к control.
UPDATE experiment_variants ev
SET key          = 'control',
    variant_type = 'CONTROL'
FROM control_matches cm
WHERE ev.id = cm.variant_id;

-- Если подходящего варианта не было, добавляем новый CONTROL в конец списка.
INSERT INTO experiment_variants (id, experiment_id, key, value, value_type, position, weight, variant_type)
SELECT gen_random_uuid(),
       fd.experiment_id,
       'control',
       fd.default_value,
       fd.default_value_type,
       vs.next_position,
       vs.average_weight,
       'CONTROL'
FROM flag_defaults fd
         JOIN variant_stats vs
              ON vs.experiment_id = fd.experiment_id
         LEFT JOIN control_matches cm
                   ON cm.experiment_id = fd.experiment_id
WHERE cm.variant_id IS NULL;

-- После заполнения еще раз проверяем итоговую конфигурацию каждого experiment.
DO
$$
    DECLARE
        invalid_experiment_id UUID;
    BEGIN
        SELECT variant_counts.experiment_id
        INTO invalid_experiment_id
        FROM (SELECT experiment_id,
                     COUNT(*) FILTER (WHERE variant_type = 'CONTROL') AS control_count,
                     COUNT(*) FILTER (WHERE variant_type = 'REGULAR') AS regular_count
              FROM experiment_variants
              GROUP BY experiment_id) variant_counts
        WHERE control_count <> 1
           OR regular_count < 1
        LIMIT 1;

        IF invalid_experiment_id IS NOT NULL THEN
            RAISE EXCEPTION
                'Experiment % must contain exactly one CONTROL and at least one REGULAR variant after migration',
                invalid_experiment_id;
        END IF;
    END
$$;

-- Теперь можно зафиксировать новые инварианты на уровне схемы.
ALTER TABLE experiment_variants
    ALTER COLUMN variant_type SET NOT NULL;

ALTER TABLE experiment_variants
    ADD CONSTRAINT experiment_variants_variant_type_check
        CHECK (variant_type IN ('CONTROL', 'REGULAR'));

CREATE UNIQUE INDEX experiment_variants_single_control_per_experiment_idx
    ON experiment_variants (experiment_id)
    WHERE variant_type = 'CONTROL';

ALTER TABLE experiment_variants
    ADD CONSTRAINT experiment_variants_experiment_value_unique
        UNIQUE (experiment_id, value, value_type);

ALTER TABLE experiment_variants
    ADD CONSTRAINT experiment_variants_key_matches_type_check
        CHECK (
            (variant_type = 'CONTROL' AND key = 'control')
                OR (variant_type = 'REGULAR' AND key <> 'control')
            );

-- Временная функция больше не нужна.
DROP FUNCTION normalize_feature_value(VARCHAR, VARCHAR);
