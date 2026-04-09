ALTER TABLE experiment_variants
    DROP CONSTRAINT IF EXISTS experiment_variants_weight_positive_check;

ALTER TABLE experiment_variants
    ALTER COLUMN weight DROP NOT NULL;

UPDATE experiment_variants
SET weight = NULL
WHERE variant_type = 'CONTROL';

ALTER TABLE experiment_variants
    ADD CONSTRAINT experiment_variants_weight_check
        CHECK (
            (variant_type = 'CONTROL' AND weight IS NULL)
                OR (variant_type = 'REGULAR' AND weight IS NOT NULL AND weight > 0)
            );
