CREATE OR REPLACE VIEW repos_view
    (repository_key, type, package_type, created, modified, repo_ts_vector)
AS
SELECT repository_config.repository_key,
       repository_config.type,
       repository_config.package_type,
       repository_config.created,
       repository_config.modified,
       to_tsvector('simple', translate(repository_key, '/-_', '   '))
           AS repo_ts_vector
FROM repository_config
WHERE repository_config.repository_key::text <> 'auto-trashcan'::text;