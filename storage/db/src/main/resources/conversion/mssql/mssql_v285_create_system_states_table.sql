CREATE TABLE system_states (
    use_case_name  NVARCHAR(255) NOT NULL,
    id             NVARCHAR(255) NOT NULL,
    timestamp      BIGINT        NOT NULL,
    node_id        NVARCHAR(255) NOT NULL,
    actor          NVARCHAR(255),
    data           NVARCHAR(MAX),
    CONSTRAINT system_states_pk PRIMARY KEY NONCLUSTERED (use_case_name, id)
);
CREATE INDEX system_states_uc_ts_idx ON system_states (use_case_name, timestamp);
