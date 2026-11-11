CREATE TABLE repo_traffic_daily
(
    id                     BIGINT         NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),
    update_time            BIGINT         NOT NULL,
    action                 VARCHAR(16)    NOT NULL,
    actor                  VARCHAR(32)    NOT NULL,
    redirect_target        VARCHAR(16)    NOT NULL,
    origin_repo_key        VARCHAR(128)   NOT NULL,
    origin_repo_type       VARCHAR(16)    NOT NULL,
    origin_package_type    VARCHAR(64)    NOT NULL,
    repo_key               VARCHAR(128)   NOT NULL,
    repo_type              VARCHAR(16)    NOT NULL,
    package_type           VARCHAR(64)    NOT NULL,
    request_count          BIGINT         DEFAULT 0 NOT NULL,
    total_transferred_size BIGINT         DEFAULT 0 NOT NULL,
    CONSTRAINT repo_traffic_daily_pk PRIMARY KEY (id),
    CONSTRAINT repo_traffic_daily_uk UNIQUE (update_time, action, actor, redirect_target, origin_repo_key, repo_key)
);
