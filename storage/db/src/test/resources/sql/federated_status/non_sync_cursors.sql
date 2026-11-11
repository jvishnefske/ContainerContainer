INSERT INTO node_event_cursor (operator_id, event_marker, type, status, last_updated) VALUES
('t1-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/t1-repo-cursor', 1,'FEDERATED_REPLICATION', 'HEALTHY', null),
('t2-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/t2-repo-cursor', 1,'FEDERATED_REPLICATION', 'OUT_OF_SYNC', null),
('t3-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/t3-repo-cursor', 1,'FEDERATED_REPLICATION', 'OUT_OF_SYNC', null),
('t4-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/t4-repo-cursor', 1,'FEDERATED_REPLICATION', 'OUT_OF_SYNC', null),
('non-federated-repohttp://docker.for.mac.localhost:10102/artifactory/non-federated-repo-cursor', 1,'METADATA_EVENTS', 'HEALTHY', null);
