CREATE TABLE distributed_blocking_locks
(
    lock_key VARCHAR(255) NOT NULL,
    CONSTRAINT distributed_blocking_locks_pk PRIMARY KEY (lock_key)
);