CREATE TABLE virtual_repo_dangling_refs
(
    virtual_repo_key VARCHAR2(64)  NOT NULL,
    child_repo_key   VARCHAR2(64)  NOT NULL,
    created          NUMBER(19, 0) NOT NULL,
    modified         NUMBER(19, 0) NOT NULL,
    CONSTRAINT virtual_repo_dangling_refs_pk PRIMARY KEY (virtual_repo_key, child_repo_key)
);
