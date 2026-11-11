package org.artifactory.repo.packagetype.configuration;

import lombok.Data;

/**
 * @author Tamar Rosen
 */
@Data
public class GitConfiguration {
    private long modelVersion;
    private String registryUrl = "https://index.crates.io";
}
