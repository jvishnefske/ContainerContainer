CREATE TABLE cleanup_run_summary
(
    policy_key                    VARCHAR(255)       NOT NULL,
    policy_run_id                 BIGINT             NOT NULL,
    dry_run                       SMALLINT DEFAULT 0 NOT NULL,
    started_date                  BIGINT             NOT NULL,
    finished_date                 BIGINT             NOT NULL,
    status                        VARCHAR(64)        NOT NULL,
    type                          VARCHAR(64)        NOT NULL,
    temp_nodes_cleaned            SMALLINT DEFAULT 0 NOT NULL,
    estimated_packages_count      BIGINT             NOT NULL,
    deleted_packages_count        BIGINT             NOT NULL,
    failed_packages_count         BIGINT             NOT NULL,
    deleted_artifacts_count       BIGINT             NOT NULL,
    failed_artifacts_count        BIGINT             NOT NULL,
    original_binary_size          BIGINT             NOT NULL,
    estimated_deleted_binary_size BIGINT             NOT NULL,
    triggered_by                  VARCHAR(64)        NOT NULL,
    CONSTRAINT cleanup_run_summary_pk PRIMARY KEY (policy_key, policy_run_id)
);
CREATE INDEX cleanup_run_summary_prun_policy_run_id_idx ON cleanup_run_summary (policy_run_id);
CREATE INDEX cleanup_run_summary_estimated_deleted_binary_size_idx ON cleanup_run_summary (estimated_deleted_binary_size);
CREATE INDEX cleanup_run_summary_status_idx ON cleanup_run_summary (status);
CREATE INDEX cleanup_run_summary_type_idx ON cleanup_run_summary (type);
