IF CONVERT(VARCHAR, (SELECT SERVERPROPERTY ('edition'))) like '%Enterprise%'
    CREATE INDEX node_events_timestamp_event_id_type_idx ON node_events (timestamp, event_id, event_type) WITH (ONLINE = ON)
ELSE
    CREATE INDEX node_events_timestamp_event_id_type_idx ON node_events (timestamp, event_id, event_type);
