CREATE TABLE storage_events (
    event_id   BIGINT      NOT NULL AUTO_INCREMENT,
    timestamp  BIGINT      NOT NULL,
    event_type SMALLINT    NOT NULL,
    sha1       CHAR(40)    NOT NULL,
    sha256     CHAR(64)    NOT NULL,
    bin_length BIGINT      NOT NULL,
    CONSTRAINT storage_events_pk PRIMARY KEY (event_id)
);
