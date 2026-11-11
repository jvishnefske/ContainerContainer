CREATE TABLE storage_cache
(
    id                      BIGINT          IDENTITY(1,1) NOT NULL,
    entity_type             VARCHAR(32)     NOT NULL, -- Type of the cached entity (e.g., project, repository etc.)
    entity_key              VARCHAR(255)    NOT NULL, -- Unique (per type) key identifying the entity
    cache_value             NVARCHAR(MAX)   NOT NULL,-- Serialized cache value for the entity
    calculation_timestamp   BIGINT          NOT NULL, -- Timestamp (epoch ms) when the cache was calculated
    CONSTRAINT storage_cache_pk PRIMARY KEY (id),
    CONSTRAINT storage_cache_uk UNIQUE (entity_type, entity_key)
);