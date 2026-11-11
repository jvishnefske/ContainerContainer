CREATE TABLE repo_migrations (
  repo_name VARCHAR(256) PRIMARY KEY,
  start_time TIMESTAMP NULL,
  end_time TIMESTAMP NULL,
  dry_run BOOLEAN NOT NULL,
  package_type VARCHAR(64) NOT NULL,
  migration_status VARCHAR(64) NOT NULL,
  target_repo VARCHAR(256),
  job_id VARCHAR(64) NOT NULL UNIQUE,
  repository_type VARCHAR(32),
  scheduled_time TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
CREATE INDEX idx_repo_migrations_status ON repo_migrations(migration_status);
