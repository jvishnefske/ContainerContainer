CREATE TABLE config_history
(
    id               BIGINT         IDENTITY(1,1),
    config_data      VARBINARY(MAX) NOT NULL,
    repo_config_data VARBINARY(MAX) NOT NULL,
    log_config_data  VARBINARY(MAX) NOT NULL
    CONSTRAINT config_history_pk PRIMARY KEY(id)
);
