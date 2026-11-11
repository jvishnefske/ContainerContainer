CREATE TABLE unique_ids (
  index_type VARCHAR(32) NOT NULL,
  current_id BIGINT      NOT NULL,
  CONSTRAINT unique_ids_pk PRIMARY KEY (index_type)
);

CREATE TABLE binaries (
  sha1       CHAR(40) NOT NULL,
  md5        CHAR(32) NOT NULL,
  bin_length BIGINT   NOT NULL,
  sha256     CHAR(64) NOT NULL,
  CONSTRAINT binaries_pk PRIMARY KEY (sha1)
);
CREATE UNIQUE INDEX binaries_md5_idx ON binaries (md5);
CREATE INDEX binaries_sha256_idx ON binaries (sha256);

CREATE TABLE binary_blobs (
  sha1 CHAR(40) NOT NULL,
  data VARBINARY(MAX),
  CONSTRAINT binary_blobs_pk PRIMARY KEY (sha1)
);

CREATE TABLE nodes (
  node_id             BIGINT        NOT NULL,
  node_type           BIT           NOT NULL,
  repo                VARCHAR(64)   NOT NULL,
  node_path           VARCHAR(1024) NOT NULL,
  node_name           VARCHAR(255)  NOT NULL,
  depth               SMALLINT      NOT NULL,
  created             BIGINT        NOT NULL,
  created_by          VARCHAR(64),
  modified            BIGINT        NOT NULL,
  modified_by         VARCHAR(64),
  updated             BIGINT,
  bin_length          BIGINT,
  sha1_actual         CHAR(40),
  sha1_original       VARCHAR(1024),
  md5_actual          CHAR(32),
  md5_original        VARCHAR(1024),
  sha256              CHAR(64),
  repo_path_checksum  CHAR(40),
  CONSTRAINT nodes_pk PRIMARY KEY NONCLUSTERED (node_id),
  CONSTRAINT nodes_binaries_fk FOREIGN KEY (sha1_actual) REFERENCES binaries (sha1)
);
CREATE UNIQUE CLUSTERED INDEX nodes_repo_path_name_idx ON nodes (repo, node_path, node_name);
CREATE INDEX nodes_node_path_idx ON nodes (node_path);
CREATE INDEX nodes_name_path_idx ON nodes (node_name, node_path);
CREATE INDEX nodes_sha1_actual_idx ON nodes (sha1_actual);
CREATE INDEX nodes_md5_actual_idx ON nodes (md5_actual);
CREATE INDEX nodes_sha256_idx ON nodes (sha256);

CREATE TABLE node_props (
  prop_id    BIGINT NOT NULL,
  node_id    BIGINT NOT NULL,
  prop_key   VARCHAR(255),
  prop_value VARCHAR(4000),
  CONSTRAINT node_props_pk PRIMARY KEY (prop_id),
  CONSTRAINT node_props_nodes_fk FOREIGN KEY (node_id) REFERENCES nodes (node_id)
);
CREATE INDEX node_props_prop_key_value_idx ON node_props (prop_key) INCLUDE(prop_value);
CREATE INDEX node_props_prop_value_key_idx ON node_props (prop_value) INCLUDE(prop_key);
CREATE INDEX node_props_node_prop_value_idx ON node_props(node_id) INCLUDE(prop_key, prop_value);

CREATE TABLE node_meta_infos (
  node_id           BIGINT NOT NULL,
  props_modified    BIGINT,
  props_modified_by VARCHAR(64),
  props_md5         CHAR(32),
  CONSTRAINT node_meta_infos_pk PRIMARY KEY (node_id),
  CONSTRAINT node_meta_infos_nodes_fk FOREIGN KEY (node_id) REFERENCES nodes (node_id)
);

CREATE TABLE watches (
  watch_id BIGINT      NOT NULL,
  node_id  BIGINT      NOT NULL,
  username VARCHAR(255) NOT NULL,
  since    BIGINT      NOT NULL,
  CONSTRAINT watches_pk PRIMARY KEY (watch_id),
  CONSTRAINT watches_nodes_fk FOREIGN KEY (node_id) REFERENCES nodes (node_id)
);
CREATE INDEX watches_node_id_idx ON watches (node_id);

CREATE TABLE stats (
  node_id            BIGINT NOT NULL,
  download_count     BIGINT,
  last_downloaded    BIGINT,
  last_downloaded_by VARCHAR(64),
  CONSTRAINT stats_pk PRIMARY KEY (node_id),
  CONSTRAINT stats_nodes_fk FOREIGN KEY (node_id) REFERENCES nodes (node_id)
);

CREATE TABLE stats_remote (
  node_id            BIGINT NOT NULL,
  origin             VARCHAR(64),
  download_count     BIGINT,
  last_downloaded    BIGINT,
  last_downloaded_by VARCHAR(64),
  path VARCHAR(1024),
  CONSTRAINT stats_remote_pk PRIMARY KEY (node_id, origin),
  CONSTRAINT stats_remote_nodes_fk FOREIGN KEY (node_id) REFERENCES nodes (node_id)
);

CREATE TABLE indexed_archives (
  archive_sha1        CHAR(40) NOT NULL,
  indexed_archives_id BIGINT   NOT NULL,
  CONSTRAINT indexed_archives_pk PRIMARY KEY (archive_sha1),
  CONSTRAINT indexed_archives_id_uq UNIQUE (indexed_archives_id),
  CONSTRAINT indexed_archives_binaries_fk FOREIGN KEY (archive_sha1) REFERENCES binaries (sha1)
);

CREATE TABLE archive_paths (
  path_id    BIGINT NOT NULL,
  entry_path VARCHAR(1024),
  CONSTRAINT archive_paths_pk PRIMARY KEY NONCLUSTERED (path_id)
);
CREATE UNIQUE CLUSTERED INDEX archive_paths_path_idx ON archive_paths (entry_path);

CREATE TABLE archive_names (
  name_id    BIGINT NOT NULL,
  entry_name VARCHAR(255),
  CONSTRAINT archive_names_pk PRIMARY KEY NONCLUSTERED (name_id)
);
CREATE UNIQUE CLUSTERED INDEX archive_names_name_idx ON archive_names (entry_name);

CREATE TABLE indexed_archives_entries (
  indexed_archives_id BIGINT NOT NULL,
  entry_path_id       BIGINT NOT NULL,
  entry_name_id       BIGINT NOT NULL,
  CONSTRAINT indexed_archives_entries_pk PRIMARY KEY (indexed_archives_id, entry_path_id, entry_name_id),
  CONSTRAINT indexed_archives_id_fk FOREIGN KEY (indexed_archives_id) REFERENCES indexed_archives (indexed_archives_id),
  CONSTRAINT entry_path_id_fk FOREIGN KEY (entry_path_id) REFERENCES archive_paths (path_id),
  CONSTRAINT entry_name_id_fk FOREIGN KEY (entry_name_id) REFERENCES archive_names (name_id)
);
CREATE INDEX indexed_entries_path_idx ON indexed_archives_entries (entry_path_id);
CREATE INDEX indexed_entries_name_idx ON indexed_archives_entries (entry_name_id);

CREATE SEQUENCE node_events_event_id_seq
    START WITH 1;

CREATE TABLE node_events (
  event_id   BIGINT        NOT NULL DEFAULT NEXT VALUE FOR node_events_event_id_seq,
  timestamp  BIGINT        NOT NULL,
  event_type TINYINT       NOT NULL,
  path       VARCHAR(1344) NOT NULL,
  hints      VARCHAR(4096),
  repo       VARCHAR(64),
  CONSTRAINT node_events_pk PRIMARY KEY (event_id)
);
CREATE INDEX node_events_full_idx
  ON node_events (timestamp, event_id, event_type, path);

CREATE INDEX node_events_repo_event_id
    ON node_events (repo, event_id);

CREATE TABLE node_events_tmp (
     tmp_event_id   BIGINT    NOT NULL,
     timestamp  BIGINT        NOT NULL,
     event_type TINYINT       NOT NULL,
     path       VARCHAR(1344) NOT NULL,
     hints      VARCHAR(4096),
     CONSTRAINT node_events_tmp_pk PRIMARY KEY (tmp_event_id)
);
CREATE INDEX node_events_tmp_idx
  ON node_events_tmp (timestamp, tmp_event_id);

CREATE SEQUENCE storage_events_event_id_seq
    START WITH 1;

CREATE TABLE storage_events (
    event_id   BIGINT      NOT NULL DEFAULT NEXT VALUE FOR storage_events_event_id_seq,
    timestamp  BIGINT      NOT NULL,
    event_type SMALLINT    NOT NULL,
    sha1       CHAR(40)    NOT NULL,
    sha256     CHAR(64)    NOT NULL,
    bin_length BIGINT      NOT NULL,
    data       VARCHAR(1024),
    CONSTRAINT storage_events_pk PRIMARY KEY (event_id)
);
CREATE INDEX storage_events_sha1_type_idx
    ON storage_events (sha1, event_type);
CREATE INDEX storage_events_bin_size_idx
    ON storage_events (bin_length);

CREATE TABLE tasks (
  task_type    VARCHAR(32)   NOT NULL,
  task_context VARCHAR(2048) NOT NULL,
  created      BIGINT,
  task_id      BIGINT IDENTITY(1,1),
  CONSTRAINT tasks_pk PRIMARY KEY (task_id)
);
CREATE INDEX tasks_type_context_idx ON tasks (task_type, task_context);

CREATE TABLE jobs (
  job_id               BIGINT      NOT NULL,
  job_type             VARCHAR(32) NOT NULL,
  job_status           VARCHAR(32) NOT NULL,
  started              BIGINT      NOT NULL,
  finished             BIGINT,
  additional_details   VARCHAR(2048),
  CONSTRAINT jobs_pk PRIMARY KEY (job_id)
);
CREATE INDEX jobs_index ON jobs (started, finished, job_status, job_type);

CREATE TABLE configs (
  config_name VARCHAR(255)   NOT NULL,
  last_modified       BIGINT  NOT NULL,
  data        VARBINARY(MAX) NOT NULL,
  CONSTRAINT configs_pk PRIMARY KEY (config_name)
);

CREATE TABLE builds
(
    build_id     BIGINT       NOT NULL,
    build_name   VARCHAR(255) NOT NULL,
    build_number VARCHAR(255) NOT NULL,
    build_date   BIGINT       NOT NULL,
    ci_url       VARCHAR(1024),
    created      BIGINT       NOT NULL,
    created_by   VARCHAR(64),
    modified     BIGINT,
    modified_by  VARCHAR(64),
    repo         VARCHAR(64)  NOT NULL DEFAULT 'artifactory-build-info',
    CONSTRAINT builds_pk PRIMARY KEY (build_id)
);
CREATE UNIQUE INDEX builds_name_num_date_repo_idx ON builds (build_name, build_number, build_date, repo);

CREATE TABLE build_promotions
(
    build_id          BIGINT      NOT NULL,
    created           BIGINT      NOT NULL,
    created_by        VARCHAR(64),
    status            VARCHAR(64) NOT NULL,
    repo              VARCHAR(64),
    promotion_comment VARCHAR(1024),
    ci_user           VARCHAR(64),
    CONSTRAINT build_promotions_pk PRIMARY KEY (build_id, created),
    CONSTRAINT build_promotions_builds_fk FOREIGN KEY (build_id) REFERENCES builds (build_id)
);
CREATE INDEX build_promotions_status_idx ON build_promotions (status);

CREATE TABLE build_props (
  prop_id    BIGINT NOT NULL,
  build_id   BIGINT NOT NULL,
  prop_key   VARCHAR(255),
  prop_value VARCHAR(2048),
  CONSTRAINT build_props_pk PRIMARY KEY (prop_id),
  CONSTRAINT build_props_builds_fk FOREIGN KEY (build_id) REFERENCES builds (build_id)
);
CREATE INDEX build_props_build_id_idx ON build_props (build_id);
CREATE INDEX build_props_prop_key_idx ON build_props (prop_key);
CREATE INDEX build_props_prop_value_idx ON build_props (prop_value);

CREATE TABLE build_modules (
  module_id      BIGINT        NOT NULL,
  build_id       BIGINT        NOT NULL,
  module_name_id VARCHAR(1024) NOT NULL,
  type           VARCHAR(64),
  CONSTRAINT build_modules_pk PRIMARY KEY (module_id),
  CONSTRAINT build_modules_builds_fk FOREIGN KEY (build_id) REFERENCES builds (build_id)
);
CREATE INDEX build_modules_build_id_idx ON build_modules (build_id);

CREATE TABLE build_artifacts (
  artifact_id   BIGINT        NOT NULL,
  module_id     BIGINT        NOT NULL,
  artifact_name VARCHAR(1024) NOT NULL,
  artifact_type VARCHAR(64),
  sha1          CHAR(40),
  md5           CHAR(32),
  CONSTRAINT build_artifacts_pk PRIMARY KEY (artifact_id),
  CONSTRAINT build_artifacts_modules_fk FOREIGN KEY (module_id) REFERENCES build_modules (module_id)
);
CREATE INDEX build_artifacts_module_id_idx ON build_artifacts (module_id);
CREATE INDEX build_artifacts_sha1_idx ON build_artifacts (sha1);
CREATE INDEX build_artifacts_md5_idx ON build_artifacts (md5);

CREATE TABLE build_dependencies (
  dependency_id      BIGINT        NOT NULL,
  module_id          BIGINT        NOT NULL,
  dependency_name_id VARCHAR(1024) NOT NULL,
  dependency_scopes  VARCHAR(1024),
  dependency_type    VARCHAR(64),
  sha1               CHAR(40),
  md5                CHAR(32),
  CONSTRAINT build_dependencies_pk PRIMARY KEY (dependency_id),
  CONSTRAINT build_dependencies_modules_fk FOREIGN KEY (module_id) REFERENCES build_modules (module_id)
);
CREATE INDEX build_dependencies_module_idx ON build_dependencies (module_id);
CREATE INDEX build_dependencies_sha1_idx ON build_dependencies (sha1);
CREATE INDEX build_dependencies_md5_idx ON build_dependencies (md5);

CREATE TABLE module_props (
  prop_id    BIGINT NOT NULL,
  module_id  BIGINT NOT NULL,
  prop_key   VARCHAR(255),
  prop_value VARCHAR(2048),
  CONSTRAINT module_props_pk PRIMARY KEY (prop_id),
  CONSTRAINT module_props_modules_fk FOREIGN KEY (module_id) REFERENCES build_modules (module_id)
);
CREATE INDEX module_props_module_id_idx ON module_props (module_id);
CREATE INDEX module_props_prop_key_idx ON module_props (prop_key);
CREATE INDEX module_props_prop_value_idx ON module_props (prop_value);

CREATE TABLE db_properties(
                              installation_date           BIGINT       NOT NULL,
                              artifactory_version         VARCHAR(128) NOT NULL,
                              artifactory_revision        INT,
                              artifactory_release         BIGINT,
                              artifactory_product_version VARCHAR(128) NOT NULL,
                              CONSTRAINT db_properties_pk PRIMARY KEY (installation_date)
);

CREATE TABLE artifactory_servers (
  server_id                VARCHAR(128) NOT NULL,
  start_time               BIGINT      NOT NULL,
  context_url              VARCHAR(255),
  membership_port          INT,
  server_state             VARCHAR(12) NOT NULL,
  server_role              VARCHAR(255) NOT NULL,
  last_heartbeat           BIGINT      NOT NULL,
  artifactory_version      VARCHAR(128) NOT NULL,
  artifactory_revision     INT,
  artifactory_release      BIGINT,
  artifactory_running_mode VARCHAR(12) NOT NULL,
  license_hash             VARCHAR(41) NOT NULL,
  CONSTRAINT artifactory_servers_pk PRIMARY KEY (server_id)
);

CREATE TABLE distributed_locks (
  category       VARCHAR(128)   NOT NULL,
  lock_key       VARCHAR(255)      NOT NULL,
  owner          VARCHAR(64)   NOT NULL,
  owner_thread   BIGINT     NOT NULL,
  owner_thread_name     VARCHAR(64)   NOT NULL,
  acquire_time   BIGINT     NOT NULL,
  CONSTRAINT locks_pk PRIMARY KEY (category,lock_key)
);
CREATE INDEX distributed_locks_owner ON distributed_locks (owner);
CREATE INDEX distributed_locks_owner_thread ON distributed_locks (owner_thread);
CREATE INDEX lock_key_idx ON distributed_locks (lock_key);

CREATE TABLE artifact_bundles (
  id                BIGINT        NOT NULL,
  name              VARCHAR(255)  NOT NULL,
  version           VARCHAR(32)   NOT NULL,
  status            VARCHAR(64)   NOT NULL,
  date_created      BIGINT        NOT NULL,
  date_distributed  BIGINT                ,
  signature         VARCHAR(1024) NOT NULL,
  type              VARCHAR(32)   DEFAULT 'TARGET' NOT NULL,
  storing_repo      VARCHAR(64),
  keep              SMALLINT,
  source_service_id VARCHAR(255),
  CONSTRAINT bundle_pk PRIMARY KEY (id)
);
CREATE INDEX name_ver_sta_date_typ_repo_idx ON artifact_bundles(name,version,status,date_created,type,storing_repo);
CREATE UNIQUE INDEX name_version_idx ON artifact_bundles (name, version, type);

CREATE TABLE bundle_files (
  id                         BIGINT                NOT NULL,
  node_id                    BIGINT                NOT NULL,
  bundle_id                  BIGINT                NOT NULL,
  repo_path                  VARCHAR(1090)         NOT NULL,
  original_component_details VARCHAR(1000)                 ,
  CONSTRAINT node_id_nodes_fk FOREIGN KEY (node_id) REFERENCES nodes (node_id),
  CONSTRAINT bundle_id_bundle_files_fk FOREIGN KEY (bundle_id) REFERENCES artifact_bundles (id),
  CONSTRAINT bundle_node_pk PRIMARY KEY (id)
);
CREATE INDEX bundle_id_bundle_files_idx ON bundle_files(bundle_id);
CREATE INDEX repo_path_bundle_files_idx ON bundle_files (repo_path);
CREATE INDEX bundle_files_node_id_idx ON bundle_files (node_id);

CREATE TABLE bundle_blobs (
  id        BIGINT NOT NULL,
  data      VARBINARY(MAX)   NOT NULL,
  bundle_id BIGINT NOT NULL,
  CONSTRAINT bundle_blobs_pk PRIMARY KEY (id),
  CONSTRAINT bundle_id_bundle_blobs_fk FOREIGN KEY (bundle_id) REFERENCES artifact_bundles (id)
);
CREATE UNIQUE INDEX bundle_id_idx on bundle_blobs (bundle_id);

CREATE TABLE trusted_keys (
  kid         VARCHAR(6)    NOT NULL,
  trusted_key VARCHAR(4000) NOT NULL,
  fingerprint VARCHAR(255)  NOT NULL,
  alias       VARCHAR(255)  NOT NULL,
  issued      BIGINT,
  issued_by   VARCHAR(255),
  expiry      BIGINT,
  CONSTRAINT trusted_keys_pk PRIMARY KEY (kid)
);

CREATE UNIQUE INDEX trusted_keys_alias ON trusted_keys (alias);

CREATE TABLE UI_SESSION (
	SESSION_ID CHAR(36),
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT UI_SESSION_PK PRIMARY KEY (SESSION_ID)
);

CREATE INDEX UI_SESSION_IX1 ON UI_SESSION (LAST_ACCESS_TIME);

CREATE TABLE UI_SESSION_ATTRIBUTES (
	SESSION_ID CHAR(36),
	ATTRIBUTE_NAME VARCHAR(200),
	ATTRIBUTE_BYTES IMAGE,
	CONSTRAINT UI_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_ID, ATTRIBUTE_NAME),
	CONSTRAINT UI_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_ID) REFERENCES UI_SESSION(SESSION_ID) ON DELETE CASCADE
);

CREATE INDEX UI_SESSION_ATTRIBUTES_IX1 ON UI_SESSION_ATTRIBUTES (SESSION_ID);

CREATE TABLE node_events_errors
(
    error_id         BIGINT        NOT NULL,
    operation_key    VARCHAR(255),
    event_category   VARCHAR(255),
    first_error_time BIGINT        NOT NULL,
    last_error_time  BIGINT        NOT NULL,
    error_count      SMALLINT      NOT NULL,
    error_message    VARCHAR(4000) NOT NULL,
    -- set NOT NULL once remove event_time
    event_id         BIGINT,
    -- deprecated (remove once moving legacy replication and mds to persistent queue)
    event_time       BIGINT        NOT NULL,
    event_type       SMALLINT      NOT NULL,
    path             VARCHAR(1344) NOT NULL,
    CONSTRAINT node_events_errors_pk PRIMARY KEY (error_id),
    CONSTRAINT node_events_errors_event_id_fk FOREIGN KEY (event_id) REFERENCES node_events (event_id)
);
CREATE INDEX node_events_errors_idx ON node_events_errors (operation_key, event_category, event_id, event_time);

CREATE TABLE node_event_cursor (
  operator_id       VARCHAR(255) NOT NULL,
  event_marker      BIGINT       NOT NULL,
  type              VARCHAR(255) NOT NULL,
  status            VARCHAR(255) ,
  last_updated      BIGINT       ,
  CONSTRAINT operator_id_pk PRIMARY KEY (operator_id)
);

CREATE TABLE node_event_priorities (
  priority_id      BIGINT        NOT NULL,
  path             VARCHAR(1344) NOT NULL,
  type             VARCHAR(1024) NOT NULL,
  operator_id      VARCHAR(255) NOT NULL,
  priority         SMALLINT      NOT NULL,
  timestamp        BIGINT        NOT NULL,
  retry_count      SMALLINT      NOT NULL,
  CONSTRAINT priority_id_pk PRIMARY KEY (priority_id)
);

CREATE TABLE migration_status (
  identifier          VARCHAR(255)         NOT NULL,
  started             BIGINT               NOT NULL,
  finished            BIGINT               NOT NULL,
  migration_info_blob VARBINARY(MAX),
  CONSTRAINT identifier_pk PRIMARY KEY (identifier)
);

CREATE TABLE schema_change_log (
  version           VARCHAR(32) PRIMARY KEY,
  description       VARCHAR(1024) NOT NULL,
  start_time        BIGINT NOT NULL,
  update_time       BIGINT NOT NULL,
  status            VARCHAR(32) NOT NULL,
  exec_type         VARCHAR(32) NOT NULL,
  product_version   VARCHAR(256),
  initial_version   VARCHAR(256),
  server_id         VARCHAR(256) NOT NULL,
  error_message     VARCHAR(1024)
);

CREATE TABLE binaries_tasks
(
    id              BIGINT      NOT NULL,
    sha1            VARCHAR(40) NOT NULL,
    time            BIGINT      NOT NULL,
    local_repo_key  VARCHAR(64) NOT NULL,
    remote_url_hash INT,
    start_time      BIGINT,
    count           INT    NOT NULL,
    server_id       VARCHAR(128),
    CONSTRAINT binaries_tasks_pk PRIMARY KEY (id)
);
CREATE INDEX binaries_tasks_count_idx ON binaries_tasks (count);
CREATE INDEX binaries_tasks_time_idx ON binaries_tasks (time);
CREATE INDEX binaries_tasks_start_time_idx ON binaries_tasks (start_time);
CREATE INDEX binaries_tasks_server_id_idx ON binaries_tasks (server_id);
CREATE INDEX binaries_tasks_repo_key_idx ON binaries_tasks (local_repo_key);

CREATE TABLE heavy_query_cache
(
    id          BIGINT         NOT NULL,
    cache_key   VARCHAR(255)   NOT NULL,
    cache_time  BIGINT         NOT NULL,
    expiry_time BIGINT         NOT NULL,
    data        VARBINARY(MAX) NOT NULL,
    CONSTRAINT hqc_pk PRIMARY KEY (id)
);
CREATE INDEX hqc_key_idx ON heavy_query_cache (cache_key);
CREATE INDEX hqc_expiry_time_idx ON heavy_query_cache (expiry_time);

CREATE TABLE repository_config
(
    repository_key VARCHAR(64) NOT NULL,
    type           VARCHAR(32) NOT NULL,
    package_type   VARCHAR(32) NOT NULL,
    revision       BIGINT      NOT NULL,
    created        BIGINT      NOT NULL,
    modified       BIGINT      NOT NULL,
    config_blob    VARBINARY(MAX),
    CONSTRAINT repo_key_pk PRIMARY KEY (repository_key)
);

CREATE TABLE mirrors_metadata
(
    metadata_type    VARCHAR(64)   NOT NULL,
    member_url       VARCHAR(500) NOT NULL,
    cache_time       BIGINT        NOT NULL,
    metadata_blob    VARBINARY(MAX),
    CONSTRAINT mirrors_remote_pk PRIMARY KEY (metadata_type, member_url)
);

CREATE INDEX cache_time_idx ON mirrors_metadata (cache_time);
CREATE INDEX member_url_idx ON mirrors_metadata (member_url);

CREATE TABLE quartz_task_history
(
    run_id             BIGINT IDENTITY (1,1),
    external_id        VARCHAR(255),
    task_type          VARCHAR(255) NOT NULL,
    started_date       BIGINT,
    finished_date      BIGINT,
    last_modified_date BIGINT       NOT NULL,
    status             VARCHAR(64)  NOT NULL,
    status_message     VARCHAR(1024),
    deleted            SMALLINT DEFAULT 0,
    task_id            VARCHAR(255) NOT NULL,
    server_id          VARCHAR(64)  NOT NULL,
    manual_trigger     SMALLINT DEFAULT 0,
    username           VARCHAR(64)  NOT NULL,
    CONSTRAINT quartz_task_history_pk PRIMARY KEY (run_id)
);
CREATE INDEX external_id_idx ON quartz_task_history (external_id);

CREATE TABLE file_list_tasks
(
    repo            VARCHAR(255)    NOT NULL,
    path            VARCHAR(255)    NOT NULL,
    heartbeat       BIGINT,
    CONSTRAINT file_list_tasks_pk PRIMARY KEY (repo)
);

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
    deleted_packages_count        BIGINT             NOT NULL,
    failed_packages_count         BIGINT             NOT NULL,
    deleted_artifacts_count       BIGINT             NOT NULL,
    failed_artifacts_count        BIGINT             NOT NULL,
    original_binary_size          BIGINT             NOT NULL,
    estimated_deleted_binary_size BIGINT             NOT NULL,
    triggered_by                  VARCHAR(64)        NOT NULL,
    total_binary_size             BIGINT DEFAULT 0   NOT NULL,
    CONSTRAINT cleanup_run_summary_pk PRIMARY KEY (policy_key, policy_run_id)
);
CREATE INDEX cleanup_run_summary_prun_policy_run_id_idx ON cleanup_run_summary (policy_run_id);
CREATE INDEX cleanup_run_summary_estimated_deleted_binary_size_idx ON cleanup_run_summary (estimated_deleted_binary_size);
CREATE INDEX cleanup_run_summary_status_idx ON cleanup_run_summary (status);
CREATE INDEX cleanup_run_summary_type_idx ON cleanup_run_summary (type);

CREATE TABLE nodes_cleaned
(
    id              BIGINT IDENTITY (1,1),
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

CREATE TABLE storage_multipart_uploads
(
    upload_id     VARCHAR(255)   NOT NULL,
    created_date  BIGINT         NOT NULL,
    temp_path     VARCHAR(255)   NOT NULL,
    status        VARCHAR(20)    NOT NULL,
    modified_date BIGINT         NOT NULL,
    data          NVARCHAR(2048),
    CONSTRAINT storage_multipart_uploads_pk PRIMARY KEY (upload_id)
);
CREATE INDEX storage_mpu_created_date_idx ON storage_multipart_uploads (created_date);
CREATE INDEX storage_mpu_status_idx ON storage_multipart_uploads (status);

CREATE TABLE policy
(
    policy_key  VARCHAR(255)       NOT NULL,
    policy_type VARCHAR(64)        NOT NULL,
    item_type   VARCHAR(64)        NOT NULL,
    enabled     SMALLINT DEFAULT 0 NOT NULL,
    created     BIGINT             NOT NULL,
    created_by  VARCHAR(64)        NOT NULL,
    config      VARBINARY(MAX)     NOT NULL,
    modified    BIGINT,
    modified_by VARCHAR(64),
    cronExp     VARCHAR(64),
    description VARCHAR(1024),
    CONSTRAINT policy_pk PRIMARY KEY (policy_key)
);

CREATE TABLE config_history
(
    id BIGINT IDENTITY(1,1),
    config_data VARBINARY(MAX) NOT NULL,
    repo_config_data VARBINARY(MAX) NOT NULL,
    log_config_data VARBINARY(MAX) NOT NULL,
    CONSTRAINT config_history_pk PRIMARY KEY(id)
    );

CREATE TABLE version_history
(
    id                  BIGINT       IDENTITY(1,1),
    version             VARCHAR(255) NOT NULL,
    config_version_id   BIGINT       NOT NULL,
    backward_compatible SMALLINT     DEFAULT 0,
    active              SMALLINT     DEFAULT 1,
    created             BIGINT       NOT NULL,
    CONSTRAINT version_history_pk PRIMARY KEY (id),
    CONSTRAINT version_history_config_fk FOREIGN KEY (config_version_id)
    REFERENCES config_history (id)
);