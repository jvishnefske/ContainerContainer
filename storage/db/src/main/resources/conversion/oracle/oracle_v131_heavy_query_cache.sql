CREATE TABLE heavy_query_cache
(
    id          NUMBER(19)   NOT NULL,
    cache_key   VARCHAR(255) NOT NULL,
    cache_time  NUMBER(19)   NOT NULL,
    expiry_time NUMBER(19)   NOT NULL,
    data        BLOB         NOT NULL,
    CONSTRAINT hqc_pk PRIMARY KEY (id)
);