CREATE TABLE remote_repo_stats_status
(
    id                      BIGSERIAL      NOT NULL,
    repository_key          VARCHAR(128)    NOT NULL,
    repo_package_type       VARCHAR(64)    NOT NULL,
    update_time             BIGINT         NOT NULL,
    aggregation_type        VARCHAR(16)    NOT NULL,
    status_code             SMALLINT       NOT NULL,
    request_count           BIGINT         DEFAULT 0 NOT NULL,
    http_method             VARCHAR(16)    NOT NULL,
    CONSTRAINT remote_repo_stats_status_pk PRIMARY KEY (id),
    CONSTRAINT remote_repo_stats_status_uk UNIQUE (repository_key, update_time, status_code, http_method, aggregation_type)
);
