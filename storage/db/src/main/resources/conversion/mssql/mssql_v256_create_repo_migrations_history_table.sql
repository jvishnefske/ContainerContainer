CREATE TABLE repo_migrations_history
(
    job_id             NVARCHAR(64)  NOT NULL,
    repo_name          NVARCHAR(256) NOT NULL,
    target_repo        NVARCHAR(256),
    migration_status   NVARCHAR(64)  NOT NULL,
    start_time         DATETIME2,
    end_time           DATETIME2,
    CONSTRAINT repo_migrations_history_pk PRIMARY KEY (job_id),
    CONSTRAINT fk_history_repo FOREIGN KEY (repo_name) REFERENCES repo_migrations (repo_name) ON DELETE CASCADE
);
CREATE INDEX idx_history_repo_name ON repo_migrations_history (repo_name, start_time DESC);
CREATE INDEX idx_history_status ON repo_migrations_history (migration_status);
