CREATE TABLE repository_config
(
    repository_key VARCHAR(64) NOT NULL,
    type           VARCHAR(32) NOT NULL,
    package_type   VARCHAR(32) NOT NULL,
    revision       BIGINT      NOT NULL,
    created        BIGINT      NOT NULL,
    modified       BIGINT      NOT NULL,
    config_blob    LONGBLOB,
    CONSTRAINT repo_key_pk PRIMARY KEY (repository_key)
);