package org.artifactory.storage.db.policy;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author punyap
 */

@Builder(toBuilder = true)
@Setter
@Getter
public class RestoreRunSummaryConfig implements PackageRunSummaryConfig {
    Boolean restoreToOriginal;
    String fallbackDestinationPath;
    String newDestinationPath;
    List<String> manifestPaths;
    List<String> includedProjects;

    @Override
    public Boolean getIncludeAllProjects() {
        return false;
    }

    @Override
    public List<String> getIncludedProjects() {
        return includedProjects;
    }
}
