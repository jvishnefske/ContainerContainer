CREATE TABLE version_history
(
    id                  NUMBER(19, 0)   GENERATED ALWAYS AS IDENTITY,
    version             VARCHAR(255)    NOT NULL,
    config_version_id   NUMBER(19, 0)   NOT NULL,
    backward_compatible NUMBER (1,0)    DEFAULT 0,
    created             NUMBER(19, 0)   NOT NULL,
    CONSTRAINT version_history_pk PRIMARY KEY (id),
    CONSTRAINT version_history_config_fk FOREIGN KEY (config_version_id)
        REFERENCES config_history (id)
);