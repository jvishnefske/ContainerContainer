CREATE TABLE version_history
(
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY(START WITH 1, INCREMENT BY 1),
    version             VARCHAR(255) NOT NULL,
    config_version_id   BIGINT       NOT NULL,
    backward_compatible SMALLINT     DEFAULT 0 NOT NULL,
    created             BIGINT       NOT NULL,
    CONSTRAINT version_history_pk PRIMARY KEY (id),
    CONSTRAINT version_history_config_fk FOREIGN KEY (config_version_id)
        REFERENCES config_history (id)
);