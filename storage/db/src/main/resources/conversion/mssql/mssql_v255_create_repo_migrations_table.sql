CREATE TABLE repo_migrations
(
    repo_name          NVARCHAR(256) NOT NULL,
    start_time         DATETIME2,
    end_time           DATETIME2,
    dry_run            BIT           NOT NULL,
    package_type       NVARCHAR(64)  NOT NULL,
    migration_status   NVARCHAR(64)  NOT NULL,
    target_repo        NVARCHAR(256),
    job_id             NVARCHAR(64)  NOT NULL,
    repository_type    NVARCHAR(32),
    scheduled_time     DATETIME2,
    CONSTRAINT repo_migrations_pk PRIMARY KEY (repo_name),
    CONSTRAINT repo_migrations_job_id_uk UNIQUE (job_id)
);
CREATE INDEX idx_repo_migrations_status ON repo_migrations (migration_status);