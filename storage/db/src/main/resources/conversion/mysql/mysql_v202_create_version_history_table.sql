CREATE TABLE version_history
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    version             VARCHAR(255) NOT NULL,
    config_version_id   BIGINT       NOT NULL,
    backward_compatible SMALLINT     DEFAULT 0,
    created             BIGINT       NOT NULL,
    CONSTRAINT version_history_pk PRIMARY KEY (id),
    CONSTRAINT version_history_config_fk FOREIGN KEY (config_version_id)
        REFERENCES config_history (id)
);