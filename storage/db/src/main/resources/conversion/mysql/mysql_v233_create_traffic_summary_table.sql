CREATE TABLE traffic_summary
(
    id                      BIGINT AUTO_INCREMENT,
    time                    BIGINT             NOT NULL,
    total_request_count     BIGINT DEFAULT 0   NOT NULL,
    upload_request_count    BIGINT DEFAULT 0   NOT NULL,
    download_request_count  BIGINT DEFAULT 0   NOT NULL,
    total_bytes             BIGINT DEFAULT 0   NOT NULL,
    upload_bytes            BIGINT DEFAULT 0   NOT NULL,
    download_bytes          BIGINT DEFAULT 0   NOT NULL,
    CONSTRAINT traffic_summary_pk PRIMARY KEY (id)
);
CREATE INDEX traffic_summary_time_idx ON traffic_summary (time);