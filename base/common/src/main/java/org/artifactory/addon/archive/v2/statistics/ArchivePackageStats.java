package org.artifactory.addon.archive.v2.statistics;

import lombok.Data;

@Data
public class ArchivePackageStats {
    // Total number of archive policies
    private long policiesTotal;
    // Total number of finished runs across all archive policies
    private long runsTotal;
    // Total number of cleaned packages across all finished runs
    private long packagesCleaned;
    // Total virtual size of cleaned packages across all finished runs
    private long archivedPackagesVirtualSize;
    // Total physical size of cleaned packages across all finished runs
    private long archivedPackagesPhysicalSize;
    // Total number of packages failed to process across all finished runs
    private long packagesFailed;
}