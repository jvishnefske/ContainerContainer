CREATE TABLE virtual_repo_dangling_refs
(
    virtual_repo_key VARCHAR(64) NOT NULL,
    child_repo_key   VARCHAR(64) NOT NULL,
    created          BIGINT      NOT NULL,
    modified         BIGINT      NOT NULL,
    CONSTRAINT virtual_repo_dangling_refs_pk PRIMARY KEY (virtual_repo_key, child_repo_key)
);
