CREATE TABLE IF NOT EXISTS metric_definitions
(
    id   UUID PRIMARY KEY    NOT NULL DEFAULT gen_random_uuid(),
    key  VARCHAR(255) UNIQUE NOT NULL CHECK (length(btrim(key)) > 0),
    name VARCHAR(255)        NOT NULL CHECK (length(btrim(name)) > 0),
    type VARCHAR(16)         NOT NULL CHECK (type IN ('UNIQUE', 'COUNTABLE'))
);

CREATE TABLE IF NOT EXISTS metric_events
(
    id         UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID             NOT NULL,
    metric_key VARCHAR(255)     NOT NULL REFERENCES metric_definitions (key),
    timestamp  TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS metric_events_user_id_metric_key_timestamp_idx
    ON metric_events (user_id, metric_key, timestamp);

CREATE TABLE IF NOT EXISTS assignment_events
(
    id            UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID             NOT NULL,
    variant_id    UUID             NOT NULL REFERENCES experiment_variants (id),
    experiment_id UUID             NOT NULL REFERENCES experiments (id),
    timestamp     TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS assignment_events_experiment_id_user_id_timestamp_id_idx
    ON assignment_events (experiment_id, user_id, timestamp, id);
