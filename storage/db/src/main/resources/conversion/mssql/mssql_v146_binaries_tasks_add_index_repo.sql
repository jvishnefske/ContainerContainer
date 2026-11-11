IF CONVERT(VARCHAR, (SELECT SERVERPROPERTY ('edition'))) like '%Enterprise%'
    CREATE INDEX binaries_tasks_repo_key_idx ON binaries_tasks (local_repo_key) WITH (ONLINE = ON)
ELSE
    CREATE INDEX binaries_tasks_repo_key_idx ON binaries_tasks (local_repo_key);