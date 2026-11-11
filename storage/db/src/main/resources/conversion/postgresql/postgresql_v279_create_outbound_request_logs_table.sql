CREATE TABLE outbound_request_logs (
    id                    BIGSERIAL      NOT NULL,
    request_time          BIGINT         NOT NULL,
    trace_id              VARCHAR(64),
    repo_key              VARCHAR(128)   NOT NULL,
    http_method           VARCHAR(10)    NOT NULL,
    request_url           VARCHAR(2048)  NOT NULL,
    status_code           INT            NOT NULL,
    duration_ms           BIGINT         NOT NULL,
    search_vector         TSVECTOR       GENERATED ALWAYS AS (
        to_tsvector('simple',
            coalesce(request_url, '')                                         || ' ' ||
            regexp_replace(coalesce(request_url, ''), '/', ' ', 'g')         || ' ' ||
            coalesce(repo_key, '')                                            || ' ' ||
            coalesce(http_method, '')                                         || ' ' ||
            coalesce(trace_id, '')
        )
    ) STORED,
    CONSTRAINT outbound_request_logs_pk PRIMARY KEY (id)
);

CREATE INDEX outbound_rl_request_time_idx
    ON outbound_request_logs USING BRIN (request_time) WITH (autosummarize = on);

CREATE INDEX outbound_rl_status_time_idx
    ON outbound_request_logs (status_code, request_time DESC);

CREATE INDEX outbound_rl_repo_time_idx
    ON outbound_request_logs (repo_key, request_time DESC);

CREATE INDEX outbound_rl_trace_idx
    ON outbound_request_logs (trace_id) WHERE trace_id IS NOT NULL;

