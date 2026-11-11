CREATE TABLE policy
(
    policy_key  VARCHAR(255)            NOT NULL,
    policy_type VARCHAR(64)             NOT NULL,
    item_type   VARCHAR(64)             NOT NULL,
    enabled     NUMBER(1, 0)  DEFAULT 0 NOT NULL,
    created     NUMBER(19, 0)           NOT NULL,
    created_by  VARCHAR2(64)            NOT NULL,
    config      BLOB                    NOT NULL,
    modified    NUMBER(19, 0),
    modified_by VARCHAR2(64),
    cronExp     VARCHAR(64),
    description VARCHAR(1024),
    CONSTRAINT policy_pk PRIMARY KEY (policy_key)
);