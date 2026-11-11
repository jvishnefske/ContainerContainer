CREATE TABLE policy
(
    policy_key  VARCHAR(255)       NOT NULL,
    policy_type VARCHAR(64)        NOT NULL,
    item_type   VARCHAR(64)        NOT NULL,
    enabled     SMALLINT DEFAULT 0 NOT NULL,
    created     BIGINT             NOT NULL,
    created_by  VARCHAR(64)        NOT NULL,
    config      BLOB               NOT NULL,
    modified    BIGINT,
    modified_by VARCHAR(64),
    cronExp     VARCHAR(64),
    description VARCHAR(1024),
    CONSTRAINT policy_pk PRIMARY KEY (policy_key)
);