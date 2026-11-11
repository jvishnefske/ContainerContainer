CREATE TABLE repository_stats
(
    repository_key            VARCHAR(64)   NOT NULL,
    last_downloaded           BIGINT        DEFAULT 0,
    last_uploaded             BIGINT        DEFAULT 0,
    last_statistics_updated   BIGINT        NOT NULL,
    CONSTRAINT repository_stats_pk PRIMARY KEY (repository_key),
    CONSTRAINT repository_stats_repository_key_fk FOREIGN KEY (repository_key) REFERENCES repository_config (repository_key) ON DELETE CASCADE
);

CREATE INDEX idx_repository_stats_last_statistics_updated ON repository_stats (last_statistics_updated);
