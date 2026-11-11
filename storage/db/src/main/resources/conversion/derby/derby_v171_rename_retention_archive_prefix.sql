UPDATE migration_status
SET identifier = 'retention_archive_artifact_' || SUBSTR(identifier, 19)
WHERE identifier LIKE 'retention_archive_%'
  AND identifier NOT LIKE 'retention_archive_package_%';