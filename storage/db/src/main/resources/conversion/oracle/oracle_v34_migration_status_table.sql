CREATE TABLE migration_status (
  identifier          VARCHAR2(255)         NOT NULL,
  started             NUMBER(19, 0)         NOT NULL,
  finished            NUMBER(19, 0)         NOT NULL,
  migration_info_blob BLOB,
  CONSTRAINT identifier_pk PRIMARY KEY (identifier)
);