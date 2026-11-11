package org.artifactory.storage.db.policy;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Builder(toBuilder = true)
@Setter
@Getter
public class RunSummaryConfig implements PackageRunSummaryConfig {
    private long created;
    private String createdBy;
    private Long modified;
    private String modifiedBy;
    private String cronExp;
    private String description;
    private Boolean skipTrashCan;
    private Integer durationInMinutes;
    private List<String> packageTypes;
    private List<String> repos;
    private List<String> excludedRepos;
    private List<String> includedPackages;
    private List<String> excludedPackages;
    private Boolean includeAllProjects;
    private List<String> includedProjects;
    private Boolean includeAllStages;
    private List<String> includedStages;
    private Integer createdBeforeInMonths;
    private Integer lastDownloadedBeforeInMonths;
    private Integer createdBeforeInDays;
    private Integer lastDownloadedBeforeInDays;
    private Integer keepLastNVersions;
    private Map<String, List<String>> includedProperties;
    private Map<String, List<String>> excludedProperties;
    private List<String> releaseBundles;
    private List<String> excludePromotedEnvironments;
    private List<String> buildPatterns;
    private Boolean deleteBuildArtifacts;
    private List<String> includedPackageVersions;
    private List<String> excludedPackageVersions;
    private Set<String> includedFolderPaths;
    private Set<String> excludedFolderPaths;

    @Override
    public Boolean getIncludeAllProjects() {
        return includeAllProjects;
    }

    @Override
    public List<String> getIncludedProjects() {
        return includedProjects;
    }
}
