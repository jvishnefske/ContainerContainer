package org.artifactory.aql.model;

/**
 * @author Eli Fishman
 */
public record AqlTrackingInfo(String origin, String query) {

    /**
     * Origin value used by {@code POST /api/search/aql} -- the only origin that gets stats.
     */
    public static final String SEARCH_AQL_ORIGIN = "search/aql";
}
