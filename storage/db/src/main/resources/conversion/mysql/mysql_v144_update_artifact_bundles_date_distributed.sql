UPDATE artifact_bundles
SET date_distributed = date_created
where type = 'TARGET' AND date_distributed IS NULL;