drop table MIGRATION_STATUS;
create table MIGRATION_STATUS
(
    IDENTIFIER          VARCHAR(255) not null
        constraint IDENTIFIER_PK
            primary key,
    STARTED             BIGINT       not null,
    FINISHED            BIGINT       not null,
    MIGRATION_INFO_BLOB BLOB
);