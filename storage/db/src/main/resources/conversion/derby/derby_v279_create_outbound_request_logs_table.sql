CREATE TABLE outbound_request_logs (
    id                    BIGINT       NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),
    request_time          BIGINT       NOT NULL,
    trace_id              VARCHAR(64),
    repo_key              VARCHAR(128) NOT NULL,
    http_method           VARCHAR(10)  NOT NULL,
    request_url           VARCHAR(2048) NOT NULL,
    status_code           INT          NOT NULL,
    duration_ms           BIGINT       NOT NULL,
    CONSTRAINT outbound_request_logs_pk PRIMARY KEY (id)
);

CREATE INDEX outbound_rl_request_time_idx ON outbound_request_logs (request_time);
CREATE INDEX outbound_rl_status_time_idx  ON outbound_request_logs (status_code, request_time);
CREATE INDEX outbound_rl_repo_time_idx    ON outbound_request_logs (repo_key, request_time);
CREATE INDEX outbound_rl_trace_idx        ON outbound_request_logs (trace_id);

