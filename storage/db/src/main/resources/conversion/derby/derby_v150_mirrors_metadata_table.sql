CREATE TABLE mirrors_metadata
(
    metadata_type    VARCHAR(64)   NOT NULL,
    member_url       VARCHAR(500) NOT NULL,
    cache_time       BIGINT        NOT NULL,
    metadata_blob    BLOB,
    CONSTRAINT mirrors_remote_pk PRIMARY KEY (metadata_type, member_url)
);

CREATE INDEX cache_time_idx ON mirrors_metadata (cache_time);
CREATE INDEX member_url_idx ON mirrors_metadata (member_url);

