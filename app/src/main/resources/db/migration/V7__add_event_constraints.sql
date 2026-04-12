ALTER TABLE assignment_events
    ADD CONSTRAINT assignment_events_experiment_user_unique
        UNIQUE (experiment_id, user_id);

CREATE INDEX assignment_events_experiment_variant_idx
    ON assignment_events (experiment_id, variant_id);

CREATE INDEX metric_events_metric_key_user_timestamp_idx
    ON metric_events (metric_key, user_id, timestamp);

ALTER TABLE experiments
    ADD CONSTRAINT experiments_completed_requires_started_check
        CHECK (completed_at IS NULL OR started_at IS NOT NULL);

ALTER TABLE experiments
    ADD CONSTRAINT experiments_completed_after_started_check
        CHECK (completed_at IS NULL OR completed_at > started_at);

ALTER TABLE experiment_variants
    ADD CONSTRAINT experiment_variants_weight_positive_check
        CHECK (weight > 0);
