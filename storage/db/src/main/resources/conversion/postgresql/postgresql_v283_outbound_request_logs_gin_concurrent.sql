CREATE INDEX CONCURRENTLY IF NOT EXISTS outbound_rl_search_vector_idx
    ON outbound_request_logs USING GIN (search_vector);
