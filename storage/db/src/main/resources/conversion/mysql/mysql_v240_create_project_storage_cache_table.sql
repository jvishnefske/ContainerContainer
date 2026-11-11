CREATE TABLE storage_cache
(
    id                      BIGINT         AUTO_INCREMENT NOT NULL,
    entity_type             VARCHAR(32)    NOT NULL,
    entity_key              VARCHAR(255)   NOT NULL,
    cache_value             VARCHAR(10240) NOT NULL,
    calculation_timestamp   BIGINT         NOT NULL,
    CONSTRAINT storage_cache_pk PRIMARY KEY (id),
    CONSTRAINT storage_cache_uk UNIQUE (entity_type, entity_key)
);