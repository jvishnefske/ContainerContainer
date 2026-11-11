UPDATE migration_status
SET identifier = REPLACE(identifier, 'retention_restore_', 'retention_restore_artifact_')
WHERE identifier LIKE 'retention_restore_%'
  AND identifier NOT LIKE 'retention_restore_package_%';