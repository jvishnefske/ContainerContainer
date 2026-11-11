package org.artifactory.policy.cleanup.packages;

import lombok.Builder;
import lombok.Data;
import org.artifactory.common.repo.PackageType;

@Data
@Builder
public class PackageCleanupSupportOptions implements Comparable<PackageCleanupSupportOptions> {
    private PackageType packageType;
    private boolean nVersionSupport;

    @Override
    public int compareTo(PackageCleanupSupportOptions other) {
        return this.packageType.compareTo(other.packageType);
    }
}