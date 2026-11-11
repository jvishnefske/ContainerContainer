CREATE TABLE transparent_repo_config
(
    repository_key VARCHAR2(128) NOT NULL,
    type           VARCHAR2(32)  NOT NULL,
    package_type   VARCHAR2(32)  NOT NULL,
    revision       NUMBER(19, 0) NOT NULL,
    created        NUMBER(19, 0) NOT NULL,
    modified       NUMBER(19, 0) NOT NULL,
    config_blob    BLOB,
    CONSTRAINT transparent_repo_key_pk PRIMARY KEY (repository_key)
);
