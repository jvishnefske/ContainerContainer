UPDATE migration_status
SET identifier = REPLACE(identifier, 'retention_archive_', 'retention_archive_artifact_')
WHERE identifier LIKE 'retention_archive_%'
  AND identifier NOT LIKE 'retention_archive_package_%';