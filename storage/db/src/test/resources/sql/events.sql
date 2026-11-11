INSERT INTO node_events_tmp (tmp_event_id, timestamp, event_type, path) VALUES
(1, 1515653241184, 1, 'repo1/'),
(2, 1515653241185, 1, 'repo1/ant/'),
(3, 1515653241185, 1, 'repo1/ant/1.2/'),
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