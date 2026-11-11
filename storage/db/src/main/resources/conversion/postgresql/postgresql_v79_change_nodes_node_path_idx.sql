CREATE
INDEX CONCURRENTLY IF NOT EXISTS pattern_nodes_node_path_idx ON nodes (node_path varchar_pattern_ops);
DROP
INDEX IF EXISTS nodes_node_path_idx;
ALTER
INDEX pattern_nodes_node_path_idx RENAME TO nodes_node_path_idx;