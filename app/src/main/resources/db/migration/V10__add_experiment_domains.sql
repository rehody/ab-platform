CREATE TABLE IF NOT EXISTS experiment_domains
(
    key       VARCHAR(64) PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO experiment_domains (key, name)
VALUES ('LEGACY', 'Legacy'),
       ('AUTH', 'Auth'),
       ('ONBOARDING', 'Onboarding'),
       ('CATALOG', 'Catalog'),
       ('SEARCH', 'Search'),
       ('RECOMMENDATIONS', 'Recommendations'),
       ('CART', 'Cart'),
       ('CHECKOUT', 'Checkout'),
       ('PAYMENTS', 'Payments'),
       ('ORDERS', 'Orders'),
       ('PROFILE', 'Profile'),
       ('NOTIFICATIONS', 'Notifications'),
       ('CONTENT_FEED', 'Content feed'),
       ('ADS', 'Ads'),
       ('SUBSCRIPTIONS', 'Subscriptions'),
       ('SUPPORT', 'Support')
ON CONFLICT (key) DO NOTHING;

ALTER TABLE experiments
    ADD COLUMN IF NOT EXISTS domain_key VARCHAR(64);

UPDATE experiments
SET domain_key = 'LEGACY'
WHERE domain_key IS NULL;

ALTER TABLE experiments
    ALTER COLUMN domain_key SET NOT NULL;

DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1
                       FROM information_schema.table_constraints
                       WHERE table_name = 'experiments'
                         AND constraint_name = 'experiments_domain_key_constraint') THEN
            ALTER TABLE experiments
                ADD CONSTRAINT experiments_domain_key_constraint
                    FOREIGN KEY (domain_key) REFERENCES experiment_domains (key);
        END IF;
    END;
$$;

CREATE INDEX IF NOT EXISTS experiments_domain_key_idx
    ON experiments (domain_key);

DROP TRIGGER IF EXISTS experiment_domains_set_updated_at
    ON experiment_domains;
