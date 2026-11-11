-- noinspection SqlDialectInspectionForFile

INSERT INTO node_event_cursor (operator_id, event_marker, type, status, last_updated) VALUES
('test1-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test1-repo-cursor', 22,'FEDERATED_REPLICATION', 'HEALTHY', null),
('test2-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test2-repo-cursor', 25,'FEDERATED_REPLICATION', 'HEALTHY', null),
('test3-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test3-repo-cursor', 29,'FEDERATED_REPLICATION', 'HEALTHY', null),
('test4-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test4-repo-cursor', 31,'FEDERATED_REPLICATION', 'HEALTHY', null),
('test5-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test5-repo-cursor', 32,'FEDERATED_REPLICATION', 'PENDING_FS', null),
('test6-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test6-repo-cursor', 33,'FEDERATED_REPLICATION', 'PENDING_FS', null),
('test7-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test7-repo-cursor', 34,'FEDERATED_REPLICATION', 'FS_RUNNING', null),
('test8-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test8-repo-cursor', 35,'FEDERATED_REPLICATION', 'OUT_OF_SYNC', null),
('test9-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test9-repo-cursor', 36,'FEDERATED_REPLICATION', 'EXHAUSTED', null),
('test10-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test10-repo-cursor', 37,'FEDERATED_REPLICATION', 'DISABLED', null),
('test11-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test11-repo-cursor', 38,'FEDERATED_REPLICATION', 'FS_RUNNING', null),
('test12-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test12-repo-cursor', 39,'FEDERATED_REPLICATION', 'FS_RUNNING', null),
('test13-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test13-repo-cursor', 40,'FEDERATED_REPLICATION', 'DISABLED', null),
('non-federated-repohttp://docker.for.mac.localhost:10102/artifactory/non-federated-repo-cursor', 28,'METADATA_EVENTS', 'HEALTHY', null);
