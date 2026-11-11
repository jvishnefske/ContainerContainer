CREATE TABLE request_logs (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    request_time          BIGINT        NOT NULL,
    trace_id              VARCHAR(64),
    username              VARCHAR(256)  NOT NULL,
    http_method           VARCHAR(10)   NOT NULL,
    request_path          VARCHAR(2048) NOT NULL,
    status_code           INT           NOT NULL,
    duration_ms           BIGINT        NOT NULL,
    CONSTRAINT request_logs_pk PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

CREATE INDEX request_logs_request_time_idx ON request_logs (request_time);
CREATE INDEX req_logs_username_time_idx ON request_logs (username, request_time);
CREATE INDEX request_logs_trace_idx ON request_logs (trace_id);
CREATE FULLTEXT INDEX request_logs_search_ft_idx
    ON request_logs (request_path, username);
