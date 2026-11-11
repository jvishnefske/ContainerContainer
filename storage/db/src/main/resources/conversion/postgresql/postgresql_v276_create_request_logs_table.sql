CREATE TABLE request_logs (
    id                    BIGSERIAL     NOT NULL,
    request_time          BIGINT        NOT NULL,
    trace_id              VARCHAR(64),
    username              VARCHAR(256)  NOT NULL,
    http_method           VARCHAR(10)   NOT NULL,
    request_path          VARCHAR(2048) NOT NULL,
    status_code           INT           NOT NULL,
    duration_ms           BIGINT        NOT NULL,
    search_vector         TSVECTOR      GENERATED ALWAYS AS (
        to_tsvector('simple',
            coalesce(request_path, '')                                        || ' ' ||
            regexp_replace(coalesce(request_path, ''), '/', ' ', 'g')        || ' ' ||
            coalesce(username, '')                                            || ' ' ||
            coalesce(CAST(status_code AS VARCHAR), '')
        )
    ) STORED,
    CONSTRAINT request_logs_pk PRIMARY KEY (id)
);

CREATE INDEX request_logs_request_time_idx
    ON request_logs USING BRIN (request_time) WITH (autosummarize = on);

CREATE INDEX req_logs_username_time_idx
    ON request_logs (username, request_time DESC);

CREATE INDEX request_logs_trace_idx
    ON request_logs (trace_id) WHERE trace_id IS NOT NULL;

