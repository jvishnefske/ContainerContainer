CREATE INDEX CONCURRENTLY repo_build_name_date_desc_idx ON builds (repo, build_name, build_date DESC) INCLUDE (build_id, build_number);
