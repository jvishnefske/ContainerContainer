create table ARCHIVE_NAMES
(
    NAME_ID BIGINT not null
        constraint ARCHIVE_NAMES_PK
            primary key,
    ENTRY_NAME VARCHAR(255)
        constraint ARCHIVE_NAMES_NAME_IDX
            unique
);

create table ARCHIVE_PATHS
(
    PATH_ID BIGINT not null
        constraint ARCHIVE_PATHS_PK
            primary key,
    ENTRY_PATH VARCHAR(1024)
        constraint ARCHIVE_PATHS_PATH_IDX
            unique
);

create table ARTIFACTORY_SERVERS
(
    SERVER_ID VARCHAR(41) not null
        constraint ARTIFACTORY_SERVERS_PK
            primary key,
    START_TIME BIGINT not null,
    CONTEXT_URL VARCHAR(255),
    MEMBERSHIP_PORT INTEGER,
    SERVER_STATE VARCHAR(12) not null,
    SERVER_ROLE VARCHAR(12) not null,
    LAST_HEARTBEAT BIGINT not null,
    ARTIFACTORY_VERSION VARCHAR(30) not null,
    ARTIFACTORY_REVISION INTEGER,
    ARTIFACTORY_RELEASE BIGINT,
    ARTIFACTORY_RUNNING_MODE VARCHAR(12) not null,
    LICENSE_HASH VARCHAR(41) not null
);

create table ARTIFACT_BUNDLES
(
    ID BIGINT not null
        constraint BUNDLE_PK
            primary key,
    NAME VARCHAR(255) not null,
    VERSION VARCHAR(30) not null,
    STATUS VARCHAR(64) not null,
    DATE_CREATED BIGINT not null,
    SIGNATURE VARCHAR(1024) not null
);

create index NAME_VERSION_IDX
	on ARTIFACT_BUNDLES (NAME, VERSION);

create index NAME_VERSION_STATUS_IDX
	on ARTIFACT_BUNDLES (NAME, VERSION, STATUS);

create table BINARIES
(
    SHA1 CHAR(40) not null
        constraint BINARIES_PK
            primary key,
    MD5 CHAR(32) not null
        constraint BINARIES_MD5_IDX
            unique,
    BIN_LENGTH BIGINT not null,
    SHA256 CHAR(64) not null
);

create index BINARIES_SHA256_IDX
	on BINARIES (SHA256);

create table BINARY_BLOBS
(
    SHA1 CHAR(40) not null
        constraint BINARY_BLOBS_PK
            primary key,
    DATA BLOB
);

create table BUILDS
(
    BUILD_ID BIGINT not null
        constraint BUILDS_PK
            primary key,
    BUILD_NAME VARCHAR(255) not null,
    BUILD_NUMBER VARCHAR(255) not null,
    BUILD_DATE BIGINT not null,
    CI_URL VARCHAR(1024),
    CREATED BIGINT not null,
    CREATED_BY VARCHAR(64),
    MODIFIED BIGINT,
    MODIFIED_BY VARCHAR(64)
);

create unique index BUILDS_NAME_NUMBER_DATE_IDX
	on BUILDS (BUILD_NAME, BUILD_NUMBER, BUILD_DATE);

create table BUILD_MODULES
(
    MODULE_ID BIGINT not null
        constraint BUILD_MODULES_PK
            primary key,
    BUILD_ID BIGINT not null
        constraint BUILD_MODULES_BUILDS_FK
            references BUILDS,
    MODULE_NAME_ID VARCHAR(1024) not null
);

create table BUILD_ARTIFACTS
(
    ARTIFACT_ID BIGINT not null
        constraint BUILD_ARTIFACTS_PK
            primary key,
    MODULE_ID BIGINT not null
        constraint BUILD_ARTIFACTS_MODULES_FK
            references BUILD_MODULES,
    ARTIFACT_NAME VARCHAR(1024) not null,
    ARTIFACT_TYPE VARCHAR(64),
    SHA1 CHAR(40),
    MD5 CHAR(32)
);

create index BUILD_ARTIFACTS_MD5_IDX
	on BUILD_ARTIFACTS (MD5);

create index BUILD_ARTIFACTS_SHA1_IDX
	on BUILD_ARTIFACTS (SHA1);

create table BUILD_DEPENDENCIES
(
    DEPENDENCY_ID BIGINT not null
        constraint BUILD_DEPENDENCIES_PK
            primary key,
    MODULE_ID BIGINT not null
        constraint BUILD_DEPENDENCIES_MODULES_FK
            references BUILD_MODULES,
    DEPENDENCY_NAME_ID VARCHAR(1024) not null,
    DEPENDENCY_SCOPES VARCHAR(1024),
    DEPENDENCY_TYPE VARCHAR(64),
    SHA1 CHAR(40),
    MD5 CHAR(32)
);

create index BUILD_DEPENDENCIES_MD5_IDX
	on BUILD_DEPENDENCIES (MD5);

create index BUILD_DEPENDENCIES_SHA1_IDX
	on BUILD_DEPENDENCIES (SHA1);

create table BUILD_PROMOTIONS
(
    BUILD_ID BIGINT not null
        constraint BUILD_PROMOTIONS_BUILDS_FK
            references BUILDS,
    CREATED BIGINT not null,
    CREATED_BY VARCHAR(64),
    STATUS VARCHAR(64) not null,
    REPO VARCHAR(64),
    PROMOTION_COMMENT VARCHAR(1024),
    CI_USER VARCHAR(64)
);

create unique index BUILD_PROMOTIONS_CREATED_IDX
    on BUILD_PROMOTIONS (BUILD_ID, CREATED);

create index BUILD_PROMOTIONS_STATUS_IDX
	on BUILD_PROMOTIONS (STATUS);

create table BUILD_PROPS
(
    PROP_ID BIGINT not null
        constraint BUILD_PROPS_PK
            primary key,
    BUILD_ID BIGINT not null
        constraint BUILD_PROPS_BUILDS_FK
            references BUILDS,
    PROP_KEY VARCHAR(255),
    PROP_VALUE VARCHAR(2048)
);

create index BUILD_PROPS_PROP_KEY_IDX
	on BUILD_PROPS (PROP_KEY);

create index BUILD_PROPS_PROP_VALUE_IDX
	on BUILD_PROPS (PROP_VALUE);

create table BUNDLE_BLOBS
(
    ID BIGINT not null
        constraint BUNDLE_BLOBS_PK
            primary key,
    DATA BLOB not null,
    BUNDLE_ID BIGINT not null
        constraint BUNDLE_ID_IDX
            unique
        constraint BUNDLE_ID_BUNDLE_BLOBS_FK
            references ARTIFACT_BUNDLES
);

create table CONFIGS
(
    CONFIG_NAME VARCHAR(255) not null
        constraint CONFIGS_PK
            primary key,
    LAST_MODIFIED BIGINT not null,
    DATA BLOB not null
);

create table DB_PROPERTIES
(
    INSTALLATION_DATE BIGINT not null
        constraint DB_PROPERTIES_PK
            primary key,
    ARTIFACTORY_VERSION VARCHAR(30) not null,
    ARTIFACTORY_REVISION INTEGER,
    ARTIFACTORY_RELEASE BIGINT
);

create table DISTRIBUTED_LOCKS
(
    CATEGORY VARCHAR(64) not null,
    LOCK_KEY VARCHAR(255) not null,
    OWNER VARCHAR(64) not null,
    OWNER_THREAD BIGINT not null,
    OWNER_THREAD_NAME VARCHAR(64) not null,
    ACQUIRE_TIME BIGINT not null,
    constraint LOCKS_PK
        primary key (CATEGORY, LOCK_KEY)
);

create index DISTRIBUTED_LOCKS_OWNER
	on DISTRIBUTED_LOCKS (OWNER);

create index DISTRIBUTED_LOCKS_OWNER_THREAD
	on DISTRIBUTED_LOCKS (OWNER_THREAD);

create table INDEXED_ARCHIVES
(
    ARCHIVE_SHA1 CHAR(40) not null
        constraint INDEXED_ARCHIVES_PK
            primary key
        constraint INDEXED_ARCHIVES_BINARIES_FK
            references BINARIES,
    INDEXED_ARCHIVES_ID BIGINT not null
        unique
);

create table INDEXED_ARCHIVES_ENTRIES
(
    INDEXED_ARCHIVES_ID BIGINT not null
        constraint INDEXED_ARCHIVES_ID_FK
            references INDEXED_ARCHIVES (INDEXED_ARCHIVES_ID),
    ENTRY_PATH_ID BIGINT not null
        constraint ENTRY_PATH_ID_FK
            references ARCHIVE_PATHS,
    ENTRY_NAME_ID BIGINT not null
        constraint ENTRY_NAME_ID_FK
            references ARCHIVE_NAMES,
    constraint INDEXED_ARCHIVES_ENTRIES_PK
        primary key (INDEXED_ARCHIVES_ID, ENTRY_PATH_ID, ENTRY_NAME_ID)
);

create table MASTER_KEY_STATUS
(
    IS_UNIQUE_KEY SMALLINT not null
        unique,
    STATUS VARCHAR(16) not null,
    SET_BY_NODE_ID VARCHAR(64) not null,
    KID CHAR(64) not null
        constraint KID_PK
            primary key,
    EXPIRES BIGINT not null
);

create table MODULE_PROPS
(
    PROP_ID BIGINT not null
        constraint MODULE_PROPS_PK
            primary key,
    MODULE_ID BIGINT not null
        constraint MODULE_PROPS_MODULES_FK
            references BUILD_MODULES,
    PROP_KEY VARCHAR(255),
    PROP_VALUE VARCHAR(2048)
);

create index MODULE_PROPS_PROP_KEY_IDX
	on MODULE_PROPS (PROP_KEY);

create index MODULE_PROPS_PROP_VALUE_IDX
	on MODULE_PROPS (PROP_VALUE);

create table NODES
(
    NODE_ID BIGINT not null
        constraint NODES_PK
            primary key,
    NODE_TYPE SMALLINT not null,
    REPO VARCHAR(64) not null,
    NODE_PATH VARCHAR(1024) not null,
    NODE_NAME VARCHAR(255) not null,
    DEPTH SMALLINT not null,
    CREATED BIGINT not null,
    CREATED_BY VARCHAR(64),
    MODIFIED BIGINT not null,
    MODIFIED_BY VARCHAR(64),
    UPDATED BIGINT,
    BIN_LENGTH BIGINT,
    SHA1_ACTUAL CHAR(40)
        constraint NODES_BINARIES_FK
            references BINARIES,
    SHA1_ORIGINAL VARCHAR(1024),
    MD5_ACTUAL CHAR(32),
    MD5_ORIGINAL VARCHAR(1024),
    SHA256 CHAR(64),
    REPO_PATH_CHECKSUM CHAR(40),
    constraint NODES_REPO_PATH_NAME_IDX
        unique (REPO, NODE_PATH, NODE_NAME)
);

create table BUNDLE_FILES
(
    ID BIGINT not null
        constraint BUNDLE_NODE_PK
            primary key,
    NODE_ID BIGINT not null
        constraint NODE_ID_NODES_FK
            references NODES,
    BUNDLE_ID BIGINT not null
        constraint BUNDLE_ID_BUNDLE_FILES_FK
            references ARTIFACT_BUNDLES,
    REPO_PATH VARCHAR(1090) not null
);

create index NODES_MD5_ACTUAL_IDX
	on NODES (MD5_ACTUAL);

create index NODES_NODE_NAME_IDX
	on NODES (NODE_NAME);

create index NODES_NODE_PATH_IDX
	on NODES (NODE_PATH);

create index NODES_SHA256_IDX
	on NODES (SHA256);

create table NODE_EVENTS
(
    EVENT_ID BIGINT not null
        constraint NODE_EVENTS_PK
            primary key,
    TIMESTAMP BIGINT not null,
    EVENT_TYPE SMALLINT not null,
    PATH VARCHAR(1344) not null
);

create index NODE_EVENTS_FULL_IDX
	on NODE_EVENTS (TIMESTAMP, EVENT_ID, EVENT_TYPE, PATH);

create table NODE_META_INFOS
(
    NODE_ID BIGINT not null
        constraint NODE_META_INFOS_PK
            primary key
        constraint NODE_META_INFOS_NODES_FK
            references NODES,
    PROPS_MODIFIED BIGINT,
    PROPS_MODIFIED_BY VARCHAR(64)
);

create table NODE_PROPS
(
    PROP_ID BIGINT not null
        constraint NODE_PROPS_PK
            primary key,
    NODE_ID BIGINT not null
        constraint NODE_PROPS_NODES_FK
            references NODES,
    PROP_KEY VARCHAR(255),
    PROP_VALUE VARCHAR(2400)
);

create index NODE_PROPS_PROP_KEY_IDX
	on NODE_PROPS (PROP_KEY);

create index NODE_PROPS_PROP_VALUE_IDX
	on NODE_PROPS (PROP_VALUE);

create table PERMISSION_TARGETS
(
    PERM_TARGET_ID BIGINT not null
        constraint PERMISSION_TARGETS_PK
            primary key,
    PERM_TARGET_NAME VARCHAR(64) not null
        constraint PERMISSION_TARGETS_NAME_IDX
            unique,
    INCLUDES VARCHAR(1024),
    EXCLUDES VARCHAR(1024)
);

create table ACLS
(
    ACL_ID BIGINT not null
        constraint ACLS_PK
            primary key,
    PERM_TARGET_ID BIGINT
        constraint ACLS_PERMISSION_TARGETS_FK
            references PERMISSION_TARGETS,
    MODIFIED BIGINT,
    MODIFIED_BY VARCHAR(64)
);

create table PERMISSION_TARGET_REPOS
(
    PERM_TARGET_ID BIGINT not null
        constraint PERMISSION_TARGET_REPOS_FK
            references PERMISSION_TARGETS,
    REPO_KEY VARCHAR(64) not null
);

create table STATS
(
    NODE_ID BIGINT not null
        constraint STATS_PK
            primary key
        constraint STATS_NODES_FK
            references NODES,
    DOWNLOAD_COUNT BIGINT,
    LAST_DOWNLOADED BIGINT,
    LAST_DOWNLOADED_BY VARCHAR(64)
);

create table STATS_REMOTE
(
    NODE_ID BIGINT not null
        constraint STATS_REMOTE_NODES_FK
            references NODES,
    ORIGIN VARCHAR(64) not null,
    DOWNLOAD_COUNT BIGINT,
    LAST_DOWNLOADED BIGINT,
    LAST_DOWNLOADED_BY VARCHAR(64),
    PATH VARCHAR(1024),
    constraint STATS_REMOTE_PK
        primary key (NODE_ID, ORIGIN)
);

create table TASKS
(
    TASK_TYPE VARCHAR(32) not null,
    TASK_CONTEXT VARCHAR(1024) not null,
    CREATED BIGINT
);

create index TASKS_TYPE_CONTEXT_IDX
	on TASKS (TASK_TYPE, TASK_CONTEXT);

create table TRUSTED_KEYS
(
    KID VARCHAR(6) not null
        constraint TRUSTED_KEYS_PK
            primary key,
    TRUSTED_KEY VARCHAR(4000) not null,
    FINGERPRINT VARCHAR(255) not null,
    ALIAS VARCHAR(255),
    ISSUED BIGINT,
    ISSUED_BY VARCHAR(255),
    EXPIRY BIGINT
);

create table UNIQUE_IDS
(
    INDEX_TYPE VARCHAR(32) not null
        constraint UNIQUE_IDS_PK
            primary key,
    CURRENT_ID BIGINT not null
);

create table WATCHES
(
    WATCH_ID BIGINT not null
        constraint WATCHES_PK
            primary key,
    NODE_ID BIGINT not null
        constraint WATCHES_NODES_FK
            references NODES,
    USERNAME VARCHAR(64) not null,
    SINCE BIGINT not null
);