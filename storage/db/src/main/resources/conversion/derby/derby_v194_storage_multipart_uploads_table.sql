CREATE TABLE storage_multipart_uploads
(
    upload_id     VARCHAR(255) NOT NULL,
    created_date  BIGINT       NOT NULL,
    temp_path     VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    modified_date BIGINT       NOT NULL,
    data          CLOB(2048),
    CONSTRAINT storage_multipart_uploads_pk PRIMARY KEY (upload_id)
);
CREATE INDEX storage_mpu_created_date_idx ON storage_multipart_uploads (created_date);
CREATE INDEX storage_mpu_status_idx ON storage_multipart_uploads (status);