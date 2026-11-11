create table archive_names
(
    name_id    number(19)    not null
        primary key,
    entry_name varchar2(255) null,
    constraint archive_names_name_idx
        unique (entry_name)
);

create table archive_paths
(
    path_id    number(19)     not null
        primary key,
    entry_path varchar2(1024) null
);

create index archive_paths_path_idx
    on archive_paths (entry_path);

create table artifact_bundles
(
    id           number(19)     not null
        primary key,
    name         varchar2(255)  not null,
    version      varchar2(30)   not null,
    status       varchar2(64)   not null,
    date_created number(19)     not null,
    signature    varchar2(1024) not null
);

create unique index name_version_idx
    on artifact_bundles (name, version);
create index name_version_status_idx
    on artifact_bundles (name, version, status);

create table artifactory_servers
(
    server_id                varchar2(41)  not null
        primary key,
    start_time               number(19)    not null,
    context_url              varchar2(255) null,
    membership_port          number(10)    null,
    server_state             varchar2(12)  not null,
    server_role              varchar2(12)  not null,
    last_heartbeat           number(19)    not null,
    artifactory_version      varchar2(30)  not null,
    artifactory_revision     number(10)    null,
    artifactory_release      number(19)    null,
    artifactory_running_mode varchar2(12)  not null,
    license_hash             varchar2(41)  not null
);

create table binaries
(
    sha1       char(40)   not null
        primary key,
    md5        char(32)   not null,
    bin_length number(19) not null,
    sha256     char(64)   not null,
    constraint binaries_md5_idx
        unique (md5)
);

create index binaries_sha256_idx
    on binaries (sha256);

create table binary_blobs
(
    sha1 char(40) not null
        primary key,
    data blob     null
);

create table build_jsons
(
    build_id        number(19) not null
        primary key,
    build_info_json blob       null
);

create table builds
(
    build_id     number(19)     not null
        primary key,
    build_name   varchar2(255)  not null,
    build_number varchar2(255)  not null,
    build_date   number(19)     not null,
    ci_url       varchar2(1024) null,
    created      number(19)     not null,
    created_by   varchar2(64)   null,
    modified     number(19)     null,
    modified_by  varchar2(64)   null
);

create unique index builds_name_number_date_idx
    on builds (build_name, build_number, build_date);

create table build_modules
(
    module_id      number(19)     not null
        primary key,
    build_id       number(19)     not null,
    module_name_id varchar2(1024) not null,
    constraint build_modules_builds_fk
        foreign key (build_id) references builds (build_id)
);

create table build_artifacts
(
    artifact_id   number(19)     not null
        primary key,
    module_id     number(19)     not null,
    artifact_name varchar2(1024) not null,
    artifact_type varchar2(64)   null,
    sha1          char(40)       null,
    md5           char(32)       null,
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
    dependency_id      number(19)     not null
        primary key,
    module_id          number(19)     not null,
    dependency_name_id varchar2(1024) not null,
    dependency_scopes  varchar2(1024) null,
    dependency_type    varchar2(64)   null,
    sha1               char(40)       null,
    md5                char(32)       null,
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
    build_id          number(19)     not null,
    created           number(19)     not null,
    created_by        varchar2(64)   null,
    status            varchar2(64)   not null,
    repo              varchar2(64)   null,
    promotion_comment varchar2(1024) null,
    ci_user           varchar2(64)   null,
    constraint build_promotions_builds_fk
        foreign key (build_id) references builds (build_id)
);
create unique index build_promotions_created_idx
    on build_promotions (build_id, created);

create index build_promotions_status_idx
    on build_promotions (status);

create table build_props
(
    prop_id    number(19)     not null
        primary key,
    build_id   number(19)     not null,
    prop_key   varchar2(255)  null,
    prop_value varchar2(2048) null,
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
    id        number(19) not null
        primary key,
    data      blob       not null,
    bundle_id number(19) not null,
    constraint bundle_id_idx
        unique (bundle_id),
    constraint bundle_id_bundle_blobs_fk
        foreign key (bundle_id) references artifact_bundles (id)
);

create table configs
(
    config_name   varchar2(255) not null
        primary key,
    last_modified number(19)    not null,
    data          blob          not null
);

create table db_properties
(
    installation_date    number(19)   not null
        primary key,
    artifactory_version  varchar2(30) not null,
    artifactory_revision number(10)   null,
    artifactory_release  number(19)   null
);

create table distributed_locks
(
    category          varchar2(64)  not null,
    lock_key          varchar2(255) not null,
    owner             varchar2(64)  not null,
    owner_thread      number(19)    not null,
    owner_thread_name varchar2(64)  not null,
    acquire_time      number(19)    not null,
    primary key (category, lock_key)
);

create index distributed_locks_owner
    on distributed_locks (owner);

create index distributed_locks_owner_thread
    on distributed_locks (owner_thread);

create table indexed_archives
(
    archive_sha1        char(40)   not null
        primary key,
    indexed_archives_id number(19) not null,
    constraint indexed_archives_id_uq
        unique (indexed_archives_id),
    constraint indexed_archives_binaries_fk
        foreign key (archive_sha1) references binaries (sha1)
);

create table indexed_archives_entries
(
    indexed_archives_id number(19) not null,
    entry_path_id       number(19) not null,
    entry_name_id       number(19) not null,
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
    is_unique_key  char(1)      not null,
    status         varchar2(16) not null,
    set_by_node_id varchar2(64) not null,
    kid            char(64)     not null
        primary key,
    expires        number(19)   not null,
    constraint unique_key
        unique (is_unique_key)
);

create table module_props
(
    prop_id    number(19)     not null
        primary key,
    module_id  number(19)     not null,
    prop_key   varchar2(255)  null,
    prop_value varchar2(2048) null,
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
    event_id   number(19)     not null
        primary key,
    timestamp  number(19)     not null,
    event_type number(5)      not null,
    path       varchar2(1344) not null
);
create index node_events_full_idx
    on node_events (timestamp, event_id, event_type, path);

create table nodes
(
    node_id            number(19)     not null
        primary key,
    node_type          number(5)      not null,
    repo               varchar2(64)   not null,
    node_path          varchar2(1024) not null,
    node_name          varchar2(255)  not null,
    depth              number(5)      not null,
    created            number(19)     not null,
    created_by         varchar2(64)   null,
    modified           number(19)     not null,
    modified_by        varchar2(64)   null,
    updated            number(19)     null,
    bin_length         number(19)     null,
    sha1_actual        char(40)       null,
    sha1_original      varchar2(1024) null,
    md5_actual         char(32)       null,
    md5_original       varchar2(1024) null,
    sha256             char(64)       null,
    repo_path_checksum char(40)       null,
    constraint nodes_binaries_fk
        foreign key (sha1_actual) references binaries (sha1)
);

create table bundle_files
(
    id        number(19)     not null
        primary key,
    node_id   number(19)     not null,
    bundle_id number(19)     not null,
    repo_path varchar2(1090) not null,
    constraint bundle_id_bundle_files_fk
        foreign key (bundle_id) references artifact_bundles (id),
    constraint node_id_nodes_fk
        foreign key (node_id) references nodes (node_id)
);

create table node_meta_infos
(
    node_id           number(19)   not null
        primary key,
    props_modified    number(19)   null,
    props_modified_by varchar2(64) null,
    constraint node_meta_infos_nodes_fk
        foreign key (node_id) references nodes (node_id)
);

create table node_props
(
    prop_id    number(19)     not null
        primary key,
    node_id    number(19)     not null,
    prop_key   varchar2(255)  null,
    prop_value varchar2(2400) null,
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
    on nodes (node_name);

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
    perm_target_id   number(19)     not null
        primary key,
    perm_target_name varchar2(64)   not null,
    includes         varchar2(1024) null,
    excludes         varchar2(1024) null,
    constraint permission_targets_name_idx
        unique (perm_target_name)
);

create table acls
(
    acl_id         number(19)   not null
        primary key,
    perm_target_id number(19)   null,
    modified       number(19)   null,
    modified_by    varchar2(64) null,
    constraint acls_permission_targets_fk
        foreign key (perm_target_id) references permission_targets (perm_target_id)
);

create index acls_perm_target_id_idx
    on acls (perm_target_id);

create table permission_target_repos
(
    perm_target_id number(19)   not null,
    repo_key       varchar2(64) not null,
    constraint permission_target_repos_fk
        foreign key (perm_target_id) references permission_targets (perm_target_id)
);

create table stats
(
    node_id            number(19)   not null
        primary key,
    download_count     number(19)   null,
    last_downloaded    number(19)   null,
    last_downloaded_by varchar2(64) null,
    constraint stats_nodes_fk
        foreign key (node_id) references nodes (node_id)
);

create table stats_remote
(
    node_id            number(19)     not null,
    origin             varchar2(64)   not null,
    download_count     number(19)     null,
    last_downloaded    number(19)     null,
    last_downloaded_by varchar2(64)   null,
    path               varchar2(1024) null,
    primary key (node_id, origin),
    constraint stats_remote_nodes_fk
        foreign key (node_id) references nodes (node_id)
);

create table tasks
(
    task_type    varchar2(32)   not null,
    task_context varchar2(1024) not null,
    created      number(19)     null
);

create index tasks_type_context_idx
    on tasks (task_type, task_context);

create table trusted_keys
(
    kid         varchar2(6)    not null
        primary key,
    trusted_key varchar2(4000) not null,
    fingerprint varchar2(255)  not null,
    alias       varchar2(255)  null,
    issued      number(19)     null,
    issued_by   varchar2(255)  null,
    expiry      number(19)     null
);

create table unique_ids
(
    index_type varchar2(32) not null
        primary key,
    current_id number(19)   not null
);

create table watches
(
    watch_id number(19)   not null
        primary key,
    node_id  number(19)   not null,
    username varchar2(64) not null,
    since    number(19)   not null,
    constraint watches_nodes_fk
        foreign key (node_id) references nodes (node_id)
);

create index watches_node_id_idx
    on watches (node_id);
