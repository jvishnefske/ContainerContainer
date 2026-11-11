CREATE TABLE file_list_tasks
(
    repo            VARCHAR(255)    NOT NULL,
    path            VARCHAR(255)    NOT NULL,
    heartbeat       NUMBER(19, 0),
    CONSTRAINT file_list_tasks_pk PRIMARY KEY (repo)
);