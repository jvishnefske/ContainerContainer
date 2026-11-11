UPDATE migration_status
SET identifier = 'retention_restore_artifact_' || SUBSTR(identifier, 19)
WHERE identifier LIKE 'retention_restore_%'
  AND identifier NOT LIKE 'retention_restore_package_%';