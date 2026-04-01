CREATE TABLE IF NOT EXISTS experiments
(
    id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    flag_key   VARCHAR(255) NOT NULL REFERENCES feature_flags (feature_key),
    state      VARCHAR(16)  NOT NULL CHECK ( state IN
                                             ('DRAFT', 'IN_REVIEW', 'REJECTED', 'APPROVED', 'RUNNING', 'PAUSED',
                                              'COMPLETED', 'ARCHIVED') ),
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS experiments_flag_key_idx ON experiments (flag_key);

CREATE TABLE IF NOT EXISTS experiment_variants
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    experiment_id UUID         NOT NULL REFERENCES experiments (id) ON DELETE CASCADE,
    key           VARCHAR(255) NOT NULL,
    value         TEXT         NOT NULL,
    value_type    VARCHAR(16)  NOT NULL CHECK ( value_type IN ('NUMBER', 'STRING', 'BOOL') ),
    position      INT          NOT NULL CHECK ( position >= 0 ),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (experiment_id, key),
    UNIQUE (experiment_id, position)
);

CREATE INDEX IF NOT EXISTS experiment_variants_key_idx ON experiment_variants (key);
CREATE INDEX IF NOT EXISTS experiment_variants_experiment_id_idx ON experiment_variants (experiment_id);

CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER experiments_before_update
    BEFORE UPDATE
    ON experiments
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE TRIGGER experiment_variants_before_update
    BEFORE UPDATE
    ON experiment_variants
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
