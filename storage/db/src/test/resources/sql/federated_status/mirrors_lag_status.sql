INSERT INTO node_events (event_id, timestamp, event_type, path, hints, repo) VALUES
(22, 1658735274, 1, 'test1-repo/t.txt', null, 'test1-repo'),
(23, 1658735275, 2, 'test1-repo/a/f.txt', null, 'test1-repo'),
(24, 1658735276, 2, 'test1-repo/a/t.txt', null, 'test1-repo'),
(25, 1658735280, 1, 'test2-repo/t.txt', null, 'test2-repo'),
(26, 1658735281, 2, 'test2-repo/a/f.txt', null, 'test2-repo'),
(27, 1658735282, 2, 'test2-repo/a/t.txt', null, 'test2-repo'),
(28, 1658735274, 1, 'non-federated-repo/t.txt', null, 'test3-repo'),
(29, 1658735283, 1, 'test3-repo/d.txt', 'fdr', 'test3-repo'),
(30, 1658735284, 1, 'test3-repo/t.txt', 'fdr', 'test3-repo'),
(31, 1658735284, 1, 'test4-repo/t.txt', 'fdr', 'test4-repo');




INSERT INTO node_event_cursor (operator_id, event_marker, type, status, last_updated) VALUES
('test1-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test1-repo-cursor', 22,'FEDERATED_REPLICATION', 'HEALTHY', null),
('test2-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test2-repo-cursor', 25,'FEDERATED_REPLICATION', 'HEALTHY', null),
('test3-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test3-repo-cursor', 29,'FEDERATED_REPLICATION', 'HEALTHY', null),
('test4-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test4-repo-cursor', 31,'FEDERATED_REPLICATION', 'HEALTHY', null),
('non-federated-repohttp://docker.for.mac.localhost:10102/artifactory/non-federated-repo-cursor', 28,'METADATA_EVENTS', 'HEALTHY', null);
