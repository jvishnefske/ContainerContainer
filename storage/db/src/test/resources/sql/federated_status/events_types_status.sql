INSERT INTO node_events (event_id, timestamp, event_type, path, hints, repo) VALUES
(1, 1658735274, 1, 'fed1/', null, 'fed1'),
(2, 1658735274, 1, 'fed1/a.txt', null, 'fed1'),
(3, 1658735275, 1, 'fed1/b.txt', null, 'fed1'),
(4, 1658735274, 2, 'fed1/a.txt', null, 'fed1'),
(5, 1658735274, 3, 'fed1/b.txt', null, 'fed1'),
(6, 1658735274, 1, 'out-of-sync/', null, 'out-of-sync'),
(7, 1658735275, 1, 'out-of-sync/a.txt', null, 'out-of-sync'),
(8, 1658735274, 1, 'non-federated/', null, 'non-federated'),
(9, 1658735275, 1, 'non-federated/a.txt', null, 'non-federated'),
(10, 1658735274, 1, 'hint-not-null/', 'fdr', 'hint-not-null'),
(11, 1658735275, 1, 'hint-not-null/a.txt', 'fdr', 'hint-not-null'),
(12, 1658735274, 1, 'events-afterl/', null, 'events-after'),
(13, 1658735275, 1, 'events-after/a.txt', null, 'events-after'),
(14, 1658735276, 1, 'fed2/', '["ast.async.thread"]', 'fed2'),
(15, 1658735276, 3, 'fed2/a.txt','["file:true","evd.{\"operation\":\"DELETE_SUBJECT\",\"previousSha256\":\"abc\"}"]', 'fed2'),
(16, 1658735276, 3, 'fed2/a.txt','["evd.{\"operation\":\"DELETE_SUBJECT\",\"previousSha256\":\"def\"}", "file:true"]', 'fed2');


INSERT INTO node_event_cursor (operator_id, event_marker, type, status, last_updated) VALUES
('fed1-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/fed1-cursor', 1, 'FEDERATED_REPLICATION', 'HEALTHY', null),
('fed1-federated_replicationhttp://docker.for.mac.localhost:10103/artifactory/fed1-cursor', 5, 'FEDERATED_REPLICATION', 'HEALTHY', null),
('fed1-federated_replicationhttp://docker.for.mac.localhost:10104/artifactory/fed1-cursor', 2, 'FEDERATED_REPLICATION', 'HEALTHY', null),
('fed2-federated_replicationhttp://docker.for.mac.localhost:10105/artifactory/fed2-cursor', 10, 'FEDERATED_REPLICATION', 'HEALTHY', null),
('out-of-sync-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/out-of-sync-cursor', 6, 'FEDERATED_REPLICATION', 'OUT_OF_SYNC', null),
('out-of-sync-federated_replicationhttp://docker.for.mac.localhost:10103/artifactory/out-of-sync-cursor', 7, 'FEDERATED_REPLICATION', 'HEALTHY', null),
('non-federated-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/non-federated-cursor', 8, 'kuku', 'HEALTHY', null),
('hint-not-null-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/hint-not-null-cursor', 10, 'FEDERATED_REPLICATION', 'HEALTHY', null),
('no-match-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/no-match-cursor', 1, 'FEDERATED_REPLICATION', 'HEALTHY', null),
('events-after-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/events-after-cursor', 12, 'FEDERATED_REPLICATION', 'HEALTHY', null);