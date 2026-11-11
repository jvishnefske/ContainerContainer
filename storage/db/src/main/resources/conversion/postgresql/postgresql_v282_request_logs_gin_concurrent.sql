CREATE INDEX CONCURRENTLY IF NOT EXISTS request_logs_search_vector_idx
    ON request_logs USING GIN (search_vector);
