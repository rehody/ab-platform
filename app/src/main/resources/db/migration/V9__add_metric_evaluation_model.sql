ALTER TABLE metric_definitions
    ADD COLUMN direction           VARCHAR(32)    NOT NULL DEFAULT 'MORE_IS_BETTER'
        CHECK (direction IN ('MORE_IS_BETTER', 'LESS_IS_BETTER')),

    ADD COLUMN severity            VARCHAR(16)    NOT NULL DEFAULT 'MEDIUM'
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'EXTREME')),

    ADD COLUMN deviation_threshold NUMERIC(10, 4) NOT NULL DEFAULT 0.1000
        CHECK (deviation_threshold > 0);

CREATE TABLE experiment_metrics
(
    experiment_id UUID         NOT NULL REFERENCES experiments (id),
    metric_key    VARCHAR(255) NOT NULL REFERENCES metric_definitions (key),
    PRIMARY KEY (experiment_id, metric_key)
);

CREATE INDEX experiment_metrics_metric_key_idx
    ON experiment_metrics (metric_key);

CREATE TABLE experiment_metric_risks
(
    id                  UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
    experiment_id       UUID             NOT NULL REFERENCES experiments (id),
    metric_key          VARCHAR(255)     NOT NULL REFERENCES metric_definitions (key),
    variant_id          UUID             NOT NULL REFERENCES experiment_variants (id),
    status              VARCHAR(16)      NOT NULL CHECK (status IN ('OPEN', 'RESOLVED')),
    opened_at           TIMESTAMPTZ      NOT NULL,
    resolved_at         TIMESTAMPTZ,
    resolution_comment  TEXT,
    last_evaluated_at   TIMESTAMPTZ      NOT NULL,
    last_bad_deviation  NUMERIC(10, 4)   NOT NULL CHECK (last_bad_deviation >= 0),
    worst_bad_deviation NUMERIC(10, 4)   NOT NULL CHECK (worst_bad_deviation >= 0),
    auto_paused_at      TIMESTAMPTZ,
    CONSTRAINT experiment_metric_risks_unique UNIQUE (experiment_id, metric_key, variant_id)
);

CREATE INDEX experiment_metric_risks_experiment_metric_idx
    ON experiment_metric_risks (experiment_id, metric_key);
