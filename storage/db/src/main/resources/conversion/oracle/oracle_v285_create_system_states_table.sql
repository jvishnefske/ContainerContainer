CREATE TABLE system_states (
    use_case_name  VARCHAR2(255)  NOT NULL,
    id             VARCHAR2(255)  NOT NULL,
    timestamp      NUMBER(19, 0)  NOT NULL,
    node_id        VARCHAR2(255)  NOT NULL,
    actor          VARCHAR2(255),
    data           CLOB,
    CONSTRAINT system_states_pk PRIMARY KEY (use_case_name, id)
);
CREATE INDEX system_states_uc_ts_idx ON system_states (use_case_name, timestamp);
