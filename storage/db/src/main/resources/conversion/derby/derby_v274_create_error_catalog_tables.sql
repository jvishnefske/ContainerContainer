CREATE TABLE error_catalog_templates
(
    id                  BIGINT       NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),
    signature_hash      CHAR(64)     NOT NULL,
    severity            VARCHAR(8)   NOT NULL,
    logger_class        VARCHAR(512) NOT NULL,
    exception_class     VARCHAR(512),
    template_message    CLOB         NOT NULL,
    first_occurrence_at BIGINT       NOT NULL,
    last_occurrence_at  BIGINT       NOT NULL,
    occurrence_count    BIGINT       DEFAULT 1 NOT NULL,
    created_at          BIGINT       NOT NULL,
    updated_at          BIGINT       NOT NULL,
    CONSTRAINT error_catalog_templates_pk PRIMARY KEY (id),
    CONSTRAINT error_catalog_templates_uk UNIQUE (signature_hash)
);

CREATE INDEX error_catalog_templates_last_occ_idx ON error_catalog_templates (last_occurrence_at);
CREATE INDEX error_catalog_templates_sev_last_idx ON error_catalog_templates (severity, last_occurrence_at);
CREATE INDEX error_catalog_templates_logger_last_idx ON error_catalog_templates (logger_class, last_occurrence_at);

CREATE TABLE error_catalog
(
    id          BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),
    template_id BIGINT NOT NULL,
    occurred_at BIGINT NOT NULL,
    raw_message CLOB   NOT NULL,
    CONSTRAINT error_catalog_pk PRIMARY KEY (id)
);

CREATE INDEX error_catalog_template_idx ON error_catalog (template_id, occurred_at);
CREATE INDEX error_catalog_occurred_idx ON error_catalog (occurred_at);
