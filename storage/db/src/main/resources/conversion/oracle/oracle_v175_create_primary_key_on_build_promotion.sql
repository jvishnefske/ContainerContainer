ALTER TABLE build_promotions ADD CONSTRAINT build_promotions_builds_pk PRIMARY KEY (build_id, created)
    USING INDEX build_promotions_created_idx ENABLE NOVALIDATE;