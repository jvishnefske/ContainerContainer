package org.artifactory.aql.model;

/**
 * @author Eli Fishman
 */
public record AqlQueryContext(AqlQueryRepoType queryRepoType, AqlDomainEnum domain,
                              boolean aqlLimitByConfiguration, long limit) {
    public AqlQueryContext(AqlQueryRepoType queryRepoType, AqlDomainEnum domain) {
        this(queryRepoType, domain, false,  -1);
    }
}
