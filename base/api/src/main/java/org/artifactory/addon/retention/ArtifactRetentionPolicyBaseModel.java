package org.artifactory.addon.retention;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public class ArtifactRetentionPolicyBaseModel {
    private String key;
    private boolean enabled;
    private String description;
    private String aqlQuery;
    private String cronExp;
    private Long durationInMinutes;
    private Long expirationTimeInMonths;
    private boolean skipTrashcan;
    private Boolean packageRetention;
    private ArtifactRetentionCandidateCriteria searchCriteriaForm;
    private PackageRetentionCandidateCriteria packageSearchCriteriaForm;
}
