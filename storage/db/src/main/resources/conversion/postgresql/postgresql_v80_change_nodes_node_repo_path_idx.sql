CREATE
UNIQUE INDEX CONCURRENTLY IF NOT EXISTS pattern_nodes_repo_path_name_idx ON nodes (repo varchar_pattern_ops, node_path varchar_pattern_ops, node_name varchar_pattern_ops);
DROP
INDEX IF EXISTS nodes_repo_path_name_idx;
ALTER
INDEX pattern_nodes_repo_path_name_idx RENAME TO nodes_repo_path_name_idx;