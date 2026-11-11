CREATE TABLE binaries_tasks
(
    id              NUMBER(19, 0)      NOT NULL,
    sha1            VARCHAR(40)        NOT NULL,
    time            NUMBER(19, 0)      NOT NULL,
    local_repo_key  VARCHAR(64)        NOT NULL,
    remote_url_hash INT,
    start_time      NUMBER(19, 0),
    count           INT                NOT NULL,
    server_id       VARCHAR(128),
    CONSTRAINT binaries_tasks_pk PRIMARY KEY (id)
);