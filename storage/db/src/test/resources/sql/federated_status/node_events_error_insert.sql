INSERT INTO node_events (event_id, timestamp, event_type, path, hints, repo) VALUES
(1, 1658735274, 1, 'repo/t.txt', null, 'repo');

INSERT INTO node_events_errors (error_id, operation_key, event_category, first_error_time, last_error_time, error_count, error_message, event_id, event_time, event_type, path) VALUES
(1, 'repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/repo-cursor', '1', 1658735274, 16587352745, 2, 'mock error', 1, 16587352745, 2, 'dfd'),
(2, 'repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/repo-cursor', '1', 1658735277, 16587352745, 2, 'mock error', 1, 16587352745, 2, 'dfd'),
(3, 'repo-federated_replicationhttp://docker.for.mac.localhost:10103/artifactory/repo-cursor', '1', 1658735277, 16587352745, 2, 'mock error', 1, 16587352745, 2, 'dfd');