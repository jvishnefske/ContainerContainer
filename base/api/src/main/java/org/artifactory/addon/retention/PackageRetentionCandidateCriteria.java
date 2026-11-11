package org.artifactory.addon.retention;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Oleksandr Legenkiy
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class PackageRetentionCandidateCriteria {
    private String packageType;
    private String componentName;
    private String version;
    private List<String> packageRepos;
    private Long createdBefore;
    private Long downloadedBefore;
}
