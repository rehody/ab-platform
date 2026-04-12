CREATE TABLE audit_events
(
    id                UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
    action            VARCHAR(128)     NOT NULL,
    actor_type        VARCHAR(32)      NOT NULL,
    user_actor_id     UUID,
    system_actor_name VARCHAR(128),
    target_type       VARCHAR(128)     NOT NULL,
    target_id         UUID             NOT NULL,
    event_timestamp   TIMESTAMPTZ      NOT NULL,
    details           JSONB            NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT audit_events_actor_check CHECK (
        (actor_type = 'USER' AND user_actor_id IS NOT NULL AND system_actor_name IS NULL)
            OR
        (actor_type = 'SYSTEM' AND user_actor_id IS NULL AND system_actor_name IS NOT NULL)
        )
);

CREATE INDEX audit_events_timestamp_idx
    ON audit_events (event_timestamp DESC);

CREATE INDEX audit_events_target_idx
    ON audit_events (target_type, target_id, event_timestamp DESC);
