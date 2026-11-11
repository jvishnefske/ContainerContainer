IF CONVERT(VARCHAR, (SELECT SERVERPROPERTY ('edition'))) like '%Enterprise%'
    CREATE INDEX repo_build_name_date_idx ON builds (repo, build_name, build_date) WITH (ONLINE = ON)
ELSE
    CREATE INDEX repo_build_name_date_idx ON builds (repo, build_name, build_date);