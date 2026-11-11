CREATE TABLE config_history
(
    id               BIGSERIAL,
    config_data      bytea        NOT NULL,
    repo_config_data bytea        NOT NULL,
    log_config_data  bytea        NOT NULL,
    CONSTRAINT config_history_pk PRIMARY KEY (id)
);
