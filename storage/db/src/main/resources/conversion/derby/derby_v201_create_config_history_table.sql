CREATE TABLE config_history
(
    id               BIGINT       GENERATED ALWAYS AS IDENTITY(START WITH 1, INCREMENT BY 1),
    config_data      BLOB         NOT NULL,
    repo_config_data BLOB         NOT NULL,
    log_config_data  BLOB         NOT NULL,
    CONSTRAINT config_history_pk PRIMARY KEY (id)
);
