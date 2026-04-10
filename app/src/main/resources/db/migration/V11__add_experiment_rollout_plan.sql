ALTER TABLE experiments
    ADD COLUMN IF NOT EXISTS regular_rollout_percentage INT NOT NULL DEFAULT 5;

ALTER TABLE experiments
    ADD COLUMN IF NOT EXISTS after_rollback BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE experiments
    ADD COLUMN IF NOT EXISTS still_negative_after_rollback BOOLEAN NOT NULL DEFAULT FALSE;

DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1
                       FROM information_schema.table_constraints
                       WHERE table_schema = current_schema()
                         AND table_name = 'experiments'
                         AND constraint_name = 'experiment_regular_rollout_percentage_check') THEN
            ALTER TABLE experiments
                ADD CONSTRAINT experiment_regular_rollout_percentage_check
                    CHECK (regular_rollout_percentage IN (5, 15, 30, 50, 75, 100));
        END IF;
    END
$$;
