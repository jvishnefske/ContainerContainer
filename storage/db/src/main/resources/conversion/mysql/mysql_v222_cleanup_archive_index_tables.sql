START TRANSACTION;

-- Drop foreign key constraints.
ALTER TABLE indexed_archives_entries DROP FOREIGN KEY indexed_archives_id_fk;
ALTER TABLE indexed_archives_entries DROP FOREIGN KEY entry_path_id_fk;
ALTER TABLE indexed_archives_entries DROP FOREIGN KEY entry_name_id_fk;
ALTER TABLE indexed_archives DROP FOREIGN KEY indexed_archives_binaries_fk;

-- Truncate the tables.
TRUNCATE TABLE indexed_archives_entries;
TRUNCATE TABLE indexed_archives;
TRUNCATE TABLE archive_paths;
TRUNCATE TABLE archive_names;

-- Recreate foreign key constraints
ALTER TABLE indexed_archives_entries ADD CONSTRAINT indexed_archives_id_fk FOREIGN KEY (indexed_archives_id) REFERENCES indexed_archives (indexed_archives_id);
ALTER TABLE indexed_archives_entries ADD CONSTRAINT entry_path_id_fk FOREIGN KEY (entry_path_id) REFERENCES archive_paths (path_id);
ALTER TABLE indexed_archives_entries ADD CONSTRAINT entry_name_id_fk FOREIGN KEY (entry_name_id) REFERENCES archive_names (name_id);

ALTER TABLE indexed_archives ADD CONSTRAINT indexed_archives_binaries_fk FOREIGN KEY (archive_sha1) REFERENCES binaries (sha1);

COMMIT;