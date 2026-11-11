package org.artifactory.aql.model;

/**
 * This is used to mark the type of AqlQuery currently processing, the default is local
 *
 * @author Tomer Slutsky
 */
public enum AqlQueryRepoType {
    local("local"),
    remote("remote"),
    virtual("virtual");

    private final String name;

    AqlQueryRepoType(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }
}
