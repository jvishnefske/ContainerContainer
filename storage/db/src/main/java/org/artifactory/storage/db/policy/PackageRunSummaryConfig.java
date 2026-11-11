package org.artifactory.storage.db.policy;

import java.util.List;

/**
 * @author punyap
 */
public interface PackageRunSummaryConfig {
    List<String> getIncludedProjects();

    Boolean getIncludeAllProjects();
}
