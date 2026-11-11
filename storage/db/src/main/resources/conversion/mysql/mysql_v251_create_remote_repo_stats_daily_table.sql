CREATE TABLE remote_repo_stats_daily
(
    id                      BIGINT         AUTO_INCREMENT NOT NULL,
    repository_key          VARCHAR(128)    NOT NULL,
    repo_package_type       VARCHAR(64)    NOT NULL,
    update_time             BIGINT         NOT NULL,
    total_request_count     BIGINT         DEFAULT 0 NOT NULL,
    total_response_time_ms    BIGINT         DEFAULT 0 NOT NULL,
    max_response_time_ms    BIGINT         DEFAULT 0 NOT NULL,
    ttfb_avg                BIGINT         DEFAULT 0 NOT NULL,
    total_response_size     BIGINT         DEFAULT 0 NOT NULL,
    status_2xx_count        BIGINT         DEFAULT 0 NOT NULL,
    status_3xx_count        BIGINT         DEFAULT 0 NOT NULL,
    status_4xx_count        BIGINT         DEFAULT 0 NOT NULL,
    status_5xx_count        BIGINT         DEFAULT 0 NOT NULL,
    http_method             VARCHAR(16)    NOT NULL,
    transfer_rate           BIGINT         DEFAULT 0 NOT NULL,
    CONSTRAINT remote_repo_stats_daily_pk PRIMARY KEY (id),
    CONSTRAINT remote_repo_stats_daily_uk UNIQUE (repository_key, update_time, http_method)
);
