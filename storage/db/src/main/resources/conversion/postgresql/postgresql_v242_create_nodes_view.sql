CREATE OR REPLACE VIEW nodes_view
            (node_id, repo, node_path, node_name, created, modified, node_type, depth, bin_length,
             sha1_actual, md5_actual, sha256, node_ts_vector, full_path)
AS
SELECT nodes.node_id,
       nodes.repo,
       nodes.node_path,
       nodes.node_name,
       nodes.created,
       nodes.modified,
       nodes.node_type,
       nodes.depth,
       nodes.bin_length,
       nodes.sha1_actual,
       nodes.md5_actual,
       nodes.sha256,
       to_tsvector('simple', translate(repo || ' ' || node_path || ' ' || node_name, '/-_', '   ')) AS search_vector,
       nodes.repo || '/' || nodes.node_path || '/' || nodes.node_name AS full_path
FROM nodes
WHERE nodes.repo::text <> 'auto-trashcan'::text;