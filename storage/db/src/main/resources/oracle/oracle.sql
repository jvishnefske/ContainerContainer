CREATE TABLE unique_ids (
  index_type VARCHAR2(32)  NOT NULL,
  current_id NUMBER(19, 0) NOT NULL,
  CONSTRAINT unique_ids_pk PRIMARY KEY (index_type)
);

CREATE TABLE binaries (
  sha1       CHAR(40)      NOT NULL,
  md5        CHAR(32)      NOT NULL,
  bin_length NUMBER(19, 0) NOT NULL,
  sha256     CHAR(64)      NOT NULL,
  CONSTRAINT binaries_pk PRIMARY KEY (sha1)
);
CREATE UNIQUE INDEX binaries_md5_idx ON binaries (md5);
CREATE INDEX binaries_sha256_idx ON binaries (sha256);

CREATE TABLE binary_blobs (
  sha1 CHAR(40) NOT NULL,
  data BLOB,
  CONSTRAINT binary_blobs_pk PRIMARY KEY (sha1)
);

CREATE TABLE nodes (
  node_id             NUMBER(19, 0)  NOT NULL,
  node_type           NUMBER(5, 0)   NOT NULL,
  repo                VARCHAR2(64)   NOT NULL,
  node_path           VARCHAR2(1024) NOT NULL,
  node_name           VARCHAR2(255)  NOT NULL,
  depth               NUMBER(5, 0)   NOT NULL,
  created             NUMBER(19, 0)  NOT NULL,
  created_by          VARCHAR2(64),
  modified            NUMBER(19, 0)  NOT NULL,
  modified_by         VARCHAR2(64),
  updated             NUMBER(19, 0),
  bin_length          NUMBER(19, 0),
  sha1_actual         CHAR(40),
  sha1_original       VARCHAR2(1024),
  md5_actual          CHAR(32),
  md5_original        VARCHAR2(1024),
  sha256              CHAR(64),
  repo_path_checksum  CHAR(40),
  CONSTRAINT nodes_pk PRIMARY KEY (node_id),
  CONSTRAINT nodes_binaries_fk FOREIGN KEY (sha1_actual) REFERENCES binaries (sha1)
);
CREATE UNIQUE INDEX nodes_repo_path_name_idx ON nodes (repo, node_path, node_name);
CREATE INDEX nodes_node_path_idx ON nodes (node_path);
CREATE INDEX nodes_name_path_idx ON nodes (node_name, node_path);
CREATE INDEX nodes_sha1_actual_idx ON nodes (sha1_actual);
CREATE INDEX nodes_md5_actual_idx ON nodes (md5_actual);
CREATE INDEX nodes_sha256_idx ON nodes (sha256);
CREATE INDEX nodes_repo_created_idx ON nodes (repo, created);

CREATE TABLE node_props (
  prop_id    NUMBER(19, 0) NOT NULL,
  node_id    NUMBER(19, 0) NOT NULL,
  prop_key   VARCHAR2(255),
  prop_value VARCHAR2(4000),
  CONSTRAINT node_props_pk PRIMARY KEY (prop_id),
  CONSTRAINT node_props_nodes_fk FOREIGN KEY (node_id) REFERENCES nodes (node_id)
);
CREATE INDEX node_props_node_prop_value_idx ON node_props(node_id, prop_key, prop_value);
CREATE INDEX node_props_prop_key_value_idx ON node_props (prop_key, prop_value);
CREATE INDEX node_prop_nuget_lower_idx ON node_props (LOWER(prop_key), LOWER(prop_value));

CREATE TABLE node_meta_infos (
  node_id           NUMBER(19, 0) NOT NULL,
  props_modified    NUMBER(19, 0),
  props_modified_by VARCHAR2(64),
  props_md5         CHAR(32),
  CONSTRAINT node_meta_infos_pk PRIMARY KEY (node_id),
  CONSTRAINT node_meta_infos_nodes_fk FOREIGN KEY (node_id) REFERENCES nodes (node_id)
);

CREATE TABLE watches (
  watch_id NUMBER(19, 0) NOT NULL,
  node_id  NUMBER(19, 0) NOT NULL,
  username VARCHAR2(255)  NOT NULL,
  since    NUMBER(19, 0) NOT NULL,
  CONSTRAINT watches_pk PRIMARY KEY (watch_id),
  CONSTRAINT watches_nodes_fk FOREIGN KEY (node_id) REFERENCES nodes (node_id)
);
CREATE INDEX watches_node_id_idx ON watches (node_id);

CREATE TABLE stats (
  node_id            NUMBER(19, 0) NOT NULL,
  download_count     NUMBER(19, 0),
  last_downloaded    NUMBER(19, 0),
  last_downloaded_by VARCHAR2(64),
  CONSTRAINT stats_pk PRIMARY KEY (node_id),
  CONSTRAINT stats_nodes_fk FOREIGN KEY (node_id) REFERENCES nodes (node_id)
);

CREATE TABLE stats_remote (
  node_id            NUMBER(19, 0) NOT NULL,
  origin             VARCHAR2(64),
  download_count     NUMBER(19, 0),
  last_downloaded    NUMBER(19, 0),
  last_downloaded_by VARCHAR2(64),
  path VARCHAR(1024),
  CONSTRAINT stats_remote_pk PRIMARY KEY (node_id, origin),
  CONSTRAINT stats_remote_nodes_fk FOREIGN KEY (node_id) REFERENCES nodes (node_id)
);

CREATE TABLE indexed_archives (
  archive_sha1        CHAR(40)      NOT NULL,
  indexed_archives_id NUMBER(19, 0) NOT NULL,
  CONSTRAINT indexed_archives_pk PRIMARY KEY (archive_sha1),
  CONSTRAINT indexed_archives_id_uq UNIQUE (indexed_archives_id),
  CONSTRAINT indexed_archives_binaries_fk FOREIGN KEY (archive_sha1) REFERENCES binaries (sha1)
);

CREATE TABLE archive_paths (
  path_id    NUMBER(19, 0) NOT NULL,
  entry_path VARCHAR2(1024),
  CONSTRAINT archive_paths_pk PRIMARY KEY (path_id)
);
CREATE UNIQUE INDEX archive_paths_path_idx ON archive_paths (entry_path);

CREATE TABLE archive_names (
  name_id    NUMBER(19, 0) NOT NULL,
  entry_name VARCHAR2(255),
  CONSTRAINT archive_names_pk PRIMARY KEY (name_id)
);
CREATE UNIQUE INDEX archive_names_name_idx ON archive_names (entry_name);

CREATE TABLE indexed_archives_entries (
  indexed_archives_id NUMBER(19, 0) NOT NULL,
  entry_path_id       NUMBER(19, 0) NOT NULL,
  entry_name_id       NUMBER(19, 0) NOT NULL,
  CONSTRAINT indexed_archives_entries_pk PRIMARY KEY (indexed_archives_id, entry_path_id, entry_name_id),
  CONSTRAINT indexed_archives_id_fk FOREIGN KEY (indexed_archives_id) REFERENCES indexed_archives (indexed_archives_id),
  CONSTRAINT entry_path_id_fk FOREIGN KEY (entry_path_id) REFERENCES archive_paths (path_id),
  CONSTRAINT entry_name_id_fk FOREIGN KEY (entry_name_id) REFERENCES archive_names (name_id)
);
CREATE INDEX indexed_entries_path_idx ON indexed_archives_entries (entry_path_id);
CREATE INDEX indexed_entries_name_idx ON indexed_archives_entries (entry_name_id);

CREATE SEQUENCE node_events_event_id_seq
    START WITH 1
    MAXVALUE 9999999999999999999;

CREATE TABLE node_events(
    event_id   NUMBER(19, 0)                              NOT NULL,
    timestamp  NUMBER(19, 0)                              NOT NULL,
    event_type NUMBER(5, 0)                               NOT NULL,
    path       VARCHAR(1344)                              NOT NULL,
    hints      VARCHAR(4000),
    repo       VARCHAR(64),
    CONSTRAINT node_events_pk PRIMARY KEY (event_id)
);
CREATE INDEX node_events_timestamp_event_id_type_idx
  ON node_events (timestamp, event_id, event_type);

CREATE INDEX node_events_repo_event_id
    ON node_events (repo, event_id);

CREATE SEQUENCE storage_events_event_id_seq
    START WITH 1
    MAXVALUE 9999999999999999999;

CREATE TABLE storage_events (
    event_id   NUMBER(19, 0) NOT NULL,
    timestamp  NUMBER(19, 0) NOT NULL,
    event_type NUMBER(5, 0)  NOT NULL,
    sha1       CHAR(40)      NOT NULL,
    sha256     CHAR(64)      NOT NULL,
    bin_length NUMBER(19, 0) NOT NULL,
    data       VARCHAR(1024),
    CONSTRAINT storage_events_pk PRIMARY KEY (event_id)
);
CREATE INDEX storage_events_sha1_type_idx ON storage_events (sha1, event_type);
CREATE INDEX storage_events_bin_size_idx ON storage_events (bin_length);

CREATE TABLE node_events_tmp (
    tmp_event_id NUMBER(19, 0) NOT NULL,
    timestamp    NUMBER(19, 0) NOT NULL,
    event_type   NUMBER(5, 0)  NOT NULL,
    path         VARCHAR(1344) NOT NULL,
    hints        VARCHAR(4000),
    CONSTRAINT   node_events_tmp_pk PRIMARY KEY (tmp_event_id)
);
CREATE INDEX node_events_tmp_idx
  ON node_events_tmp (timestamp, tmp_event_id);

CREATE TABLE tasks (
  task_type    VARCHAR2(32)   NOT NULL,
  task_context VARCHAR2(2048) NOT NULL,
  created      NUMBER(19, 0),
  task_id      NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
  CONSTRAINT tasks_pk PRIMARY KEY (task_id)
);
CREATE INDEX tasks_type_context_idx ON tasks (task_type, task_context);

CREATE TABLE jobs (
  job_id               NUMBER(19, 0)      NOT NULL,
  job_type             VARCHAR(32)        NOT NULL,
  job_status           VARCHAR(32)        NOT NULL,
  started              NUMBER(19, 0)      NOT NULL,
  finished             NUMBER(19, 0),
  additional_details   VARCHAR(2048),
  CONSTRAINT jobs_pk PRIMARY KEY (job_id)
);
CREATE INDEX jobs_index ON jobs (started, finished, job_status, job_type);

CREATE TABLE configs (
  config_name VARCHAR2(255) NOT NULL,
  last_modified             NUMBER(19, 0),
  data        BLOB          NOT NULL,
  CONSTRAINT configs_pk PRIMARY KEY (config_name)
);

CREATE TABLE builds
(
    build_id     NUMBER(19, 0)                                 NOT NULL,
    build_name   VARCHAR2(255)                                 NOT NULL,
    build_number VARCHAR2(255)                                 NOT NULL,
    build_date   NUMBER(19, 0)                                 NOT NULL,
    ci_url       VARCHAR2(1024),
    created      NUMBER(19, 0)                                 NOT NULL,
    created_by   VARCHAR2(64),
    modified     NUMBER(19, 0),
    modified_by  VARCHAR2(64),
    repo         VARCHAR2(64) DEFAULT 'artifactory-build-info' NOT NULL,
    immutable    NUMBER(5, 0) DEFAULT NULL,
    CONSTRAINT builds_pk PRIMARY KEY (build_id)
);
CREATE UNIQUE INDEX builds_name_num_date_repo_idx ON builds (build_name, build_number, build_date, repo);
CREATE INDEX repo_build_name_date_idx ON builds (repo, build_name, build_date);

CREATE TABLE build_promotions
(
    build_id          NUMBER(19, 0) NOT NULL,
    created           NUMBER(19, 0) NOT NULL,
    created_by        VARCHAR2(64),
    status            VARCHAR2(64)  NOT NULL,
    repo              VARCHAR2(64),
    promotion_comment VARCHAR2(1024),
    ci_user           VARCHAR2(64),
    CONSTRAINT build_promotions_pk PRIMARY KEY (build_id, created),
    CONSTRAINT build_promotions_builds_fk FOREIGN KEY (build_id) REFERENCES builds (build_id)
);
CREATE INDEX build_promotions_status_idx ON build_promotions (status);

CREATE TABLE build_props (
  prop_id    NUMBER(19, 0) NOT NULL,
  build_id   NUMBER(19, 0) NOT NULL,
  prop_key   VARCHAR2(255),
  prop_value VARCHAR2(2048),
  CONSTRAINT build_props_pk PRIMARY KEY (prop_id),
  CONSTRAINT build_props_builds_fk FOREIGN KEY (build_id) REFERENCES builds (build_id)
);
CREATE INDEX build_props_build_id_idx ON build_props (build_id);
CREATE INDEX build_props_prop_key_idx ON build_props (prop_key);
CREATE INDEX build_props_prop_value_idx ON build_props (prop_value);

CREATE TABLE build_modules(
  module_id      NUMBER(19, 0) NOT NULL,
  build_id       NUMBER(19, 0) NOT NULL,
  module_name_id VARCHAR2(1024) NOT NULL,
  type           VARCHAR2(64),
  CONSTRAINT build_modules_pk PRIMARY KEY (module_id),
  CONSTRAINT build_modules_builds_fk FOREIGN KEY (build_id) REFERENCES builds (build_id)
);
CREATE INDEX build_modules_build_id_idx ON build_modules (build_id);

CREATE TABLE build_artifacts (
  artifact_id   NUMBER(19, 0)  NOT NULL,
  module_id     NUMBER(19, 0)  NOT NULL,
  artifact_name VARCHAR2(1024) NOT NULL,
  artifact_type VARCHAR2(64),
  sha1          CHAR(40),
  md5           CHAR(32),
  CONSTRAINT build_artifacts_pk PRIMARY KEY (artifact_id),
  CONSTRAINT build_artifacts_modules_fk FOREIGN KEY (module_id) REFERENCES build_modules (module_id)
);
CREATE INDEX build_artifacts_module_id_idx ON build_artifacts (module_id);
CREATE INDEX build_artifacts_sha1_idx ON build_artifacts (sha1);
CREATE INDEX build_artifacts_md5_idx ON build_artifacts (md5);

CREATE TABLE build_dependencies (
  dependency_id      NUMBER(19, 0)  NOT NULL,
  module_id          NUMBER(19, 0)  NOT NULL,
  dependency_name_id VARCHAR2(1024) NOT NULL,
  dependency_scopes  VARCHAR2(4000),
  dependency_type    VARCHAR2(64),
  sha1               CHAR(40),
  md5                CHAR(32),
  CONSTRAINT build_dependencies_pk PRIMARY KEY (dependency_id),
  CONSTRAINT build_dependencies_modules_fk FOREIGN KEY (module_id) REFERENCES build_modules (module_id)
);
CREATE INDEX build_dependencies_module_idx ON build_dependencies (module_id);
CREATE INDEX build_dependencies_sha1_idx ON build_dependencies (sha1);
CREATE INDEX build_dependencies_md5_idx ON build_dependencies (md5);

CREATE TABLE build_release_bundles (
  build_rb_id       NUMBER(19, 0) NOT NULL,
  build_id          NUMBER(19, 0) NOT NULL,
  bundle_repository VARCHAR2(64)  NOT NULL,
  bundle_name       VARCHAR2(255) NOT NULL,
  bundle_version    VARCHAR2(255) NOT NULL,
  CONSTRAINT build_rb_pk PRIMARY KEY (build_rb_id),
  CONSTRAINT build_rb_build_id_fk FOREIGN KEY (build_id) REFERENCES builds (build_id) ON DELETE CASCADE
);
CREATE INDEX build_rb_build_id_idx ON build_release_bundles (build_id);
CREATE INDEX build_rb_bundle_repository_idx ON build_release_bundles (bundle_repository);
CREATE INDEX build_rb_bundle_name_idx ON build_release_bundles (bundle_name);
CREATE INDEX build_rb_bundle_version_idx ON build_release_bundles (bundle_version);

CREATE TABLE module_props (
  prop_id    NUMBER(19, 0) NOT NULL,
  module_id  NUMBER(19, 0) NOT NULL,
  prop_key   VARCHAR2(255),
  prop_value VARCHAR2(2048),
  CONSTRAINT module_props_pk PRIMARY KEY (prop_id),
  CONSTRAINT module_props_modules_fk FOREIGN KEY (module_id) REFERENCES build_modules (module_id)
);
CREATE INDEX module_props_module_id_idx ON module_props (module_id);
CREATE INDEX module_props_prop_key_idx ON module_props (prop_key);
CREATE INDEX module_props_prop_value_idx ON module_props (prop_value);

CREATE TABLE db_properties(
                              installation_date           NUMBER       NOT NULL,
                              artifactory_version         VARCHAR(128) NOT NULL,
                              artifactory_revision        INT,
                              artifactory_release         NUMBER,
                              artifactory_product_version VARCHAR(128) NOT NULL,
                              CONSTRAINT db_properties_pk PRIMARY KEY (installation_date)
);

CREATE TABLE artifactory_servers (
  server_id                VARCHAR(128) NOT NULL,
  start_time               NUMBER      NOT NULL,
  context_url              VARCHAR(255),
  membership_port          INT,
  server_state             VARCHAR(12) NOT NULL,
  server_role              VARCHAR(255) NOT NULL,
  last_heartbeat           NUMBER      NOT NULL,
  artifactory_version      VARCHAR(128) NOT NULL,
  artifactory_revision     INT,
  artifactory_release      NUMBER,
  artifactory_running_mode VARCHAR(12) NOT NULL,
  license_hash             VARCHAR(41) NOT NULL,
  CONSTRAINT artifactory_servers_pk PRIMARY KEY (server_id)
);

CREATE TABLE distributed_locks (
  category       VARCHAR(128)    NOT NULL,
  lock_key       VARCHAR(255)   NOT NULL,
  owner          VARCHAR(64)    NOT NULL,
  owner_thread   NUMBER(19, 0)  NOT NULL,
  owner_thread_name     VARCHAR(64)   NOT NULL,
  acquire_time   NUMBER(19, 0)  NOT NULL,
  CONSTRAINT locks_pk PRIMARY KEY (category,lock_key)
);

CREATE TABLE artifact_bundles (
  id                NUMBER        NOT NULL,
  name              VARCHAR(255)  NOT NULL,
  version           VARCHAR(255)   NOT NULL,
  status            VARCHAR(64)   NOT NULL,
  date_created      NUMBER        NOT NULL,
  date_distributed  NUMBER                ,
  signature         VARCHAR(1024) NOT NULL,
  type              VARCHAR(32)   DEFAULT 'TARGET' NOT NULL,
  storing_repo      VARCHAR(64),
  keep              NUMBER(5, 0),
  source_service_id VARCHAR(255),
  CONSTRAINT bundle_pk PRIMARY KEY (id)
);
CREATE INDEX name_ver_sta_date_typ_repo_idx ON artifact_bundles(name,version,status,date_created,type,storing_repo);
CREATE UNIQUE INDEX name_version_idx ON artifact_bundles (name, version, type);

CREATE TABLE bundle_files (
  id                         NUMBER        NOT NULL,
  node_id                    NUMBER        NOT NULL,
  bundle_id                  NUMBER        NOT NULL,
  repo_path                  VARCHAR(1090) NOT NULL,
  original_component_details VARCHAR(1000)         ,
  CONSTRAINT node_id_nodes_fk FOREIGN KEY (node_id) REFERENCES nodes (node_id),
  CONSTRAINT bundle_id_bundle_files_fk FOREIGN KEY (bundle_id) REFERENCES artifact_bundles (id),
  CONSTRAINT bundle_node_pk PRIMARY KEY (id)
);
CREATE INDEX bundle_id_bundle_files_idx ON bundle_files(bundle_id);
CREATE INDEX repo_path_bundle_files_idx ON bundle_files (repo_path);
CREATE INDEX bundle_files_node_id_idx ON bundle_files (node_id);

CREATE TABLE bundle_blobs (
  id        NUMBER NOT NULL,
  data      BLOB   NOT NULL,
  bundle_id NUMBER NOT NULL,
  CONSTRAINT bundle_blobs_pk PRIMARY KEY (id),
  CONSTRAINT bundle_id_bundle_blobs_fk FOREIGN KEY (bundle_id) REFERENCES artifact_bundles (id)
);
CREATE UNIQUE INDEX bundle_id_idx on bundle_blobs (bundle_id);

CREATE TABLE trusted_keys (
  kid         VARCHAR2(6)    NOT NULL,
  trusted_key VARCHAR2(4000) NOT NULL,
  fingerprint VARCHAR2(255)  NOT NULL,
  key_type    VARCHAR2(255)  DEFAULT 'PGP' NOT NULL,
  alias       VARCHAR2(255)  NOT NULL,
  issued      NUMBER,
  issued_by   VARCHAR2(255),
  expiry      NUMBER,
  CONSTRAINT trusted_keys_pk PRIMARY KEY (kid)
);

CREATE UNIQUE INDEX trusted_keys_alias ON trusted_keys (alias);

CREATE TABLE UI_SESSION (
  SESSION_ID CHAR(36),
  CREATION_TIME NUMBER(19,0) NOT NULL,
  LAST_ACCESS_TIME NUMBER(19,0) NOT NULL,
  MAX_INACTIVE_INTERVAL NUMBER(10,0) NOT NULL,
  PRINCIPAL_NAME VARCHAR2(100 CHAR),
  CONSTRAINT UI_SESSION_PK PRIMARY KEY (SESSION_ID)
);

CREATE INDEX UI_SESSION_IX1 ON UI_SESSION (LAST_ACCESS_TIME);

CREATE TABLE UI_SESSION_ATTRIBUTES (
  SESSION_ID CHAR(36),
  ATTRIBUTE_NAME VARCHAR2(200 CHAR),
  ATTRIBUTE_BYTES BLOB,
  CONSTRAINT UI_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_ID, ATTRIBUTE_NAME),
  CONSTRAINT UI_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_ID) REFERENCES UI_SESSION(SESSION_ID) ON DELETE CASCADE
);

CREATE INDEX UI_SESSION_ATTRIBUTES_IX1 ON UI_SESSION_ATTRIBUTES (SESSION_ID);

CREATE TABLE UI_SESSION_V2
(
    PRIMARY_ID            CHAR(36),
    SESSION_ID            CHAR(36),
    CREATION_TIME         NUMBER(19,0) NOT NULL,
    LAST_ACCESS_TIME      NUMBER(19,0) NOT NULL,
    MAX_INACTIVE_INTERVAL NUMBER(10,0) NOT NULL,
    EXPIRY_TIME           NUMBER(19,0) NOT NULL,
    PRINCIPAL_NAME        VARCHAR2(100 CHAR),
    CONSTRAINT UI_SESSION_V2_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE INDEX UI_SESSION_V2_IX1 ON UI_SESSION_V2 (LAST_ACCESS_TIME);

CREATE TABLE UI_SESSION_V2_ATTRIBUTES
(
    SESSION_PRIMARY_ID CHAR(36),
    ATTRIBUTE_NAME     VARCHAR2(200 CHAR),
    ATTRIBUTE_BYTES    BLOB,
    CONSTRAINT UI_SESSION_V2_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT UI_SESSION_V2_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES UI_SESSION_V2 (PRIMARY_ID) ON DELETE CASCADE
);

CREATE INDEX UI_SESSION_V2_ATTRIBUTES_IX1 ON UI_SESSION_V2_ATTRIBUTES (SESSION_PRIMARY_ID);

CREATE TABLE node_events_errors
(
    error_id         NUMBER(19, 0)      NOT NULL,
    operation_key    VARCHAR2(255),
    event_category   VARCHAR2(255),
    first_error_time NUMBER(19, 0)      NOT NULL,
    last_error_time  NUMBER(19, 0)      NOT NULL,
    error_count      NUMBER(5, 0)       NOT NULL,
    error_message    VARCHAR2(4000)     NOT NULL,
    -- set NOT NULL once remove event_time
    event_id         NUMBER(19, 0),
    -- deprecated (remove once moving legacy replication and mds to persistent queue)
    event_time       NUMBER(19, 0)      NOT NULL,
    event_type       NUMBER(5, 0)       NOT NULL,
    path             VARCHAR2(1344)     NOT NULL,
    CONSTRAINT node_events_errors_pk PRIMARY KEY (error_id),
    CONSTRAINT node_events_errors_event_id_fk FOREIGN KEY (event_id) REFERENCES node_events (event_id)
);
CREATE INDEX node_events_errors_idx ON node_events_errors (operation_key, event_category, event_id, event_time);

CREATE TABLE node_event_cursor (
  operator_id       VARCHAR2(255)       NOT NULL,
  event_marker      NUMBER(19, 0)       NOT NULL,
  type              VARCHAR2(255)       NOT NULL,
  status            VARCHAR2(255)   DEFAULT NULL,
  last_updated      NUMBER(19, 0)   DEFAULT NULL,
  CONSTRAINT operator_id_pk PRIMARY KEY (operator_id)
);

CREATE TABLE node_event_priorities (
  priority_id      NUMBER(19, 0)        NOT NULL,
  path             VARCHAR2(1344)       NOT NULL,
  type             VARCHAR2(1024)       NOT NULL,
  operator_id      VARCHAR2(255)        NOT NULL,
  priority         NUMBER(19, 0)        NOT NULL,
  timestamp        NUMBER(19, 0)        NOT NULL,
  retry_count      NUMBER(1, 0)         NOT NULL,
  CONSTRAINT priority_id_pk PRIMARY KEY (priority_id)
);

CREATE TABLE migration_status (
  identifier          VARCHAR2(255)         NOT NULL,
  started             NUMBER(19, 0)         NOT NULL,
  finished            NUMBER(19, 0)         NOT NULL,
  migration_info_blob BLOB,
  CONSTRAINT identifier_pk PRIMARY KEY (identifier)
);

CREATE TABLE schema_change_log (
  version         VARCHAR(32) PRIMARY KEY,
  description     VARCHAR(1024) NOT NULL,
  start_time      NUMBER(19) NOT NULL,
  update_time     NUMBER(19) NOT NULL,
  status          VARCHAR(32)   NOT NULL,
  exec_type       VARCHAR(32)   NOT NULL,
  product_version VARCHAR(256),
  initial_version VARCHAR(256),
  server_id       VARCHAR(256)  NOT NULL,
  error_message   VARCHAR(1024)
);

CREATE TABLE binaries_tasks
(
    id              NUMBER(19, 0)      NOT NULL,
    sha1            VARCHAR(40)        NOT NULL,
    time            NUMBER(19, 0)      NOT NULL,
    local_repo_key  VARCHAR(64)        NOT NULL,
    remote_url_hash INT,
    start_time      NUMBER(19, 0),
    count           INT                NOT NULL,
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
    id          NUMBER(19)   NOT NULL,
    cache_key   VARCHAR(255) NOT NULL,
    cache_time  NUMBER(19)   NOT NULL,
    expiry_time NUMBER(19)   NOT NULL,
    data        BLOB         NOT NULL,
    CONSTRAINT hqc_pk PRIMARY KEY (id)
);
CREATE INDEX hqc_key_idx ON heavy_query_cache (cache_key);
CREATE INDEX hqc_expiry_time_idx ON heavy_query_cache (expiry_time);


CREATE TABLE repository_config
(
    repository_key VARCHAR2(64) NOT NULL,
    type           VARCHAR2(32) NOT NULL,
    repo_type_v2   VARCHAR2(32),
    package_type   VARCHAR2(32) NOT NULL,
    revision       NUMBER(19, 0) NOT NULL,
    created        NUMBER(19, 0) NOT NULL,
    modified       NUMBER(19, 0) NOT NULL,
    config_blob    BLOB,
    CONSTRAINT repo_key_pk PRIMARY KEY (repository_key)
);

CREATE TABLE mirrors_metadata
(
    metadata_type    VARCHAR2(64)   NOT NULL,
    member_url       VARCHAR2(500) NOT NULL,
    cache_time       NUMBER(19,0)    NOT NULL,
    metadata_blob    BLOB,
    CONSTRAINT mirrors_remote_pk PRIMARY KEY (metadata_type, member_url)
);

CREATE INDEX cache_time_idx ON mirrors_metadata (cache_time);
CREATE INDEX member_url_idx ON mirrors_metadata (member_url);

CREATE TABLE quartz_task_history
(
    run_id             NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    external_id        VARCHAR(255),
    task_type          VARCHAR(255)  NOT NULL,
    started_date       NUMBER(19, 0),
    finished_date      NUMBER(19, 0),
    last_modified_date NUMBER(19, 0) NOT NULL,
    status             VARCHAR(64)   NOT NULL,
    status_message     VARCHAR(1024),
    deleted            NUMBER(1, 0)  DEFAULT 0,
    task_id            VARCHAR(255)  NOT NULL,
    server_id          VARCHAR(64)   NOT NULL,
    manual_trigger     NUMBER(1, 0)  DEFAULT 0,
    username           VARCHAR(64)   NOT NULL,
    CONSTRAINT quartz_task_history_pk PRIMARY KEY (run_id)
);
CREATE INDEX external_id_idx ON quartz_task_history (external_id);

CREATE TABLE file_list_tasks
(
    repo            VARCHAR(255)    NOT NULL,
    path            VARCHAR(255)    NOT NULL,
    heartbeat       NUMBER(19, 0),
    CONSTRAINT file_list_tasks_pk PRIMARY KEY (repo)
);

CREATE TABLE cleanup_run_summary
(
    policy_key                    VARCHAR(255) NOT NULL,
    policy_run_id                 NUMBER(19, 0) NOT NULL,
    dry_run                       NUMBER(1, 0) DEFAULT 0 NOT NULL,
    started_date                  NUMBER(19, 0) NOT NULL,
    finished_date                 NUMBER(19, 0) NOT NULL,
    status                        VARCHAR(64)  NOT NULL,
    type                          VARCHAR(64)  NOT NULL,
    temp_nodes_cleaned            NUMBER(1, 0) DEFAULT 0 NOT NULL,
    deleted_packages_count        NUMBER(19, 0) NOT NULL,
    failed_packages_count         NUMBER(19, 0) NOT NULL,
    deleted_artifacts_count       NUMBER(19, 0) NOT NULL,
    failed_artifacts_count        NUMBER(19, 0) NOT NULL,
    original_binary_size          NUMBER(19, 0) NOT NULL,
    estimated_deleted_binary_size NUMBER(19, 0) NOT NULL,
    triggered_by                  VARCHAR(64)   NOT NULL,
    total_binary_size NUMBER(19, 0) DEFAULT 0 NOT NULL,
    run_config                      BLOB,
    CONSTRAINT cleanup_run_summary_pk PRIMARY KEY (policy_key, policy_run_id)
);
CREATE INDEX cleanup_run_summary_prun_policy_run_id_idx ON cleanup_run_summary (policy_run_id);
CREATE INDEX cleanup_run_summary_estimated_deleted_binary_size_idx ON cleanup_run_summary (estimated_deleted_binary_size);
CREATE INDEX cleanup_run_summary_status_idx ON cleanup_run_summary (status);
CREATE INDEX cleanup_run_summary_type_idx ON cleanup_run_summary (type);

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
    processed_items_count          NUMBER(19, 0) NOT NULL,
    failed_items_count             NUMBER(19, 0) NOT NULL,
    total_items_count              NUMBER(19, 0) DEFAULT 0 NOT NULL,
    processed_physical_binary_size NUMBER(19, 0) NOT NULL,
    processed_virtual_binary_size  NUMBER(19, 0) NOT NULL,
    total_binary_size              NUMBER(19, 0) DEFAULT 0 NOT NULL,
    triggered_by                   VARCHAR(64)  NOT NULL,
    run_config                     BLOB,
    CONSTRAINT retention_run_summary_pk PRIMARY KEY (id)
);
CREATE INDEX retention_run_summary_rkey_run_id_idx ON retention_run_summary (policy_key, run_id);
CREATE INDEX retention_run_summary_processed_virtual_binary_size_idx ON retention_run_summary (processed_virtual_binary_size);
CREATE INDEX retention_run_summary_status_idx ON retention_run_summary (status);
CREATE INDEX retention_run_summary_retention_type_idx ON retention_run_summary (retention_type);

CREATE TABLE nodes_cleaned
(
    id                          NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    policy_key                  VARCHAR(255)            NOT NULL,
    policy_run_id               NUMBER(19, 0)           NOT NULL,
    repo                        VARCHAR(64)             NOT NULL,
    node_path                   VARCHAR(1024)           NOT NULL,
    node_name                   VARCHAR(255)            NOT NULL,
    bin_length                  NUMBER(19, 0)           NOT NULL,
    sha1                        CHAR(40)                NOT NULL,
    status                      NUMBER(5, 0)           NOT NULL,
    lead_artifact               NUMBER(1, 0)  DEFAULT 0 NOT NULL,
    package_name                VARCHAR(255),
    package_version             VARCHAR(255),
    message                     VARCHAR(1024),
    metadata                    BLOB,
    CONSTRAINT nodes_cleaned_pk PRIMARY KEY (id)
);
CREATE INDEX nodes_cleaned_pkey_prun_status_sha1_idx ON nodes_cleaned (policy_key, policy_run_id, status, sha1);
CREATE INDEX nodes_cleaned_policy_key_policy_run_id_idx ON nodes_cleaned (policy_key, policy_run_id);

CREATE TABLE storage_multipart_uploads
(
    upload_id     VARCHAR(255)  NOT NULL,
    created_date  NUMBER(19, 0) NOT NULL,
    temp_path     VARCHAR(255)   NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    modified_date NUMBER(19, 0) NOT NULL,
    data          VARCHAR2(2048),
    CONSTRAINT storage_multipart_uploads_pk PRIMARY KEY (upload_id)
);
CREATE INDEX storage_mpu_created_date_idx ON storage_multipart_uploads (created_date);
CREATE INDEX storage_mpu_status_idx ON storage_multipart_uploads (status);

CREATE TABLE policy
(
    policy_key  VARCHAR(255)            NOT NULL,
    policy_type VARCHAR(64)             NOT NULL,
    item_type   VARCHAR(64)             NOT NULL,
    enabled     NUMBER(1, 0)  DEFAULT 0 NOT NULL,
    created     NUMBER(19, 0)           NOT NULL,
    created_by  VARCHAR2(64)            NOT NULL,
    config      BLOB                    NOT NULL,
    modified    NUMBER(19, 0),
    modified_by VARCHAR2(64),
    cronExp     VARCHAR(64),
    description VARCHAR(1024),
    CONSTRAINT policy_pk PRIMARY KEY (policy_key)
);

CREATE TABLE config_history
(
    id               NUMBER(19, 0)  GENERATED ALWAYS AS IDENTITY,
    config_data      BLOB         NOT NULL,
    repo_config_data BLOB         NOT NULL,
    log_config_data BLOB          NOT NULL,
    CONSTRAINT config_history_pk PRIMARY KEY (id)
);

CREATE TABLE version_history
(
    id                  NUMBER(19, 0)   GENERATED ALWAYS AS IDENTITY,
    version             VARCHAR(255)    NOT NULL,
    config_version_id   NUMBER(19, 0)   NOT NULL,
    backward_compatible NUMBER (1,0)    DEFAULT 0,
    active              NUMBER (1,0)    DEFAULT 1,
    created             NUMBER(19, 0)   NOT NULL,
    CONSTRAINT version_history_pk PRIMARY KEY (id),
    CONSTRAINT version_history_config_fk FOREIGN KEY (config_version_id)
    REFERENCES config_history (id)
);

INSERT INTO schema_change_log (version, description, start_time, update_time, status, exec_type, product_version, server_id)
values ('v204', 'v204_metadata_service_rollback_blocker', 0, 0, 'COMPLETED',
        'SEQ_SYNC', '7.90', 'dummy_server');

INSERT INTO schema_change_log (version, description, start_time, update_time, status, exec_type, product_version, server_id)
values ('v217', 'v217_metadata_service_rollback_blocker', 0, 0, 'COMPLETED',
        'SEQ_SYNC', '7.102', 'dummy_server');

INSERT INTO schema_change_log (version, description, start_time, update_time, status, exec_type, product_version, server_id)
values ('v224', 'v224_metadata_service_rollback_blocker', 0, 0, 'COMPLETED',
        'SEQ_SYNC', '7.108', 'dummy_server');

CREATE TABLE repository_stats
(
    repository_key            VARCHAR2(64)   NOT NULL,
    last_downloaded           NUMBER(19, 0)  DEFAULT 0,
    last_uploaded             NUMBER(19, 0)  DEFAULT 0,
    last_statistics_updated   NUMBER(19, 0)  NOT NULL,
    CONSTRAINT repository_stats_pk PRIMARY KEY (repository_key),
    CONSTRAINT repository_stats_repository_key_fk FOREIGN KEY (repository_key) REFERENCES repository_config (repository_key) ON DELETE CASCADE
);
CREATE INDEX idx_repository_stats_last_statistics_updated ON repository_stats (last_statistics_updated);


CREATE TABLE traffic_summary
(
    id                      NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,
    time                    NUMBER(19)         NOT NULL,
    total_request_count     NUMBER(19) DEFAULT 0   NOT NULL,
    upload_request_count    NUMBER(19) DEFAULT 0   NOT NULL,
    download_request_count  NUMBER(19) DEFAULT 0   NOT NULL,
    total_bytes             NUMBER(19) DEFAULT 0   NOT NULL,
    upload_bytes            NUMBER(19) DEFAULT 0   NOT NULL,
    download_bytes          NUMBER(19) DEFAULT 0   NOT NULL,
    CONSTRAINT traffic_summary_pk PRIMARY KEY (id)
);
CREATE INDEX traffic_summary_time_idx ON traffic_summary (time);


CREATE TABLE distributed_blocking_locks
(
    lock_key VARCHAR(255) NOT NULL,
    CONSTRAINT distributed_blocking_locks_pk PRIMARY KEY (lock_key)
);

CREATE TABLE storage_cache
(
    id                      NUMBER(19)  GENERATED BY DEFAULT AS IDENTITY,
    entity_type             VARCHAR2(32)   NOT NULL,
    entity_key              VARCHAR2(255)  NOT NULL,
    cache_value             CLOB           NOT NULL,
    calculation_timestamp   NUMBER(19, 0)  NOT NULL,
    CONSTRAINT storage_cache_pk PRIMARY KEY (id),
    CONSTRAINT storage_cache_uk UNIQUE (entity_type, entity_key)
);

CREATE TABLE remote_repo_stats_hourly
(
    id                      NUMBER(19)     GENERATED BY DEFAULT AS IDENTITY,
    repository_key          VARCHAR2(128)  NOT NULL,
    repo_package_type       VARCHAR2(64)   NOT NULL,
    update_time             NUMBER(19, 0)  NOT NULL,
    total_request_count     NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    total_response_time_ms    NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    max_response_time_ms    NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    ttfb_avg                NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    total_response_size     NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    status_2xx_count        NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    status_3xx_count        NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    status_4xx_count        NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    status_5xx_count        NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    http_method             VARCHAR2(16)   NOT NULL,
    transfer_rate           NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    CONSTRAINT remote_repo_stats_hourly_pk PRIMARY KEY (id),
    CONSTRAINT remote_repo_stats_hourly_uk UNIQUE (repository_key, update_time, http_method)
);

CREATE TABLE remote_repo_stats_daily
(
    id                      NUMBER(19)     GENERATED BY DEFAULT AS IDENTITY,
    repository_key          VARCHAR2(128)  NOT NULL,
    repo_package_type       VARCHAR2(64)   NOT NULL,
    update_time             NUMBER(19, 0)  NOT NULL,
    total_request_count     NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    total_response_time_ms    NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    max_response_time_ms    NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    ttfb_avg                NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    total_response_size     NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    status_2xx_count        NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    status_3xx_count        NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    status_4xx_count        NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    status_5xx_count        NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    http_method             VARCHAR2(16)   NOT NULL,
    transfer_rate           NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    CONSTRAINT remote_repo_stats_daily_pk PRIMARY KEY (id),
    CONSTRAINT remote_repo_stats_daily_uk UNIQUE (repository_key, update_time, http_method)
);

CREATE TABLE remote_repo_stats_status
(
    id                      NUMBER(19)     GENERATED BY DEFAULT AS IDENTITY,
    repository_key          VARCHAR2(128)  NOT NULL,
    repo_package_type       VARCHAR2(64)   NOT NULL,
    update_time             NUMBER(19, 0)  NOT NULL,
    aggregation_type        VARCHAR2(16)   NOT NULL,
    status_code             NUMBER(5, 0)   NOT NULL,
    request_count           NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    http_method             VARCHAR2(16)   NOT NULL,
    CONSTRAINT remote_repo_stats_status_pk PRIMARY KEY (id),
    CONSTRAINT remote_repo_stats_status_uk UNIQUE (repository_key, update_time, status_code, http_method, aggregation_type)
);

CREATE TABLE repo_traffic_hourly
(
    id                     NUMBER(19)     GENERATED BY DEFAULT AS IDENTITY,
    update_time            NUMBER(19, 0)  NOT NULL,
    action                 VARCHAR2(16)   NOT NULL,
    actor                  VARCHAR2(32)   NOT NULL,
    redirect_target        VARCHAR2(16)   NOT NULL,
    origin_repo_key        VARCHAR2(128)  NOT NULL,
    origin_repo_type       VARCHAR2(16)   NOT NULL,
    origin_package_type    VARCHAR2(64)   NOT NULL,
    repo_key               VARCHAR2(128)  NOT NULL,
    repo_type              VARCHAR2(16)   NOT NULL,
    package_type           VARCHAR2(64)   NOT NULL,
    request_count          NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    total_transferred_size NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    CONSTRAINT repo_traffic_hourly_pk PRIMARY KEY (id),
    CONSTRAINT repo_traffic_hourly_uk UNIQUE (update_time, action, actor, redirect_target, origin_repo_key, repo_key)
);

CREATE TABLE repo_traffic_daily
(
    id                     NUMBER(19)     GENERATED BY DEFAULT AS IDENTITY,
    update_time            NUMBER(19, 0)  NOT NULL,
    action                 VARCHAR2(16)   NOT NULL,
    actor                  VARCHAR2(32)   NOT NULL,
    redirect_target        VARCHAR2(16)   NOT NULL,
    origin_repo_key        VARCHAR2(128)  NOT NULL,
    origin_repo_type       VARCHAR2(16)   NOT NULL,
    origin_package_type    VARCHAR2(64)   NOT NULL,
    repo_key               VARCHAR2(128)  NOT NULL,
    repo_type              VARCHAR2(16)   NOT NULL,
    package_type           VARCHAR2(64)   NOT NULL,
    request_count          NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    total_transferred_size NUMBER(19, 0)  DEFAULT 0 NOT NULL,
    CONSTRAINT repo_traffic_daily_pk PRIMARY KEY (id),
    CONSTRAINT repo_traffic_daily_uk UNIQUE (update_time, action, actor, redirect_target, origin_repo_key, repo_key)
);

CREATE TABLE virtual_repo_dangling_refs
(
    virtual_repo_key VARCHAR2(64)  NOT NULL,
    child_repo_key   VARCHAR2(64)  NOT NULL,
    created          NUMBER(19, 0) NOT NULL,
    modified         NUMBER(19, 0) NOT NULL,
    CONSTRAINT virtual_repo_dangling_refs_pk PRIMARY KEY (virtual_repo_key, child_repo_key)
);

CREATE TABLE transparent_repo_config
(
    repository_key VARCHAR2(128) NOT NULL,
    type           VARCHAR2(32)  NOT NULL,
    package_type   VARCHAR2(32)  NOT NULL,
    revision       NUMBER(19, 0) NOT NULL,
    created        NUMBER(19, 0) NOT NULL,
    modified       NUMBER(19, 0) NOT NULL,
    config_blob    BLOB,
    CONSTRAINT transparent_repo_key_pk PRIMARY KEY (repository_key)
);

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

CREATE TABLE repo_migrations_history (
                                         job_id VARCHAR2(64) PRIMARY KEY,
                                         repo_name VARCHAR2(256) NOT NULL,
                                         target_repo VARCHAR2(256),
                                         migration_status VARCHAR2(64) NOT NULL,
                                         start_time TIMESTAMP,
                                         end_time TIMESTAMP,
                                         CONSTRAINT fk_history_repo FOREIGN KEY (repo_name) REFERENCES repo_migrations(repo_name) ON DELETE CASCADE
);
CREATE INDEX idx_history_repo_name ON repo_migrations_history(repo_name, start_time DESC);
CREATE INDEX idx_history_status ON repo_migrations_history(migration_status);

CREATE TABLE error_catalog_templates
(
    id                  NUMBER(19)    GENERATED BY DEFAULT AS IDENTITY,
    signature_hash      VARCHAR2(64)  NOT NULL,
    severity            VARCHAR2(8)   NOT NULL,
    logger_class        VARCHAR2(512) NOT NULL,
    exception_class     VARCHAR2(512),
    template_message    CLOB          NOT NULL,
    first_occurrence_at NUMBER(19, 0) NOT NULL,
    last_occurrence_at  NUMBER(19, 0) NOT NULL,
    occurrence_count    NUMBER(19, 0) DEFAULT 1 NOT NULL,
    created_at          NUMBER(19, 0) NOT NULL,
    updated_at          NUMBER(19, 0) NOT NULL,
    CONSTRAINT error_catalog_templates_pk PRIMARY KEY (id),
    CONSTRAINT error_catalog_templates_uk UNIQUE (signature_hash)
);

CREATE INDEX error_catalog_templates_last_occ_idx ON error_catalog_templates (last_occurrence_at);
CREATE INDEX error_catalog_templates_sev_last_idx ON error_catalog_templates (severity, last_occurrence_at);
CREATE INDEX error_catalog_templates_logger_last_idx ON error_catalog_templates (logger_class, last_occurrence_at);

CREATE TABLE error_catalog
(
    id          NUMBER(19)    GENERATED BY DEFAULT AS IDENTITY,
    template_id NUMBER(19, 0) NOT NULL,
    occurred_at NUMBER(19, 0) NOT NULL,
    raw_message CLOB          NOT NULL,
    CONSTRAINT error_catalog_pk PRIMARY KEY (id)
);

CREATE INDEX error_catalog_template_idx ON error_catalog (template_id, occurred_at);
CREATE INDEX error_catalog_occurred_idx ON error_catalog (occurred_at);
CREATE TABLE request_logs (
    id                    NUMBER(19)     GENERATED BY DEFAULT AS IDENTITY,
    request_time          NUMBER(19, 0)  NOT NULL,
    trace_id              VARCHAR2(64),
    username              VARCHAR2(256)  NOT NULL,
    http_method           VARCHAR2(10)   NOT NULL,
    request_path          VARCHAR2(2048) NOT NULL,
    status_code           NUMBER(10)     NOT NULL,
    duration_ms           NUMBER(19, 0)  NOT NULL,
    CONSTRAINT request_logs_pk PRIMARY KEY (id)
);
CREATE INDEX request_logs_request_time_idx ON request_logs (request_time);
CREATE INDEX req_logs_username_time_idx ON request_logs (username, request_time);
CREATE INDEX request_logs_trace_idx ON request_logs (trace_id);
CREATE INDEX req_logs_duration_time_idx ON request_logs (duration_ms, request_time);

CREATE TABLE outbound_request_logs (
    id                    NUMBER(19)     GENERATED BY DEFAULT AS IDENTITY,
    request_time          NUMBER(19, 0)  NOT NULL,
    trace_id              VARCHAR2(64),
    repo_key              VARCHAR2(128)  NOT NULL,
    http_method           VARCHAR2(10)   NOT NULL,
    request_url           VARCHAR2(2048) NOT NULL,
    status_code           NUMBER(10)     NOT NULL,
    duration_ms           NUMBER(19, 0)  NOT NULL,
    CONSTRAINT outbound_request_logs_pk PRIMARY KEY (id)
);
CREATE INDEX outbound_rl_request_time_idx ON outbound_request_logs (request_time);
CREATE INDEX outbound_rl_status_time_idx  ON outbound_request_logs (status_code, request_time);
CREATE INDEX outbound_rl_repo_time_idx    ON outbound_request_logs (repo_key, request_time);
CREATE INDEX outbound_rl_trace_idx        ON outbound_request_logs (trace_id);

CREATE TABLE system_states (
    use_case_name  VARCHAR2(255)  NOT NULL,
    id             VARCHAR2(255)  NOT NULL,
    timestamp      NUMBER(19, 0)  NOT NULL,
    node_id        VARCHAR2(255)  NOT NULL,
    actor          VARCHAR2(255),
    data           CLOB,
    CONSTRAINT system_states_pk PRIMARY KEY (use_case_name, id)
);
CREATE INDEX system_states_uc_ts_idx ON system_states (use_case_name, timestamp);

