CREATE TABLE repo_migrations (
  repo_name VARCHAR2(256) PRIMARY KEY,
  start_time TIMESTAMP,
  end_time TIMESTAMP,
  dry_run NUMBER(1) NOT NULL,
  package_type VARCHAR2(64) NOT NULL,
  migration_status VARCHAR2(64) NOT NULL,
  target_repo VARCHAR2(256),
  job_id VARCHAR2(64) NOT NULL UNIQUE,
  repository_type VARCHAR2(32),
  scheduled_time TIMESTAMP
);
CREATE INDEX idx_repo_migrations_status ON repo_migrations(migration_status);
