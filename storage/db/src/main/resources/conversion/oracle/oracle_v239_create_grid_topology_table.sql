CREATE TABLE grid_topology (
  grid_topology_id    VARCHAR2(255) NOT NULL,
  topology_type       VARCHAR2(256) NOT NULL,
  additional_sites    BLOB,
  creation_date        NUMBER(19, 0)  NOT NULL,
  modification_date    NUMBER(19, 0)  NOT NULL,
  CONSTRAINT grid_topology_pk PRIMARY KEY (grid_topology_id)
);

