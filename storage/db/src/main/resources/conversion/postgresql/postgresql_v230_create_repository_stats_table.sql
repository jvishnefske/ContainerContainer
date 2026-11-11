CREATE TABLE repository_stats
(
    repository_key       VARCHAR(64)   NOT NULL,
    last_modified        BIGINT        NOT NULL,
    CONSTRAINT repository_stats_pk PRIMARY KEY (repository_key),
    CONSTRAINT repository_stats_repository_key_fk FOREIGN KEY (repository_key) REFERENCES repository_config (repository_key) ON DELETE CASCADE
);
