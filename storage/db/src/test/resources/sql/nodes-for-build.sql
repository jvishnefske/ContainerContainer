UPDATE unique_ids SET current_id = 10000 WHERE index_type = 'general';

INSERT INTO binaries VALUES
('acab88fc2a043c2479a6de676a2f8179e9ea2167', '902a360ecad98a34b59863c1e65bcf71', 3, 'bbbb23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60bbbb');

INSERT INTO nodes VALUES
(10, 1, 'repo1', 'path', 'node_test_name', 0, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 0, 'acab88fc2a043c2479a6de676a2f8179e9ea2167', NULL, NULL, NULL, NULL, '6e9ff9c21f4a26a8015b7b2e7e65dba3ca57799d');

INSERT INTO node_props VALUES
(1, 10, 'build.name', 'ba'),
(2, 10, 'build.number', '1');
