CREATE TABLE repo_migrations_history (
    job_id VARCHAR(64) PRIMARY KEY,
    repo_name VARCHAR(256) NOT NULL,
    target_repo VARCHAR(256),
    migration_status VARCHAR(64) NOT NULL,
    start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    CONSTRAINT fk_history_repo FOREIGN KEY (repo_name) REFERENCES repo_migrations(repo_name) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE INDEX idx_history_repo_name ON repo_migrations_history(repo_name, start_time DESC);
CREATE INDEX idx_history_status ON repo_migrations_history(migration_status);
