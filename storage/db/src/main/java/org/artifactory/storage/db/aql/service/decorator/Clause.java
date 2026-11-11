package org.artifactory.storage.db.aql.service.decorator;

/**
 * Represents model of AQL query clauses. Keep model as tree.
 *
 * Provides checks for whole query and/or for nested items in clause tree.
 */
interface Clause {

    /**
     * Check this node and all sub-nodes for filtering by repo (with equal operator).
     *
     * @return <code>true</code> in case query results will be filtered by a repo, <code>false</code> - otherwise.
     */
    boolean isFilteredByRepoEqual();
}
