CREATE INDEX node_prop_nuget_lower_idx ON node_props (LOWER(prop_key), LOWER(prop_value)) ONLINE;
