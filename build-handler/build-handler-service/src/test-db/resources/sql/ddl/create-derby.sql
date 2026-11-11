CREATE TABLE binaries
(
    sha1       CHAR(40) NOT NULL,
    md5        CHAR(32) NOT NULL,
    bin_length BIGINT   NOT NULL,
    sha256     CHAR(64) NOT NULL,
    CONSTRAINT binaries_pk PRIMARY KEY (sha1)
);
CREATE UNIQUE INDEX binaries_md5_idx ON binaries (md5);
CREATE INDEX binaries_sha256_idx ON binaries (sha256);


CREATE TABLE nodes
(
    node_id            BIGINT        NOT NULL,
    node_type          SMALLINT      NOT NULL,
    repo               VARCHAR(64)   NOT NULL,
    node_path          VARCHAR(1024) NOT NULL,
    node_name          VARCHAR(255)  NOT NULL,
    depth              SMALLINT      NOT NULL,
    created            BIGINT        NOT NULL,
    created_by         VARCHAR(64),
    modified           BIGINT        NOT NULL,
    modified_by        VARCHAR(64),
    updated            BIGINT,
    bin_length         BIGINT,
    sha1_actual        CHAR(40),
    sha1_original      VARCHAR(1024),
    md5_actual         CHAR(32),
    md5_original       VARCHAR(1024),
    sha256             CHAR(64),
    repo_path_checksum CHAR(40),
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


CREATE TABLE node_props
(
    prop_id    BIGINT NOT NULL,
    node_id    BIGINT NOT NULL,
    prop_key   VARCHAR(255),
    prop_value VARCHAR(4000),
    CONSTRAINT node_props_pk PRIMARY KEY (prop_id),
    CONSTRAINT node_props_nodes_fk FOREIGN KEY (node_id) REFERENCES nodes (node_id)
);
CREATE INDEX node_props_node_id_idx ON node_props (node_id);
CREATE INDEX node_props_node_prop_value_idx ON node_props (node_id, prop_key, prop_value);
CREATE INDEX node_props_prop_key_value_idx ON node_props (prop_key, prop_value);


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
    immutable    SMALLINT              DEFAULT NULL,
    CONSTRAINT builds_pk PRIMARY KEY (build_id)
);
CREATE UNIQUE INDEX builds_name_num_date_repo_idx ON builds (build_name, build_number, build_date, repo);
CREATE INDEX repo_build_name_date_idx ON builds (repo, build_name, build_date);


CREATE TABLE build_props
(
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


CREATE TABLE build_modules
(
    module_id      BIGINT        NOT NULL,
    build_id       BIGINT        NOT NULL,
    module_name_id VARCHAR(1024) NOT NULL,
    type           VARCHAR(64),
    CONSTRAINT build_modules_pk PRIMARY KEY (module_id),
    CONSTRAINT build_modules_builds_fk FOREIGN KEY (build_id) REFERENCES builds (build_id)
);
CREATE INDEX build_modules_build_id_idx ON build_modules (build_id);


CREATE TABLE module_props
(
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


CREATE TABLE build_artifacts
(
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


CREATE TABLE build_dependencies
(
    dependency_id      BIGINT        NOT NULL,
    module_id          BIGINT        NOT NULL,
    dependency_name_id VARCHAR(1024) NOT NULL,
    dependency_scopes  VARCHAR(4000),
    dependency_type    VARCHAR(64),
    sha1               CHAR(40),
    md5                CHAR(32),
    CONSTRAINT build_dependencies_pk PRIMARY KEY (dependency_id),
    CONSTRAINT build_dependencies_modules_fk FOREIGN KEY (module_id) REFERENCES build_modules (module_id)
);
CREATE INDEX build_dependencies_module_idx ON build_dependencies (module_id);
CREATE INDEX build_dependencies_sha1_idx ON build_dependencies (sha1);
CREATE INDEX build_dependencies_md5_idx ON build_dependencies (md5);


CREATE TABLE build_release_bundles
(
    build_rb_id       BIGINT       NOT NULL,
    build_id          BIGINT       NOT NULL,
    bundle_repository VARCHAR(64)  NOT NULL,
    bundle_name       VARCHAR(255) NOT NULL,
    bundle_version    VARCHAR(255) NOT NULL,
    CONSTRAINT build_rb_pk PRIMARY KEY (build_rb_id),
    CONSTRAINT build_rb_build_id_fk FOREIGN KEY (build_id) REFERENCES builds (build_id) ON DELETE CASCADE
);
CREATE INDEX build_rb_build_id_idx ON build_release_bundles (build_id);
CREATE INDEX build_rb_bundle_repository_idx ON build_release_bundles (bundle_repository);
CREATE INDEX build_rb_bundle_name_idx ON build_release_bundles (bundle_name);
CREATE INDEX build_rb_bundle_version_idx ON build_release_bundles (bundle_version);

