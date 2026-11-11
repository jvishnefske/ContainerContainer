CREATE TABLE config_history
(
    id               NUMBER(19, 0)  GENERATED ALWAYS AS IDENTITY,
    config_data      BLOB         NOT NULL,
    repo_config_data BLOB         NOT NULL,
    log_config_data  BLOB         NOT NULL,
    CONSTRAINT config_history_pk PRIMARY KEY (id)
);
