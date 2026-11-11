UPDATE unique_ids SET current_id = 10000 WHERE index_type = 'general';

INSERT INTO binaries VALUES
('dcab88fc2a043c2479a6de676a2f8179e9ea2167', '902a360ecad98a34b59863c1e65bcf71', 3, 'bbbb23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60bbbb'),
('acab88fc2a043c2479a6de676a2f8179e9ea2167', '302a360ecad98a34b59863c1e65bcf71', 78, 'dddd23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60dddd'),
('bbbb88fc2a043c2479a6de676a2f8179e9eabbbb', '402a360ecad98a34b59863c1e65bcf71', 33, 'yyyy23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy'),
('dddd88fc2a043c2479a6de676a2f8179e9eadddd', '502a360ecad98a34b59863c1e65bcf71', 333, 'dcabf055bc6d5477c35f82da16323efb884fc21a87fbf7ebda9d5848eee3e280'),
('dddd88fc2a043c2479a6de676a2f7179e9eaddac', '502a360ecad98a34b59863c1e6accf71', 500, 'acdc23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60febd'),
('dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', 666, 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy');

INSERT INTO nodes VALUES
(1, 0, 'repo1', '.', '.', 0, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, '6e9ff9c21f4a26a8015b7b2e7e65dba3ca57799d'),
(2, 0, 'repo1', '.', 'ant', 1, 1340283204448, 'yossis-1', 1340283205448,'yossis-2', 1340283205448, 0, NULL, NULL, NULL, NULL, NULL, '065521cf694a448724f1d419298008fa49cb209e'),
(3, 0, 'repo1', 'ant', 'ant', 2, 1340283204450, 'yossis-1', 1340283204450,'yossis-3', 1340283214450, 0, NULL, NULL, NULL, NULL, NULL, 'b9bc91f24b0dd032f4a1a87c4fd3c5313f59e9de'),
(4, 0, 'repo1', 'ant/ant', '1.5', 3, 1340283204448, 'yossis-9614', 1340283204448,'yossis-5612', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, '49ce3734ff35859d2a42ef64c7daea0799ab7304'),
(5, 1, 'repo1', 'ant/ant/1.5', 'ant-1.5.jar', 4, 1340283204448, 'yossis-2201', 1340283204448,'yossis-3274', 1340283204448, 716139, 'dcab88fc2a043c2479a6de676a2f8179e9ea2167', 'dcab88fc2a043c2479a6de676a2f8179e9ea2167', '902a360ecad98a34b59863c1e65bcf71', '902a360ecad98a34b59863c1e65bcf71', 'bbbb23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60bbbb', 'a128d2078db78d8ef4a7050749d09bd61bb72ddb'),
(6, 0, 'repo1', '.', 'ant-launcher', 1, 1340223204457, 'yossis-2', 1340283204448,'yossis-2', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, '75276a24c2a5a15aa94d577d8247d26418ed17b6'),
(7, 0, 'repo1', 'ant-launcher', 'ant-launcher', 2, 1340223204457, 'yossis-2', 1340283204448,'yossis-2', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, 'ce48c03ec67c4ad910baa0848fd745c20c458dfd'),
(8, 0, 'repo1', '.', 'org', 1, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, 'f3b6e167caaca1274bd745ca3d1b925f01191305'),
(9, 0, 'repo1', 'org', 'yossis', 2, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, '0b4e4280b7031f3eacb85a0444085fe9a442abb0'),
(10, 0, 'repo1', 'org/yossis', 'tools', 3, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, '1a6751f4f29f59d6903f47a24890889cba9bbe48'),
(11, 1, 'repo1', 'org/yossis/tools', 'test.bin', 4, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 43434, 'acab88fc2a043c2479a6de676a2f8179e9ea2167', 'acab88fc2a043c2479a6de676a2f8179e9ea2167', '302a360ecad98a34b59863c1e65bcf71', '302a360ecad98a34b59863c1e65bcf71', 'dddd23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60dddd', 'repo-path-name-key-11'),
(12, 1, 'repo1', 'org/yossis/tools', 'file2.pom', 4, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 43434, 'bbbb88fc2a043c2479a6de676a2f8179e9eabbbb', 'bcab88fc2a043c2479a6de676a2f8179e9ea2167', '402a360ecad98a34b59863c1e65bcf71', '402a360ecad98a34b59863c1e65bcf71',NULL,'repo-path-name-key-12'),
(13, 1, 'repo1', 'org/yossis/tools', 'file3.pom', 4, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 43434, 'dddd88fc2a043c2479a6de676a2f8179e9eadddd', 'ccab88fc2a043c2479a6de676a2f8179e9ea2167', '502a360ecad98a34b59863c1e65bcf71', '502a360ecad98a34b59863c1e65bcf71',NULL,'repo-path-name-key-13'),
(14, 0, 'repo1', 'org/yossis', 'empty', 3, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, 'repo-path-name-key-14'),
(15, 1, 'repo-copy', 'org/yossis/tools', 'file3.bin', 4, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 43434, 'dddd88fc2a043c2479a6de676a2f8179e9eadddd', 'ccab88fc2a043c2479a6de676a2f8179e9ea2167', '502a360ecad98a34b59863c1e65bcf71', '502a360ecad98a34b59863c1e65bcf71', NULL, 'repo-path-name-key-15'),
(16, 1, 'repo-copy', 'org/shayy/trustme', 'trustme.jar', 4, 1340283204447, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 43434, 'dddd88fc2a043c2479a6de676a2f7179e9eaddac', 'NO_ORIG', '502a360ecad98a34b59863c1e6accf71', 'NO_ORIG', 'acdc23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60febd', 'repo-path-name-key-16'),
(17, 1, 'repo-copy', 'org/shayy/badmd5', 'badmd5.jar', 4, 1340283204447, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 43434, 'dddd88fc2a043c2479a6de676a2f7179e9eaddac', 'NO_ORIG', '502a360ecad98a34b59863c1e6accf71', '502a360ecad98a34b59863c1e65bcf32', 'acdc23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60febd', 'repo-path-name-key-17'),
(18, 0, 'repo2', '.', '.', 0, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, 'repo-path-name-key-18'),
(19, 0, 'repo2', '.', 'a', 1, 1340283204448, 'yossis-1', 1340283205448,'yossis-2', 1340283205448, 0, NULL, NULL, NULL, NULL, NULL, 'repo-path-name-key-19'),
(20, 0, 'repo2', 'a', 'b', 2, 1340283204450, 'yossis-1', 1340283204450,'yossis-3', 1340283214450, 0, NULL, NULL, NULL, NULL, NULL, 'repo-path-name-key-20'),
(21, 1, 'repo2', 'a', 'ant-1.5.jar', 4, 1340283204448, 'yossis-2201', 1340283204448,'yossis-3274', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-21'),
(22, 1, 'repo2', 'a/b', 'ant-1.5.jar', 4, 1340283204448, 'yossis-2201', 1340283204448,'yossis-3274', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-22'),
(23, 0, 'repo2', '.', 'aa', 1, 1340283204448, 'yossis-1', 1340283205448,'yossis-2', 1340283205448, 0, NULL, NULL, NULL, NULL, NULL, 'repo-path-name-key-23'),
(24, 0, 'repo2', 'aa', 'b', 2, 1340283204450, 'yossis-1', 1340283204450,'yossis-3', 1340283214450, 0, NULL, NULL, NULL, NULL, NULL, 'repo-path-name-key-24'),
(25, 1, 'repo2', 'aa', 'ant-1.5.jar', 2, 1340283204448, 'yossis-2201', 1340283204448,'yossis-3274', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-25'),
(26, 1, 'repo2', 'aa/b', 'ant-1.5.jar', 3, 1340283204448, 'yossis-2201', 1340283204448,'yossis-3274', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-26'),
(27, 0, 'repo3', '.', '.', 0, 1340283204448, 'admin', 1340283204448,'yossis-3274', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-27'),
(28, 0, 'repo3', '.', 'a', 1, 1340283204448, 'admin', 1340283204448,'yossis-3274', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-28'),
(29, 0, 'repo3', 'a', 'b', 2, 1340283204448, 'admin', 1340283204448,'yossis-3274', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-30'),
(30, 0, 'repo3', 'a/b', 'c', 3, 1340283204448, 'admin', 1340283204448,'yossis-3274', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-31'),
(31, 1, 'repo3', 'a/b/c', 'g.txt', 4, 1340283204448, 'admin', 1340283204448,'yossis-3274', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-32'),
(32, 1, 'repo3', 'a/B/C', 'f.txt', 4, 1340283204448, 'admin', 1340283204448,'yossis-3274', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-33'),
(33, 0, 'repo3', 'a/B', 'C', 4, 1340283204448, 'admin', 1340283204448,'yossis-3274', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-34'),
(34, 1, 'repo3', 'B', 'test.txt', 2, 1340283204448, 'admin', 1340283204448,'yossis-3274', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-35'),
(35, 0, 'repo1', '.', 'a_1.2', 1, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, 'repo-path-name-key-36'),
(36, 0, 'repo1', '.', 'ab1.2', 1, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, 'repo-path-name-key-37'),
(37, 0, 'repo1', 'ab1.2', 'tt.txt', 2, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-38'),
(38, 1, 'auto-trashcan', 'repo1/a', 'file1.txt', 2, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-38real'),
(39, 1, 'auto-trashcan', 'repo1', 'fileroot.txt', 3, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-39'),
(40, 1, 'auto-trashcan', 'repo2/aa', 'aafile1.txt', 3, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-40'),
(41, 1, 'auto-trashcan', 'repo2/a/b/c', 'abcfile1.txt', 5, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-41'),
(42, 1, 'auto-trashcan', 'repo2', 'something.txt', 2, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-42'),
(43, 1, 'auto-trashcan', 'repo2', 'ably.txt', 2, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-43'),
(44, 1, 'auto-trashcan', 'repo2', 'neverreturn.txt', 2, 1340283204448, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', NULL, 'repo-path-name-key-44'),
(45, 0, 'repo1', 'ab1.3', 'z.txt', 2, 1340283204448, 'elis-1', 1340283204448, 'elis-1', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-45'),
(46, 0, 'repo1', 'ab1.4', 'x.txt', 2, 1340283204448, 'elis-2', 1340283204448, 'elis-2', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-46'),
(10001, 0, 'repo-foo', 'ab1.2', 'tt.txt', 2, 2340283204449, 'yossis-1', 1340283204448, 'yossis-1', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-10001'),
(47, 1, 'repo4', 'a', 'manifest.json', 2, 1340283204448, 'hussein-2', 1340283204448, 'hussein-2', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-47'),
(48, 0, 'repo4', 'b', 'json.json', 2, 1340283204448, 'hussein-2', 1340283204448, 'hussein-2', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-48'),
(49, 0, 'emptyRepo', '.', '.', 0, 1340283204448, 'morm', 1340283204448, 'morm', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-49'),
(50, 0, 'nonEmptyRepo', '.', '.', 0, 1340283204448, 'morm', 1340283204448, 'morm', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-50'),
(51, 1, 'nonEmptyRepo', '.', 'node.txt', 1, 1340283204448, 'morm', 1340283204448, 'morm', 1340283204448, 716139, 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', 'dddd89fc2a043c2479a6de676a2f7179e9eaddac', '503a360ecad98a34b59863c1e6accf71', '503a360ecad98a34b59863c1e6accf71', 'acab23029162f3b2dc51f512cb64bce8cb6913ed6e540f23ec567d898f60yyyy', 'repo-path-name-key-51'),
(1040, 0, 'auto-trashcan', 'repo2', 'dirA', 1, 1340283204448, 'evg', 1340283204448, 'evg', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, 'repo-path-name-key-1040'),
(1041, 0, 'auto-trashcan', 'repo2', 'dirB', 1, 1340283204448, 'evg', 1340283204448, 'evg', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, 'repo-path-name-key-1041'),
(1042, 0, 'auto-trashcan', 'repo2', 'dirC', 1, 1340283204448, 'evg', 1340283204448, 'evg', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, 'repo-path-name-key-1042'),
(1043, 0, 'auto-trashcan', 'repo2', 'dirD', 1, 1340283204448, 'evg', 1340283204448, 'evg', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, 'repo-path-name-key-1043'),
(1044, 0, 'auto-trashcan', 'repo2', 'dirE', 1, 1340283204448, 'evg', 1340283204448, 'evg', 1340283204448, 0, NULL, NULL, NULL, NULL, NULL, 'repo-path-name-key-1044');

INSERT INTO node_props VALUES
(1, 5, 'build.name', 'ant'),
(2, 5, 'build.number', '67'),
(3, 9, 'yossis', 'value1'),
(4, 9, 'trash.time', '1'),
(5, 9, 'trance', 'me'),
(6, 14, 'empty.val', ''),
(7, 14, 'null.val', NULL),
(8, 15, 'longProp', 'valueIsSmallerThanFortyCharacters'),
(9, 15, 'longProp', 'thisPropertyValueIsLongerThanFortyCharacters'),
(10, 16, 'longProp2', 'againPropertyValueWhichIsLongerThanFortyCharacters'),
(11, 16, 'differentLongKey', 'anotherPropertyValueWhichIsLongerThanFortyCharacters'),
(12, 17, 'smallProp', 'small'),
(13, 18, 'notTooLong', 'notTooLongValue'),
(14, 19, 'needToBeTrimmed', 'thisPropertyValueIsIsNeedsToBeTrimmedAsItIsBiggerThan50Chars'),
(15, 20, 'needToBeTrimmed2', 'thisPropertyValueIsIsAlsoNeedsToBeTrimmedAsItIsBiggerThan50Chars'),
(16, 22, 'trash.time', '1552838807'),
(17, 24, 'trash.time', '1552838807'),
(18, 33, 'trash.time', '1552838807'),
(19, 13, 'trash.time', '1552838807'),
(20, 29, 'trash.time', '1552838807'),
(21, 35, 'trash.time', '1552838807'),
(22, 38, 'trash.time', '1552838807'),
(23, 39, 'trash.time', '1552838807'),
(24, 40, 'trash.time', '1552838800'),
(25, 41, 'trash.time', '1552838800'),
(26, 42, 'trash.time', '1552838801'),
(27, 43, 'trash.time', '1552838599'),
(28, 44, 'trash.time', '1552839901'),
(140, 1040, 'trash.time', '1552839901'),
(141, 1041, 'trash.time', '1552839901'),
(142, 1042, 'trash.time', '1552839901'),
(143, 1043, 'trash.time', '1552839901'),
(144, 1044, 'trash.time', '1552839901');

INSERT INTO node_meta_infos VALUES
(5, 1340286103555, 'yossis','01234567890123456789012345678901'),
(9, 1340286803666, 'yoyo','01234567890123456789012345678901');

INSERT INTO watches VALUES
(1, 4, 'scott', 1340286203555),
(2, 4, 'amy', 1340286203666),
(3, 5, 'scott', 1340286203555),
(4, 9, 'yossis', 1340286203432),
(5, 9, 'ariel', 1340286203433),
(6, 10, 'dodo', 1340286203433),
(7, 10, 'momo', 1340286203433),
(8, 11, 'momo', 1340286203433);

INSERT INTO stats VALUES
(6, 15, 1340283207850, 'yossis');

INSERT INTO indexed_archives VALUES
('dcab88fc2a043c2479a6de676a2f8179e9ea2167', 6000),
('bbbb88fc2a043c2479a6de676a2f8179e9eabbbb', 6001);

INSERT INTO archive_paths VALUES
(8001, 'META-INF'),
(8002, 'org/apache/tools/ant/filters'),
(8003, 'org/apache/tools/mail'),
(8004, '.'),
(8005, 'another');

INSERT INTO archive_names VALUES
(9001, 'LICENSE.txt'),
(9002, 'MANIFEST.MF'),
(9003, 'BaseFilterReader.class'),
(9004, 'BaseParamFilterReader.class'),
(9005, 'MailMessage.class'),
(9006, 'Test'),
(9007, 'test.me');

INSERT INTO indexed_archives_entries VALUES
(6000, 8001, 9001),
(6000, 8001, 9002),
(6000, 8002, 9003),
(6000, 8002, 9004),
(6000, 8003, 9005),
(6001, 8004, 9006),
(6001, 8005, 9007);

INSERT INTO tasks (task_type, task_context, created) VALUES
('INDEX', 'repo1:ant/ant/1.5/ant-1.5.jar', 1340286208888),
('INDEX', 'reponone:test', 1340286207777),
('MMC', 'this/is/a/test', 1340286209999),
('X_TYPE', 'some::task::with::context', 1340299999999),
('DEBIAN_INDEX', 'trusty/main/i386::ADD::path/to/file47', 1578315167000),
('DEBIAN_INDEX', 'trusty/main/i386::ADD::path/to/file42', 1578315162000),
('DEBIAN_INDEX', 'trusty/main/i386::ADD::path/to/file45', 1578315165000),
('DEBIAN_INDEX', 'trusty/main/i386::ADD::path/to/file41', 1578315161000),
('DEBIAN_INDEX', 'trusty/other/i386::ADD::path/to/file45', 1578315165000);


INSERT INTO jobs VALUES
(1, 'REPLICATION', 'RUNNING', 1418380780000, NULL, 'my-json-blob'),
(2, 'REPLICATION', 'FINISHED', 1418380600000, 1418380720000, 'my-json-blob'),
(3, 'REPLICATION', 'STOPPED', 1418380610000, 1418380780000, NULL),
(4, 'OTHER', 'RUNNING', 1418380780000, 1418381140000, 'my-json-blob'),
(5, 'REPLICATION', 'FINISHED', 1355308500000, 1355308560000, 'old'),
(6, 'REPLICATION', 'FINISHED', 1355308620000, 1355308680000, 'new'),
(7, 'TYPE_TO_DELETE', 'FINISHED', 50, 8355308680000, 'deleteMe'),
(8, 'TYPE_TO_DELETE', 'FINISHED', 30, 8355308680000, 'deleteMe2'),
(9, 'TYPE_TO_DELETE', 'FINISHED', 10, 8355308680000, 'deleteME3');

INSERT INTO node_events_tmp (tmp_event_id, timestamp, event_type, path) VALUES
  (1, 1515653241184, 1, 'repo1/'),
  (2, 1515653241185, 1, 'repo1/ant/ant/'),
  (3, 1515653241185, 1, 'repo1/ant/'),
  (4, 1515653241185, 1, 'repo1/ant/ant/'),
  (5, 1515653241186, 1, 'repo1/ant/ant/1.5/'),
  (6, 1515653241186, 1, 'repo1/ant/ant/1.5/ant-1.5.jar'),
  (7, 1515653241196, 1, 'repo1/ant/ant/1.5/ant-1.5.pom'),
  (8, 1515653242123, 2, 'repo1/ant/'),
  (9, 1515653242223, 2, 'repo1/ant/ant/1.5/ant-1.5.jar'),
  (10, 1515653241289, 3, 'repo1/ant/ant/1.5/ant-1.5.pom'),
  (11, 1515653241293, 4, 'repo1/ant/ant/1.5/ant-1.5.jar'),

  (12, 1515653250000, 1, 'repo2/'),
  (13, 1515653250001, 1, 'repo2/org/'),
  (14, 1515653250002, 1, 'repo2/org/yossis/'),
  (15, 1515653250003, 1, 'repo2/org/yossis/tools/'),
  (16, 1515653250004, 1, 'repo2/org/yossis/tools/test.bin'),
  (17, 1515653250004, 4, 'repo2/org/yossis/tools/test.bin'),

  (18, 1515653250000, 3, 'repo-no-create/');

INSERT INTO node_event_cursor VALUES
  ('metadata-dispatcher', 1515653250000, 'METADATA_MIGRATION', NULL, NULL),
  ('replication-nj4-ams', 1515653250003, 'INCREMENTAL_REPLICATION_PROGRESS', NULL, NULL),
  ('repo-federated_replicationhttp://0.0.0.0:8081/artifactory/repo-cursor',	419, 'FEDERATED_REPLICATION', 'HEALTHY', 1639903154);

INSERT INTO node_event_priorities VALUES
  (123456, 'a/b', 'create', 'metadata-dispatcher', 2, 1515653250003, 1),
  (123457, 'a/b/c/d', 'props', 'metadata-dispatcher', 2, 1515653250004, 5),
  (123458, 'a/b/c/d', 'update', 'metadata-dispatcher', 3, 1515653250004, 0),
  (123459, 'a/b/c/d', 'delete', 'metadata-dispatcher', 3, 1515653250005, 0);
