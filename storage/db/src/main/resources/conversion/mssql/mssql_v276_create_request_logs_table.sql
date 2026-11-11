CREATE TABLE request_logs
(
    id                    BIGINT         IDENTITY (1, 1) NOT NULL,
    request_time          BIGINT         NOT NULL,
    trace_id              VARCHAR(64),
    username              NVARCHAR(256)  NOT NULL,
    http_method           VARCHAR(10)    NOT NULL,
    request_path          NVARCHAR(2048) NOT NULL,
    status_code           INT            NOT NULL,
    duration_ms           BIGINT         NOT NULL,
    CONSTRAINT request_logs_pk PRIMARY KEY (id)
);

CREATE INDEX request_logs_request_time_idx ON request_logs (request_time);
CREATE INDEX req_logs_username_time_idx ON request_logs (username, request_time DESC);
CREATE INDEX request_logs_trace_idx ON request_logs (trace_id);

