create table archive_names
(
    name_id    bigint       not null
        primary key,
    entry_name varchar(255) null,
    constraint archive_names_name_idx
        unique (entry_name)
);

create table archive_paths
(
    path_id    bigint        not null
        primary key,
    entry_path varchar(1024) null
);

create index archive_paths_path_idx
    on archive_paths (entry_path);

create table artifact_bundles
(
    id           bigint        not null
        primary key,
    name         varchar(255)  not null,
    version      varchar(30)   not null,
    status       varchar(64)   not null,
    date_created bigint        not null,
    signature    varchar(1024) not null
);

create unique index name_version_idx
    on artifact_bundles (name, version);
create index name_version_status_idx
    on artifact_bundles (name, version, status);

create table artifactory_servers
(
    server_id                varchar(41)  not null
        primary key,
    start_time               bigint       not null,
    context_url              varchar(255) null,
    membership_port          int          null,
    server_state             varchar(12)  not null,
    server_role              varchar(12)  not null,
    last_heartbeat           bigint       not null,
    artifactory_version      varchar(30)  not null,
    artifactory_revision     int          null,
    artifactory_release      bigint       null,
    artifactory_running_mode varchar(12)  not null,
    license_hash             varchar(41)  not null
);

create table binaries
(
    sha1       char(40) not null
        primary key,
    md5        char(32) not null,
    bin_length bigint   not null,
    sha256     char(64) not null,
    constraint binaries_md5_idx
        unique (md5)
);

create index binaries_sha256_idx
    on binaries (sha256);

create table binary_blobs
(
    sha1 char(40) not null
        primary key,
    data bytea    null
);

create table build_jsons
(
    build_id        bigint not null
        primary key,
    build_info_json bytea  null
);

create table builds
(
    build_id     bigint        not null
        primary key,
    build_name   varchar(255)  not null,
    build_number varchar(255)  not null,
    build_date   bigint        not null,
    ci_url       varchar(1024) null,
    created      bigint        not null,
    created_by   varchar(64)   null,
    modified     bigint        null,
    modified_by  varchar(64)   null
);

create unique index builds_name_number_date_idx
    on builds (build_name, build_number, build_date);

create table build_modules
(
    module_id      bigint        not null
        primary key,
    build_id       bigint        not null,
    module_name_id varchar(1024) not null,
    constraint build_modules_builds_fk
        foreign key (build_id) references builds (build_id)
);

create table build_artifacts
(
    artifact_id   bigint        not null
        primary key,
    module_id     bigint        not null,
    artifact_name varchar(1024) not null,
    artifact_type varchar(64)   null,
    sha1          char(40)      null,
    md5           char(32)      null,
    constraint build_artifacts_modules_fk
        foreign key (module_id) references build_modules (module_id)
);

create index build_artifacts_md5_idx
    on build_artifacts (md5);

create index build_artifacts_module_id_idx
    on build_artifacts (module_id);

create index build_artifacts_sha1_idx
    on build_artifacts (sha1);

create table build_dependencies
(
    dependency_id      bigint        not null
        primary key,
    module_id          bigint        not null,
    dependency_name_id varchar(1024) not null,
    dependency_scopes  varchar(1024) null,
    dependency_type    varchar(64)   null,
    sha1               char(40)      null,
    md5                char(32)      null,
    constraint build_dependencies_modules_fk
        foreign key (module_id) references build_modules (module_id)
);

create index build_dependencies_md5_idx
    on build_dependencies (md5);

create index build_dependencies_module_idx
    on build_dependencies (module_id);

create index build_dependencies_sha1_idx
    on build_dependencies (sha1);

create index build_modules_build_id_idx
    on build_modules (build_id);

create table build_promotions
(
    build_id          bigint        not null,
    created           bigint        not null,
    created_by        varchar(64)   null,
    status            varchar(64)   not null,
    repo              varchar(64)   null,
    promotion_comment varchar(1024) null,
    ci_user           varchar(64)   null,
    constraint build_promotions_builds_fk
        foreign key (build_id) references builds (build_id)
);
create unique index build_promotions_created_idx
    on build_promotions (build_id, created);

create index build_promotions_status_idx
    on build_promotions (status);

create table build_props
(
    prop_id    bigint        not null
        primary key,
    build_id   bigint        not null,
    prop_key   varchar(255)  null,
    prop_value varchar(2048) null,
    constraint build_props_builds_fk
        foreign key (build_id) references builds (build_id)
);

create index build_props_build_id_idx
    on build_props (build_id);

create index build_props_prop_key_idx
    on build_props (prop_key);

create index build_props_prop_value_idx
    on build_props (prop_value);

create table bundle_blobs
(
    id        bigint not null
        primary key,
    data      bytea  not null,
    bundle_id bigint not null,
    constraint bundle_id_idx
        unique (bundle_id),
    constraint bundle_id_bundle_blobs_fk
        foreign key (bundle_id) references artifact_bundles (id)
);

create table configs
(
    config_name   varchar(255) not null
        primary key,
    last_modified bigint       not null,
    data          bytea        not null
);

create table db_properties
(
    installation_date    bigint      not null
        primary key,
    artifactory_version  varchar(30) not null,
    artifactory_revision int         null,
    artifactory_release  bigint      null
);

create table distributed_locks
(
    category          varchar(64)  not null,
    lock_key          varchar(255) not null,
    owner             varchar(64)  not null,
    owner_thread      bigint       not null,
    owner_thread_name varchar(64)  not null,
    acquire_time      bigint       not null,
    primary key (category, lock_key)
);

create index distributed_locks_owner
    on distributed_locks (owner);

create index distributed_locks_owner_thread
    on distributed_locks (owner_thread);

create table indexed_archives
(
    archive_sha1        char(40) not null
        primary key,
    indexed_archives_id bigint   not null,
    constraint indexed_archives_id_uq
        unique (indexed_archives_id),
    constraint indexed_archives_binaries_fk
        foreign key (archive_sha1) references binaries (sha1)
);

create table indexed_archives_entries
(
    indexed_archives_id bigint not null,
    entry_path_id       bigint not null,
    entry_name_id       bigint not null,
    primary key (indexed_archives_id, entry_path_id, entry_name_id),
    constraint entry_name_id_fk
        foreign key (entry_name_id) references archive_names (name_id),
    constraint entry_path_id_fk
        foreign key (entry_path_id) references archive_paths (path_id),
    constraint indexed_archives_id_fk
        foreign key (indexed_archives_id) references indexed_archives (indexed_archives_id)
);

create index indexed_entries_name_idx
    on indexed_archives_entries (entry_name_id);

create index indexed_entries_path_idx
    on indexed_archives_entries (entry_path_id);

create table master_key_status
(
    is_unique_key  boolean     not null,
    status         varchar(16) not null,
    set_by_node_id varchar(64) not null,
    kid            char(64)    not null
        primary key,
    expires        bigint      not null,
    constraint unique_key
        unique (is_unique_key)
);

create table module_props
(
    prop_id    bigint        not null
        primary key,
    module_id  bigint        not null,
    prop_key   varchar(255)  null,
    prop_value varchar(2048) null,
    constraint module_props_modules_fk
        foreign key (module_id) references build_modules (module_id)
);

create index module_props_module_id_idx
    on module_props (module_id);

create index module_props_prop_key_idx
    on module_props (prop_key);

create index module_props_prop_value_idx
    on module_props (prop_value);

create table node_events
(
    event_id   bigint        not null
        primary key,
    timestamp  bigint        not null,
    event_type smallint      not null,
    path       varchar(1344) not null
);
create index node_events_full_idx
    on node_events (timestamp, event_id, event_type, path);

create table nodes
(
    node_id            bigint        not null
        primary key,
    node_type          smallint      not null,
    repo               varchar(64)   not null,
    node_path          varchar(1024) not null,
    node_name          varchar(255)  not null,
    depth              smallint      not null,
    created            bigint        not null,
    created_by         varchar(64)   null,
    modified           bigint        not null,
    modified_by        varchar(64)   null,
    updated            bigint        null,
    bin_length         bigint        null,
    sha1_actual        char(40)      null,
    sha1_original      varchar(1024) null,
    md5_actual         char(32)      null,
    md5_original       varchar(1024) null,
    sha256             char(64)      null,
    repo_path_checksum char(40)      null,
    constraint nodes_binaries_fk
        foreign key (sha1_actual) references binaries (sha1)
);

create table bundle_files
(
    id        bigint        not null
        primary key,
    node_id   bigint        not null,
    bundle_id bigint        not null,
    repo_path varchar(1090) not null,
    constraint bundle_id_bundle_files_fk
        foreign key (bundle_id) references artifact_bundles (id),
    constraint node_id_nodes_fk
        foreign key (node_id) references nodes (node_id)
);

create table node_meta_infos
(
    node_id           bigint      not null
        primary key,
    props_modified    bigint      null,
    props_modified_by varchar(64) null,
    constraint node_meta_infos_nodes_fk
        foreign key (node_id) references nodes (node_id)
);

create table node_props
(
    prop_id    bigint        not null
        primary key,
    node_id    bigint        not null,
    prop_key   varchar(255)  null,
    prop_value varchar(2400) null,
    constraint node_props_nodes_fk
        foreign key (node_id) references nodes (node_id)
);

create index node_props_node_prop_value_idx
    on node_props (node_id, prop_key, prop_value);

create index node_props_prop_key_value_idx
    on node_props (prop_key, prop_value);

create index nodes_md5_actual_idx
    on nodes (md5_actual);

create index nodes_node_name_idx
    on nodes (node_name varchar_pattern_ops);

create index nodes_node_path_idx
    on nodes (node_path);

create index nodes_repo_path_checksum
    on nodes (repo_path_checksum);

create index nodes_repo_path_name_idx
    on nodes (repo, node_path, node_name);

create index nodes_sha1_actual_idx
    on nodes (sha1_actual);

create index nodes_sha256_idx
    on nodes (sha256);

create table permission_targets
(
    perm_target_id   bigint        not null
        primary key,
    perm_target_name varchar(64)   not null,
    includes         varchar(1024) null,
    excludes         varchar(1024) null,
    constraint permission_targets_name_idx
        unique (perm_target_name)
);

create table acls
(
    acl_id         bigint      not null
        primary key,
    perm_target_id bigint      null,
    modified       bigint      null,
    modified_by    varchar(64) null,
    constraint acls_permission_targets_fk
        foreign key (perm_target_id) references permission_targets (perm_target_id)
);

create index acls_perm_target_id_idx
    on acls (perm_target_id);

create table permission_target_repos
(
    perm_target_id bigint      not null,
    repo_key       varchar(64) not null,
    constraint permission_target_repos_fk
        foreign key (perm_target_id) references permission_targets (perm_target_id)
);

create table stats
(
    node_id            bigint      not null
        primary key,
    download_count     bigint      null,
    last_downloaded    bigint      null,
    last_downloaded_by varchar(64) null,
    constraint stats_nodes_fk
        foreign key (node_id) references nodes (node_id)
);

create table stats_remote
(
    node_id            bigint        not null,
    origin             varchar(64)   not null,
    download_count     bigint        null,
    last_downloaded    bigint        null,
    last_downloaded_by varchar(64)   null,
    path               varchar(1024) null,
    primary key (node_id, origin),
    constraint stats_remote_nodes_fk
        foreign key (node_id) references nodes (node_id)
);

create table tasks
(
    task_type    varchar(32)   not null,
    task_context varchar(1024) not null,
    created      bigint        null
);

create index tasks_type_context_idx
    on tasks (task_type, task_context);

create table trusted_keys
(
    kid         varchar(6)    not null
        primary key,
    trusted_key varchar(4000) not null,
    fingerprint varchar(255)  not null,
    alias       varchar(255)  null,
    issued      bigint        null,
    issued_by   varchar(255)  null,
    expiry      bigint        null
);

create table unique_ids
(
    index_type varchar(32) not null
        primary key,
    current_id bigint      not null
);

create table watches
(
    watch_id bigint      not null
        primary key,
    node_id  bigint      not null,
    username varchar(64) not null,
    since    bigint      not null,
    constraint watches_nodes_fk
        foreign key (node_id) references nodes (node_id)
);

create index watches_node_id_idx
    on watches (node_id);
