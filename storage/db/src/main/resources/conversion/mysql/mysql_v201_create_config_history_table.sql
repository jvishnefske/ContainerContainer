CREATE TABLE config_history
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    config_data      LONGBLOB     NOT NULL,
    repo_config_data LONGBLOB     NOT NULL,
    log_config_data  LONGBLOB     NOT NULL,
    CONSTRAINT config_history_pk PRIMARY KEY (id)
);
