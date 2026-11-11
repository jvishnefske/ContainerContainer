-- Test data for StaleBlobQueryDao integration tests.
-- Simulates Docker repo with tag directories containing manifests and sha256__ blobs.

UPDATE unique_ids SET current_id = 10000 WHERE index_type = 'general';

INSERT INTO binaries VALUES
('aa0000000000000000000000000000000000aa01', 'md5_01', 1024, 'sha256_01_64chars_padded_000000000000000000000000000000000001'),
('aa0000000000000000000000000000000000aa02', 'md5_02', 2048, 'sha256_02_64chars_padded_000000000000000000000000000000000002'),
('aa0000000000000000000000000000000000aa03', 'md5_03', 4096, 'sha256_03_64chars_padded_000000000000000000000000000000000003'),
('aa0000000000000000000000000000000000aa04', 'md5_04', 512,  'sha256_04_64chars_padded_000000000000000000000000000000000004'),
('aa0000000000000000000000000000000000aa05', 'md5_05', 8192, 'sha256_05_64chars_padded_000000000000000000000000000000000005'),
('aa0000000000000000000000000000000000aa06', 'md5_06', 256,  'sha256_06_64chars_padded_000000000000000000000000000000000006'),
('aa0000000000000000000000000000000000aa07', 'md5_07', 100,  'sha256_07_64chars_padded_000000000000000000000000000000000007'),
('aa0000000000000000000000000000000000aa08', 'md5_08', 300,  'sha256_08_64chars_padded_000000000000000000000000000000000008');

INSERT INTO nodes VALUES
-- docker-test repo root
(5000, 0, 'docker-test', '.', '.', 0, 1000, 'admin', 1000, 'admin', 1000, 0, NULL, NULL, NULL, NULL, NULL, 'rpck-5000'),

-- === Tag dir WITH manifest.json (library/nginx/latest) - blobs here are NOT stale ===
(5001, 0, 'docker-test', 'library/nginx', 'latest', 3, 1000, 'admin', 1000, 'admin', 1000, 0, NULL, NULL, NULL, NULL, NULL, 'rpck-5001'),
(5002, 1, 'docker-test', 'library/nginx/latest', 'manifest.json', 4, 1000, 'admin', 1000, 'admin', 1000, 1024, 'aa0000000000000000000000000000000000aa01', 'aa0000000000000000000000000000000000aa01', 'md5_01', 'md5_01', 'sha256_01_64chars_padded_000000000000000000000000000000000001', 'rpck-5002'),
(5003, 1, 'docker-test', 'library/nginx/latest', 'sha256__aaa111', 4, 1000, 'admin', 1000, 'admin', 1000, 2048, 'aa0000000000000000000000000000000000aa02', 'aa0000000000000000000000000000000000aa02', 'md5_02', 'md5_02', 'sha256_02_64chars_padded_000000000000000000000000000000000002', 'rpck-5003'),
(5004, 1, 'docker-test', 'library/nginx/latest', 'sha256__aaa222', 4, 1000, 'admin', 1000, 'admin', 1000, 4096, 'aa0000000000000000000000000000000000aa03', 'aa0000000000000000000000000000000000aa03', 'md5_03', 'md5_03', 'sha256_03_64chars_padded_000000000000000000000000000000000003', 'rpck-5004'),

-- === Tag dir WITHOUT manifest (library/nginx/v1) - blobs here ARE stale ===
(5010, 0, 'docker-test', 'library/nginx', 'v1', 3, 2000, 'admin', 2000, 'admin', 2000, 0, NULL, NULL, NULL, NULL, NULL, 'rpck-5010'),
(5011, 1, 'docker-test', 'library/nginx/v1', 'sha256__orphan1', 4, 2000, 'admin', 2000, 'admin', 2000, 512, 'aa0000000000000000000000000000000000aa04', 'aa0000000000000000000000000000000000aa04', 'md5_04', 'md5_04', 'sha256_04_64chars_padded_000000000000000000000000000000000004', 'rpck-5011'),
(5012, 1, 'docker-test', 'library/nginx/v1', 'sha256__orphan2', 4, 3000, 'admin', 3000, 'admin', 3000, 8192, 'aa0000000000000000000000000000000000aa05', 'aa0000000000000000000000000000000000aa05', 'md5_05', 'md5_05', 'sha256_05_64chars_padded_000000000000000000000000000000000005', 'rpck-5012'),

-- === Tag dir WITH list.manifest.json (library/redis/latest) - blobs NOT stale ===
(5020, 0, 'docker-test', 'library/redis', 'latest', 3, 1000, 'admin', 1000, 'admin', 1000, 0, NULL, NULL, NULL, NULL, NULL, 'rpck-5020'),
(5021, 1, 'docker-test', 'library/redis/latest', 'list.manifest.json', 4, 1000, 'admin', 1000, 'admin', 1000, 256, 'aa0000000000000000000000000000000000aa06', 'aa0000000000000000000000000000000000aa06', 'md5_06', 'md5_06', 'sha256_06_64chars_padded_000000000000000000000000000000000006', 'rpck-5021'),
(5022, 1, 'docker-test', 'library/redis/latest', 'sha256__redis1', 4, 1000, 'admin', 1000, 'admin', 1000, 100, 'aa0000000000000000000000000000000000aa07', 'aa0000000000000000000000000000000000aa07', 'md5_07', 'md5_07', 'sha256_07_64chars_padded_000000000000000000000000000000000007', 'rpck-5022'),

-- === _uploads staging dir - blobs excluded by path filter ===
(5030, 1, 'docker-test', 'library/nginx/_uploads', 'sha256__uploading', 4, 1000, 'admin', 1000, 'admin', 1000, 300, 'aa0000000000000000000000000000000000aa08', 'aa0000000000000000000000000000000000aa08', 'md5_08', 'md5_08', 'sha256_08_64chars_padded_000000000000000000000000000000000008', 'rpck-5030'),

-- === Tag dir WITHOUT manifest (library/app/old) - stale with old timestamp ===
(5040, 0, 'docker-test', 'library/app', 'old', 3, 500, 'admin', 500, 'admin', 500, 0, NULL, NULL, NULL, NULL, NULL, 'rpck-5040'),
(5041, 1, 'docker-test', 'library/app/old', 'sha256__oldblob', 4, 500, 'admin', 500, 'admin', 500, 1024, 'aa0000000000000000000000000000000000aa01', 'aa0000000000000000000000000000000000aa01', 'md5_01', 'md5_01', 'sha256_01_64chars_padded_000000000000000000000000000000000001', 'rpck-5041');
