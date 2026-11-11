ALTER TABLE distributed_locks DROP CONSTRAINT locks_pk;
ALTER TABLE distributed_locks ALTER COLUMN category VARCHAR(128) NOT NULL;
ALTER TABLE distributed_locks ADD CONSTRAINT locks_pk PRIMARY KEY (category,lock_key);
