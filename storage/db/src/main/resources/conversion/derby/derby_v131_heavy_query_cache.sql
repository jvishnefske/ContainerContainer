CREATE TABLE heavy_query_cache
(
    id          BIGINT       NOT NULL,
    cache_key   VARCHAR(255) NOT NULL,
    cache_time  BIGINT       NOT NULL,
    expiry_time BIGINT       NOT NULL,
    data        BLOB         NOT NULL,
    CONSTRAINT hqc_pk PRIMARY KEY (id)
);