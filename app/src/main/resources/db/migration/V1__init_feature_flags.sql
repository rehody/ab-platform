CREATE TABLE IF NOT EXISTS feature_flags
(
    id                 UUID PRIMARY KEY    NOT NULL DEFAULT gen_random_uuid(),
    feature_key        VARCHAR(255) UNIQUE NOT NULL CHECK (length(btrim(feature_key)) > 0),
    default_value      TEXT                NOT NULL,
    default_value_type VARCHAR(16)         NOT NULL CHECK (default_value_type IN ('NUMBER', 'STRING', 'BOOL')),
    created_at         TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ         NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS feature_flag_key_idx ON feature_flags (feature_key);

CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER feature_flag_before_update
    BEFORE UPDATE
    ON feature_flags
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
