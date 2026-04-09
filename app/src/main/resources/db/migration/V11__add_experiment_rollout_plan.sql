ALTER TABLE experiments
    ADD COLUMN IF NOT EXISTS regular_rollout_percentage INT NOT NULL DEFAULT 5;

ALTER TABLE experiments
    ADD COLUMN IF NOT EXISTS is_in_rollback_state BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE experiments
    ADD COLUMN IF NOT EXISTS repeated_negative_evaluation_after_rollback BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE experiments
    ADD CONSTRAINT experiment_regular_rollout_percentage_check
        CHECK ( regular_rollout_percentage IN (5, 15, 30, 50, 75, 100) );
