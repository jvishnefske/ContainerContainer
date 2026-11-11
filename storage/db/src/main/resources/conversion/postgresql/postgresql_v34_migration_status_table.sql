CREATE TABLE migration_status (
  identifier          VARCHAR(255)         NOT NULL,
  started             BIGINT               NOT NULL,
  finished            BIGINT               NOT NULL,
  migration_info_blob BYTEA,
  CONSTRAINT identifier_pk PRIMARY KEY (identifier)
);