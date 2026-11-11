CREATE TABLE system_states (
    use_case_name  VARCHAR(255)  NOT NULL,
    id             VARCHAR(255)  NOT NULL,
    timestamp      BIGINT        NOT NULL,
    node_id        VARCHAR(255)  NOT NULL,
    actor          VARCHAR(255),
    data           TEXT,
    CONSTRAINT system_states_pk PRIMARY KEY (use_case_name, id)
);
CREATE INDEX system_states_uc_ts_idx ON system_states (use_case_name, timestamp);
