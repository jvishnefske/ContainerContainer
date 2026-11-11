CREATE TABLE nodes_cleaned
(
    id              BIGINT             NOT NULL AUTO_INCREMENT,
    policy_key      VARCHAR(255)       NOT NULL,
    policy_run_id   BIGINT             NOT NULL,
    repo            VARCHAR(64)        NOT NULL,
    node_path       VARCHAR(1024)      NOT NULL,
    node_name       VARCHAR(255)       NOT NULL,
    bin_length      BIGINT             NOT NULL,
    sha1            CHAR(40)           NOT NULL,
    status          SMALLINT           NOT NULL,
    lead_artifact   SMALLINT DEFAULT 0 NOT NULL,
    package_name    VARCHAR(255),
    package_version VARCHAR(255),
    message         VARCHAR(1024),
    CONSTRAINT nodes_cleaned_pk PRIMARY KEY (id),
    CONSTRAINT nodes_cleaned_cleanup_run_summary_fk FOREIGN KEY (policy_key, policy_run_id) REFERENCES cleanup_run_summary (policy_key, policy_run_id) ON DELETE CASCADE
);
CREATE INDEX nodes_cleaned_pkey_prun_status_sha1_idx ON nodes_cleaned (policy_key, policy_run_id, status, sha1);