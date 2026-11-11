CREATE TABLE retention_run_summary
(
    id                             NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    policy_key                     VARCHAR(255) NOT NULL,
    run_id                         NUMBER(19, 0) NOT NULL,
    dry_run                        NUMBER(1, 0) DEFAULT 0 NOT NULL,
    started_date                   NUMBER(19, 0) NOT NULL,
    finished_date                  NUMBER(19, 0) NOT NULL,
    retention_type                 VARCHAR(64)  NOT NULL,
    status                         VARCHAR(64)  NOT NULL,
    item_type                      VARCHAR(64)  NOT NULL,
    temp_nodes_cleaned             NUMBER(1, 0) DEFAULT 0 NOT NULL,
    processed_items_count          NUMBER(19, 0) DEFAULT 0 NOT NULL,
    failed_items_count             NUMBER(19, 0) DEFAULT 0 NOT NULL,
    total_items_count              NUMBER(19, 0) DEFAULT 0 NOT NULL,
    processed_physical_binary_size NUMBER(19, 0) DEFAULT 0 NOT NULL,
    processed_virtual_binary_size  NUMBER(19, 0) DEFAULT 0 NOT NULL,
    total_binary_size              NUMBER(19, 0) DEFAULT 0 NOT NULL,
    triggered_by                   VARCHAR(64)  NOT NULL,
    CONSTRAINT retention_run_summary_pk PRIMARY KEY (id)
);
CREATE INDEX retention_run_summary_rkey_run_id_idx ON retention_run_summary (policy_key, run_id);
CREATE INDEX retention_run_summary_processed_virtual_binary_size_idx ON retention_run_summary (processed_virtual_binary_size);
CREATE INDEX retention_run_summary_status_idx ON retention_run_summary (status);
CREATE INDEX retention_run_summary_retention_type_idx ON retention_run_summary (retention_type);