CREATE TABLE nodes_cleaned
(
    id              NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    policy_key      VARCHAR(255)  NOT NULL,
    policy_run_id   NUMBER(19, 0) NOT NULL,
    repo            VARCHAR(64)   NOT NULL,
    node_path       VARCHAR(1024) NOT NULL,
    node_name       VARCHAR(255)  NOT NULL,
    bin_length      NUMBER(19, 0) NOT NULL,
    sha1            CHAR(40)      NOT NULL,
    status          NUMBER(19, 0) NOT NULL,
    lead_artifact   NUMBER(1, 0) DEFAULT 0 NOT NULL,
    package_name    VARCHAR(255),
    package_version VARCHAR(255),
    message         VARCHAR(1024),
    CONSTRAINT nodes_cleaned_pk PRIMARY KEY (id),
    CONSTRAINT nodes_cleaned_cleanup_run_summary_fk FOREIGN KEY (policy_key, policy_run_id) REFERENCES cleanup_run_summary (policy_key, policy_run_id) ON DELETE CASCADE
);
CREATE INDEX nodes_cleaned_pkey_prun_status_sha1_idx ON nodes_cleaned (policy_key, policy_run_id, status, sha1);