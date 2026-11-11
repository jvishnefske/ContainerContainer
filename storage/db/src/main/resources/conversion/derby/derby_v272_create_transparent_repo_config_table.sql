CREATE TABLE transparent_repo_config
(
    repository_key VARCHAR(128) NOT NULL,
    type           VARCHAR(32) NOT NULL,
    package_type   VARCHAR(32) NOT NULL,
    revision       BIGINT      NOT NULL,
    created        BIGINT      NOT NULL,
    modified       BIGINT      NOT NULL,
    config_blob    BLOB,
    CONSTRAINT transparent_repo_key_pk PRIMARY KEY (repository_key)
);
