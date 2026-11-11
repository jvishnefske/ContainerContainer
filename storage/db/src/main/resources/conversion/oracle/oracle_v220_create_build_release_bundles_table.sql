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
