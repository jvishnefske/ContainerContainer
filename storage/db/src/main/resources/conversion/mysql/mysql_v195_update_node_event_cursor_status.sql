UPDATE node_event_cursor
SET status = 'HEALTHY'
where status = 'SYNC';