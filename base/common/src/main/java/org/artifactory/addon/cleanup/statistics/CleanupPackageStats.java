package org.artifactory.addon.cleanup.statistics;

import lombok.Data;

/**
 * Contains package cleanup statistics that are used for Call Home reporting feature
 *
 * @author Oleksandr Legenkiy
 */

@Data
public class CleanupPackageStats {

    private long policiesTotal;
    private long runsTotal;
    private long packagesCleaned;
    private long cleanedPackagesVirtualSize;
    private long cleanedPackagesPhysicalSize;
    private long packagesFailed;
    private long artifactsCleaned;

}