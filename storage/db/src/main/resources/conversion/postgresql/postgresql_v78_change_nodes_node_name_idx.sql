CREATE
INDEX CONCURRENTLY IF NOT EXISTS pattern_nodes_node_name_idx ON nodes (node_name varchar_pattern_ops);
DROP
INDEX IF EXISTS nodes_node_name_idx;
ALTER
INDEX pattern_nodes_node_name_idx RENAME TO nodes_node_name_idx;