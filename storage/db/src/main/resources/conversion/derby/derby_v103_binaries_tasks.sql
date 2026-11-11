CREATE TABLE binaries_tasks
(
    id              BIGINT      NOT NULL,
    sha1            VARCHAR(40) NOT NULL,
    time            BIGINT      NOT NULL,
    local_repo_key  VARCHAR(64) NOT NULL,
    remote_url_hash BIGINT,
    start_time      BIGINT,
    count           SMALLINT    NOT NULL,
    server_id       VARCHAR(128),
    CONSTRAINT binaries_tasks_pk PRIMARY KEY (id)
);