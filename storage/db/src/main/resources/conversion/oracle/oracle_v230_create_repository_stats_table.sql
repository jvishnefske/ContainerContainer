CREATE TABLE repository_stats (
    repository_key       VARCHAR2(64)   NOT NULL,
    last_modified        NUMBER(19, 0)  NOT NULL,
    CONSTRAINT repository_stats_pk PRIMARY KEY (repository_key),
    CONSTRAINT repository_stats_repository_key_fk FOREIGN KEY (repository_key) REFERENCES repository_config (repository_key) ON DELETE CASCADE
);
