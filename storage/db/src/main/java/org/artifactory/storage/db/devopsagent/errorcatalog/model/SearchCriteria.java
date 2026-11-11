package org.artifactory.storage.db.devopsagent.errorcatalog.model;

/**
 * Search criteria for the templates table. All fields are optional; {@code null} means "no constraint".
 *
 * @param searchText      free-form text to match against the template message via the per-DB FTS path
 * @param severityFilter  optional WARN or ERROR filter
 * @param loggerClassEq   optional exact-match logger class filter
 */
public record SearchCriteria(
        String searchText,
        String severityFilter,
        String loggerClassEq) {
}
