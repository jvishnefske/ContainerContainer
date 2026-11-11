CREATE TABLE grid_topology (
  grid_topology_id    VARCHAR(255) NOT NULL,
  topology_type       VARCHAR(256) NOT NULL,
  additional_sites    BLOB,
  creation_date       BIGINT       NOT NULL,
  modification_date   BIGINT       NOT NULL,
  CONSTRAINT grid_topology_pk PRIMARY KEY (grid_topology_id)
);

