-- noinspection SqlDialectInspectionForFile

INSERT INTO distributed_locks VALUES
('lockCategory', 'path/to/file.jar', 'owner1', 513, 'thread-test1',  1284289504000),
('lockCategory', 'mykey', 'owner1', 513, 'thread-test1',  1284289504000),
('bagants', 'path/to/file2.exe', 'owner2', 513, 'thread-test1',  1284289504000),
('arti', 'path/to/file3.zip', 'owner5', 111, 'thread-bugger-8',  1284289204000),
('somemap', 'nugetAddPkg', 'froggy', 745, 'thread-async-1',  1284289204000),
('somemap', 'imtheindexerjob', 'froggy', 746, 'thread-async-0',  1284292504000),
('replications', 'my:key', 'froggy', 746, 'thread-async-0', 1284290404000),
('replications', 'my:expiredKey', 'froggy', 746, 'thread-async-5',  1284287400000),
('MorLockCategory', 'MorLockKey1', 'MorOwner1', 123, 'mor-thread-test1', 1284289504000),
('MorLockCategory', 'MorLockKey2', 'MorOwner1', 123, 'mor-thread-test2', 1284289504000),
('MorLockCategory', 'MorLockKey3', 'MorOwner2', 123, 'mor-thread-test3', 1284289504000),
('MorLockCategory', 'MorLockKey4', 'MorOwner3', 123, 'mor-thread-test4', 1284289504000),
('MorLockCategory', 'SingleKey123', 'MorOwner4', 123, 'mor-thread-test5', 1284289504000);
