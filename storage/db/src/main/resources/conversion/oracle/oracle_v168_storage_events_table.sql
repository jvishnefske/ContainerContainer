CREATE SEQUENCE storage_events_event_id_seq
    START WITH 1
    MAXVALUE 9999999999999999999;

CREATE TABLE storage_events (
    event_id   NUMBER(19, 0) NOT NULL,
    timestamp  NUMBER(19, 0) NOT NULL,
    event_type NUMBER(5, 0)  NOT NULL,
    sha1       CHAR(40)      NOT NULL,
    sha256     CHAR(64)      NOT NULL,
    bin_length NUMBER(19, 0) NOT NULL,
    CONSTRAINT storage_events_pk PRIMARY KEY (event_id)
);