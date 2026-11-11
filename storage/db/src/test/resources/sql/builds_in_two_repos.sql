UPDATE unique_ids SET current_id = 10000 WHERE index_type = 'general';

INSERT INTO builds VALUES
(1, 'ba', '1', 1349000000000, NULL, 1350000000000, 'me', 1350000000001, 'not-me', 'artifactory-build-info', null),
(2, 'bb', '1', 1349001000000, 'http://myserver/jenkins/bb/1', 1350001000000, 'me', NULL, NULL, 'prjx-build-info', null),
(3, 'ba', '2', 1349002000000, NULL, 1350002000000, 'me', NULL, NULL, 'artifactory-build-info', null),
(4, 'bb', '2', 1349003000000, 'http://myserver/jenkins/bb/2', 1350003000000, 'me', NULL, NULL, 'prjx-build-info', null),
(5, 'ba', '3', 1349004000000, NULL, 1350004000000, 'me', NULL, NULL, 'artifactory-build-info', null),
(6, 'bb', '3', 1349004000000, 'http://myserver/jenkins/bb/3', 1350004000000, 'me', NULL, NULL, 'artifactory-build-info', null),
(7, 'cc', '1', 1349005000000, 'http://myserver/jenkins/cc/1', 1350005000000, 'me', NULL, NULL, 'artifactory-build-info', null),
(8, 'cc', '1', 1349006000000, 'http://myserver/jenkins/cc/1', 1350006000000, 'me', NULL, NULL, 'prjx-build-info', null),
(21, 'dd', '1', 1349007000000, NULL, 1349007500000, 'me', 1349007500001, 'not-me', 'artifactory-build-info', null),
(22, 'dd', '2', 1349008000000, NULL, 1349008500000, 'me', NULL, 'not-me', 'artifactory-build-info', null),
(23, 'dd', '3', 1349009000000, NULL, 1349009100000, 'me', NULL, 'not-me', 'artifactory-build-info', null),
(24, 'dd', '3', 1349010000000, NULL, 1349011000000, 'me', NULL, 'not-me', 'artifactory-build-info', null),
(25, 'dd', '4', 1349020000000, NULL, 1349021000000, 'me', NULL, 'not-me', 'artifactory-build-info', null),
(26, 'dd', '3', 1349030000000, NULL, 1349031000000, 'me', NULL, 'not-me', 'proj1-build-info', null),
(27, 'dd', '3', 1349040000000, NULL, 1349041000000, 'me', NULL, 'not-me', 'proj2-build-info', null),
(28, 'd_d', '1', 1349050000000, NULL, 1349051000000, 'me', NULL, 'not-me', 'proj2-build-info', null),
(29, 'ddd', '1', 1349060000000, NULL, 1349061000000, 'me', NULL, 'not-me', 'proj2-build-info', null),
(30, 'd_e', '1', 1349060000000, NULL, 1349061000000, 'me', NULL, 'not-me', 'proj2-build-info', null),
(31, 'd%', '1', 1349060000000, NULL, 1349061000000, 'me', NULL, 'not-me', 'proj2-build-info', null),
(32, 'test-same-sha1-build', '1', 1349100000000, NULL, 1349100000000, 'admin', NULL, NULL, 'artifactory-build-info', null);

INSERT INTO build_props VALUES
(1, 1, 'start', '0'),
(2, 1, 'status', 'bad'),
(3, 2, 'start', '1'),
(4, 2, 'status', 'not-too-bad'),
(5, 5, 'start', '4'),
(6, 5, 'status', 'good');

-- Inserting date in oposite order to check re-ordering
INSERT INTO build_promotions VALUES
                                 (3, 1350012000000, 'me', 'dead', NULL, 'bad stuff', NULL),
(5, 1350034000000, 'promoter', 'Released', 'public', 'Full release', 'rel'),
                                 (5, 1350024000000, NULL, 'staging', NULL, NULL, 'jenkins'),
                                 (4, 1350003000000, NULL, 'staging', NULL, NULL, NULL),
                                 (4, 1350023000000, 'tester', 'rollback', 'lost-local', 'Refused by QA', NULL),
                                 (4, 1350013000000, 'promoter', 'promoted', 'qa-local', 'sending to QA', 'me');

INSERT INTO build_modules VALUES
       (1, 1, 'ba:moda1', NULL),
       (2, 2, 'bb:modb1', NULL),
       (3, 2, 'bb:modb2', NULL),
       (4, 3, 'ba:moda1', NULL),
       (5, 7, 'cc:moda1', NULL),
       (6, 8, 'cc:moda1', NULL),
       (7, 1, 'b/1', 'build'),
       (8, 32, 'test-module', NULL);



INSERT INTO build_artifacts VALUES
       (2001, 1, 'ba1mod1-art1', 'dll', 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'a02a360ecad98a34b59863c1e65bcf71'),
       (2002, 2, 'bb1mod2-art1', 'dll', 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'a02a360ecad98a34b59863c1e65bcf71'),
       (2003, 3, 'bb1mod3-art1', 'dll', 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'a02a360ecad98a34b59863c1e65bcf71'),
       (2004, 4, 'ba2mod4-art1', 'dll', 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'a02a360ecad98a34b59863c1e65bcf71'),
       (2005, 1, 'ba1mod1-art2', 'dll', 'bcab88fc2a043c2479a6de676a2f8179e9ea2167', 'b02a360ecad98a34b59863c1e65bcf71'),
       (2006, 4, 'ba2mod4-art1', 'dll', 'bcab88fc2a043c2479a6de676a2f8179e9ea2167', 'b02a360ecad98a34b59863c1e65bcf71'),
       (2007, 5, 'cc2mod4-art1', 'dll', 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'b02a360ecad98a34b59863c1e65bcf71'),
       (2008, 6, 'cc2mod4-art1', 'dll', 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'b02a360ecad98a34b59863c1e65bcf71'),
       (2009, 8, 'lib-a-1.0.jar', 'jar', 'aaaaaa1111111122222222333333334444aaaa', 'b02a360ecad98a34b59863c1e65bcf72'),
       (2010, 8, 'lib-b-2.0.jar', 'jar', 'aaaaaa1111111122222222333333334444aaaa', 'b02a360ecad98a34b59863c1e65bcf72');


INSERT INTO build_dependencies VALUES
(2010, 1, 'bb1mod1-art1', 'compile', 'dll', 'ccab88fc2a043c2479a6de676a2f8179e9ea2167', 'c02a360ecad98a34b59863c1e65bcf71'),
(2011, 2, 'bb1mod2-art1', 'compile', 'dll', 'ccab88fc2a043c2479a6de676a2f8179e9ea2167', 'c02a360ecad98a34b59863c1e65bcf71'),
(2012, 3, 'ba1mod3-art1', 'compile', 'dll', 'ccab88fc2a043c2479a6de676a2f8179e9ea2167', 'c02a360ecad98a34b59863c1e65bcf71'),
(2014, 3, 'bb1mod3-art1', 'compile', 'dll', 'dcab88fc2a043c2479a6de676a2f8179e9ea2167', 'd02a360ecad98a34b59863c1e65bcf71'),
(2013, 4, 'ba2mod4-art1', 'compile', 'dll', 'dcab88fc2a043c2479a6de676a2f8179e9ea2167', 'd02a360ecad98a34b59863c1e65bcf71'),
(2015, 5, 'cc2mod4-art1', 'compile', 'dll', 'dcab88fc2a043c2479a6de676a2f8179e9ea2167', 'd02a360ecad98a34b59863c1e65bcf71'),
(2016, 6, 'cc2mod4-art1', 'compile', 'dll', 'dcab88fc2a043c2479a6de676a2f8179e9ea2167', 'd02a360ecad98a34b59863c1e65bcf71');


INSERT INTO binaries VALUES
('acab88fc2a043c2479a6de676a2f8179e9ea2167', 'c02a360ecad98a34b59863c1e65bcf71', 308, 'a43e443bbc643340296e4ffb8c52bb1ea870a349cdd1e9aa23493e8cb5274687'),
('aaaaaa1111111122222222333333334444aaaa', 'aaabbbb1111222233334444', 1024, 'bbbbbb2222222233333333444444445555bbbb');

INSERT INTO nodes VALUES
(1, 1, 'npm-local', 'test_pack1/-', 'test_pack-1.0.0.tgz', 3, 1612269225838, 'admin', 1612269225791, 'admin', 1612269225839, 308, 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'c02a360ecad98a34b59863c1e65bcf71', 'c02a360ecad98a34b59863c1e65bcf71', 'a43e443bbc643340296e4ffb8c52bb1ea870a349cdd1e9aa23493e8cb5274687', '626398ebee3e668a2d017343817d69ccc3dc9958'),
(2, 1, 'npm-local', 'test_pack2/-', 'test_pack-1.0.0.tgz', 3, 1612269225838, 'admin', 1612269225791, 'admin', 1612269225839, 308, 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'c02a360ecad98a34b59863c1e65bcf71', 'c02a360ecad98a34b59863c1e65bcf71', 'a43e443bbc643340296e4ffb8c52bb1ea870a349cdd1e9aa23493e8cb5274687', '626398ebee3e668a2d017343817d69ccc3dc9957'),
(3, 1, 'npm-local', 'test_pack3/-', 'test_pack-1.0.0.tgz', 3, 1612269225838, 'admin', 1612269225791, 'admin', 1612269225839, 308, 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'c02a360ecad98a34b59863c1e65bcf71', 'c02a360ecad98a34b59863c1e65bcf71', 'a43e443bbc643340296e4ffb8c52bb1ea870a349cdd1e9aa23493e8cb5274687', '626398ebee3e668a2d017343817d69ccc3dc9956'),
(4, 1, 'npm-local', 'test_pack4/-', 'test_pack-1.0.0.tgz', 3, 1612269225838, 'admin', 1612269225791, 'admin', 1612269225839, 308, 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'c02a360ecad98a34b59863c1e65bcf71', 'c02a360ecad98a34b59863c1e65bcf71', 'a43e443bbc643340296e4ffb8c52bb1ea870a349cdd1e9aa23493e8cb5274687', '626398ebee3e668a2d017343817d69ccc3dc9955'),
(5, 1, 'npm-local', 'test_pack5/-', 'test_pack-1.0.0.tgz', 3, 1612269225838, 'admin', 1612269225791, 'admin', 1612269225839, 308, 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'c02a360ecad98a34b59863c1e65bcf71', 'c02a360ecad98a34b59863c1e65bcf71', 'a43e443bbc643340296e4ffb8c52bb1ea870a349cdd1e9aa23493e8cb5274687', '626398ebee3e668a2d017343817d69ccc3dc9954'),
(6, 1, 'maven-local', 'org/example/lib-a/1.0', 'lib-a-1.0.jar', 3, 1349000000000, 'admin', 1349000000000, 'admin', 1349000000000, 1024, 'aaaaaa1111111122222222333333334444aaaa', 'aaaaaa1111111122222222333333334444aaaa', 'aaabbbb1111222233334444', 'aaabbbb1111222233334444', NULL, '0'),
(7, 1, 'maven-local', 'org/example/lib-b/2.0', 'lib-b-2.0.jar', 3, 1349000000000, 'admin', 1349000000000, 'admin', 1349000000000, 1024, 'aaaaaa1111111122222222333333334444aaaa', 'aaaaaa1111111122222222333333334444aaaa', 'aaabbbb1111222233334444', 'aaabbbb1111222233334444', NULL, '0');


INSERT INTO node_props VALUES
(1, 1, 'build.name', 'ba'),
(2, 1, 'build.number', '1'),
(3, 1, 'build.repo', 'artifactory-build-info'),
(4, 1, 'build.timestamp', '1349000000000'),
(5, 2, 'build.name', 'bb'),
(6, 2, 'build.number', '1'),
(7, 2, 'build.repo', 'prjx-build-info'),
(8, 3, 'build.name', 'bb'),
(9, 3, 'build.number', '1'),
(10, 3, 'build.repo', 'prjx-build-info'),
(11, 4, 'build.name', 'cc'),
(12, 4, 'build.number', '1'),
(13, 4, 'build.timestamp', '1349006000000'),
(14, 4, 'build.repo', 'prjx-build-info'),
(15, 5, 'build.name', 'cc'),
(16, 5, 'build.number', '1'),
(17, 5, 'build.timestamp', '1349005000000'),
(18, 6, 'build.name', 'test-same-sha1-build'),
(19, 6, 'build.number', '1'),
(20, 7, 'build.name', 'test-same-sha1-build'),
(21, 7, 'build.number', '1');

INSERT INTO build_release_bundles VALUES
(1, 1, 'release-bundles-v2', 'rb1', '1.0.0'),
(2, 1, 'release-bundles-v2', 'rb1', '2.0.0'),
(3, 1, 'release-bundles-v2', 'rb2', '1.0.0'),
(4, 5, 'release-bundles-v2', 'rb1', '2.0.0'),
(5, 1, 'release-bundles-v2', 'rb1', '3.0.0'),
(6, 1, 'proj-release-bundles-v2', 'rb1', '1.0.0'),
(7, 22, 'proj-release-bundles-v2', 'rb2', '1.0.0'),
(8, 22, 'proj-release-bundles-v2', 'rb1', '2.0.0'),
(9, 23, 'proj-release-bundles-v2', 'rb2', '3.0.0'),
(10, 22, 'release-bundles-v2', 'rb2', '1.0.0'),
(11, 23, 'release-bundles-v2', 'rb2', '3.0.0'),
(12, 25, 'release-bundles-v2', 'rb2', '1.0.0'),
(13, 25, 'proj-release-bundles-v2', 'rb2', '2.0.0'),
(14, 22, 'release-bundles-v2', 'rb2', '3.0.0'),
(15, 24, 'release-bundles-v2', 'rb2', '1.0.0'),
(16, 25, 'release-bundles-v2', 'rb2', '3.0.0'),
(17, 25, 'release-bundles-v2', 'rb2', '2.0.0');
