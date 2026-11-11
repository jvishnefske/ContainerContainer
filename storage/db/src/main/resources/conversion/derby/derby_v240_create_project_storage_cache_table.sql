CREATE TABLE storage_cache
(
    id                      BIGINT         NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),
    entity_type             VARCHAR(32)    NOT NULL, -- Type of the cached entity (e.g., project, repository etc.)
    entity_key              VARCHAR(255)   NOT NULL, -- Unique (per type) key identifying the entity
    cache_value             VARCHAR(10240) NOT NULL, -- Serialized cache value for the entity
    calculation_timestamp   BIGINT         NOT NULL, -- Timestamp (epoch ms) when the cache was calculated
    CONSTRAINT storage_cache_pk PRIMARY KEY (id),
    CONSTRAINT storage_cache_uk UNIQUE (entity_type, entity_key)
);