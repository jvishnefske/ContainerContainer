INSERT INTO builds VALUES
(1, 'build-without-promotions', '1', 1, NULL, 2, 'me', 3, 'not-me', 'artifactory-build-info', null),
(2, 'build-with-promotions', '1', 1, NULL, 2, 'me', 3, 'not-me', 'artifactory-build-info', null),
(3, 'build-without-promotions-with-project', '1', 1, NULL, 2, 'me', 3, 'not-me', 'project-build-info', null);

INSERT INTO build_promotions VALUES
(2, 1, 'me', 'status', 'repo-02', 'comment', 'me');

INSERT INTO build_modules VALUES
(10, 1, 'module-1', NULL),
(11, 2, 'module-2', NULL),
(12, 3, 'module-3', NULL);

INSERT INTO build_artifacts VALUES
(100, 10, 'artifact-1', 'dll', 'sha1-01', 'md5-01'),
(101, 11, 'artifact-1', 'dll', 'sha1-01', 'md5-01'),
(102, 12, 'artifact-1', 'dll', 'sha1-01', 'md5-01');

INSERT INTO build_dependencies VALUES
(200, 10, 'dependency-1', 'compile', 'dll', 'sha1-01', 'md5-01'),
(201, 11, 'dependency-1', 'compile', 'dll', 'sha1-01', 'md5-01'),
(202, 12, 'dependency-1', 'compile', 'dll', 'sha1-01', 'md5-01');

INSERT INTO binaries VALUES
('sha1-01', 'md5-01', 308, 'sha256-01');

INSERT INTO nodes VALUES
(1000, 1, 'repo-01', 'test_pack1/-', 'test_pack-1.0.0.tgz', 3, 1612269225838, 'admin', 1612269225791, 'admin', 1612269225839, 308, 'sha1-01', 'sha1-01', 'md5-01', 'md5-01', 'sha256-01', 'check-sum-01'),
(1001, 1, 'repo-02', 'test_pack1/-', 'test_pack-1.0.0.tgz', 3, 1612269225838, 'admin', 1612269225791, 'admin', 1612269225839, 308, 'sha1-01', 'sha1-01', 'md5-01', 'md5-01', 'sha256-01', 'check-sum-02'),
(1002, 1, 'project-repo', 'test_pack1/-', 'test_pack-1.0.0.tgz', 3, 1612269225838, 'admin', 1612269225791, 'admin', 1612269225839, 308, 'sha1-01', 'sha1-01', 'md5-01', 'md5-01', 'sha256-01', 'check-sum-02');

INSERT INTO node_props VALUES
(10000, 1000, 'build.name', 'build-without-promotions'),
(10001, 1000, 'build.number', '1'),
(10002, 1000, 'build.timestamp', '1'),
(10003, 1001, 'build.name', 'build-with-promotions'),
(10004, 1001, 'build.number', '1'),
(10005, 1001, 'build.timestamp', '1'),
(10006, 1002, 'build.name', 'build-without-promotions-with-project'),
(10007, 1002, 'build.number', '1'),
(10008, 1002, 'build.timestamp', '1');
