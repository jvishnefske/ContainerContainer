-- Embedded Access schema for PostgreSQL. EmbeddedAccessSchema.ensure() applies this script in one transaction
-- when the ea_kv table is absent. Every statement ends with ';'. Times are epoch milliseconds. JSON bodies hold
-- the fields that are never queried on (custom data, actions, resources).

CREATE TABLE ea_users (
  username               VARCHAR(255)  NOT NULL,
  realm                  VARCHAR(255)  NOT NULL,
  status                 VARCHAR(16)   NOT NULL,
  password_hash          VARCHAR(1024),
  must_change_password   BOOLEAN       NOT NULL,
  password_last_modified BIGINT,
  email                  VARCHAR(255),
  admin                  BOOLEAN       NOT NULL,
  last_login_time        BIGINT,
  last_login_ip          VARCHAR(64),
  body                   TEXT          NOT NULL,
  created                BIGINT        NOT NULL,
  modified               BIGINT        NOT NULL,
  CONSTRAINT ea_users_pk PRIMARY KEY (username)
);
CREATE INDEX ea_users_email_idx ON ea_users (email);

CREATE TABLE ea_groups (
  group_name       VARCHAR(255)  NOT NULL,
  description      VARCHAR(1024),
  auto_join        BOOLEAN       NOT NULL,
  admin_privileges BOOLEAN       NOT NULL,
  realm            VARCHAR(255)  NOT NULL,
  realm_attributes VARCHAR(4096),
  external_id      VARCHAR(255),
  body             TEXT          NOT NULL,
  created          BIGINT        NOT NULL,
  modified         BIGINT        NOT NULL,
  CONSTRAINT ea_groups_pk PRIMARY KEY (group_name)
);

CREATE TABLE ea_user_groups (
  username   VARCHAR(255) NOT NULL,
  group_name VARCHAR(255) NOT NULL,
  CONSTRAINT ea_user_groups_pk PRIMARY KEY (username, group_name),
  CONSTRAINT ea_user_groups_user_fk FOREIGN KEY (username) REFERENCES ea_users (username) ON DELETE CASCADE,
  CONSTRAINT ea_user_groups_group_fk FOREIGN KEY (group_name) REFERENCES ea_groups (group_name) ON DELETE CASCADE
);
CREATE INDEX ea_user_groups_group_idx ON ea_user_groups (group_name);

CREATE TABLE ea_permissions (
  perm_name     VARCHAR(255) NOT NULL,
  display_name  VARCHAR(255) NOT NULL,
  resource_type VARCHAR(64)  NOT NULL,
  service_id    VARCHAR(255) NOT NULL,
  implicit      BOOLEAN      NOT NULL,
  body          TEXT         NOT NULL,
  created       BIGINT       NOT NULL,
  modified      BIGINT       NOT NULL,
  CONSTRAINT ea_permissions_pk PRIMARY KEY (perm_name)
);
CREATE INDEX ea_permissions_type_name_idx ON ea_permissions (resource_type, display_name);

CREATE TABLE ea_tokens (
  jti          VARCHAR(255)  NOT NULL,
  subject      VARCHAR(512)  NOT NULL,
  issuer       VARCHAR(255)  NOT NULL,
  scope        TEXT          NOT NULL,
  scope_sha256 CHAR(64)      NOT NULL,
  description  VARCHAR(1024),
  iat          BIGINT        NOT NULL,
  exp          BIGINT,
  refreshable  BOOLEAN       NOT NULL,
  refresh_hash CHAR(64),
  revoked      BOOLEAN       NOT NULL,
  CONSTRAINT ea_tokens_pk PRIMARY KEY (jti),
  CONSTRAINT ea_tokens_refresh_uq UNIQUE (refresh_hash)
);
CREATE INDEX ea_tokens_subject_idx ON ea_tokens (subject);
CREATE INDEX ea_tokens_exp_idx ON ea_tokens (exp);

CREATE TABLE ea_kv (
  k        VARCHAR(255) NOT NULL,
  revision BIGINT       NOT NULL,
  v        TEXT         NOT NULL,
  CONSTRAINT ea_kv_pk PRIMARY KEY (k)
);
