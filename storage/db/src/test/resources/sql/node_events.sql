INSERT INTO node_events VALUES
(1, 1515653250001, 1, 'repo1/fileA', null, 'repo1'),
(2, 1515653250002, 1, 'repo1/fileA1', null, 'repo1'),
(3, 1515653250003, 1, 'repo1/fileA2', null, 'repo1'),
(4, 1515653250004, 1, 'repo1/fileA3', null, 'repo1'),
(5, 1515653250005, 1, 'repo1/fileA4', null, 'repo1'),
(6, 1515653250006, 1, 'repo1/fileA5', null, 'repo1'),
(7, 1515653250007, 1, 'repo2/fileh', null, 'repo2'),
(8, 1515653250008, 1, 'repo2/fileg', null, 'repo2'),
(9, 1515653250009, 1, 'repo2/filef', null, 'repo2'),
(10, 1515653250010, 1, 'repo2/filee', null, 'repo2'),
(11, 1515653250020, 1, 'repo2/filed', null, 'repo2'),
(12, 1515653250030, 1, 'repo2/filec', null, 'repo2'),
(13, 1515653250040, 1, 'repo2/fileb', null, 'repo2'),
(14, 1515653250050, 1, 'repo1/fileA6', null, 'repo1'),
(15, 1515653250060, 1, 'repo2/fileA61', null, 'repo2'),
(16, 1515653250070, 1, 'repo2/fileA62', null, 'repo2'),
(17, 1515653250080, 1, 'repo2/fileA63', null, 'repo2'),
(18, 1515653250080, 1, 'repo1-another/fileA63', null, 'repo1-another'),
(19, 1515653250080, 1, 'repo1-another/fileA63', null, 'repo1-another');

INSERT INTO node_event_cursor VALUES
('metadata-dispatcher', 1515653250000, 'METADATA_MIGRATION', NULL, NULL),
('replication-nj4-ams', 1515653250003, 'INCREMENTAL_REPLICATION_PROGRESS', NULL, NULL),
('repo-federated_replicationhttp://0.0.0.0:8081/artifactory/repo-cursor', 419, 'FEDERATED_REPLICATION', 'HEALTHY', 1639903154),
('repo1-federated_replicationhttp://0.0.0.0:8081/artifactory/repo1-cursor', 0, 'FEDERATED_REPLICATION', 'HEALTHY', 1639903154),
('repo1-another-federated_replicationhttp://0.0.0.0:8081/artifactory/repo1-another-cursor', 0, 'FEDERATED_REPLICATION', 'HEALTHY', 1639903154),
('repo2-federated_replicationhttp://0.0.0.0:8081/artifactory/repo2-cursor', 0, 'FEDERATED_REPLICATION', 'HEALTHY', 1639903154);
