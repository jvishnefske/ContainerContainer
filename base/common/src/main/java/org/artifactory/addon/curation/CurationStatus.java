package org.artifactory.addon.curation;

import org.artifactory.common.repo.PackageType;

import java.util.Set;

/**
 * @author Amir Avidan
 */
public record CurationStatus(boolean status, boolean blockDownloadEnabled, boolean indexVersionsCompliantEnabled,
                             boolean allowDownloadWhenUnavailable, Set<PackageType> forceCVSPackages) {

    public CurationStatus {
        forceCVSPackages = ForceCVSPackagesParser.emptyIfNull(forceCVSPackages);
    }

    public static final CurationStatus DISABLED = new CurationStatus(false, false, false, false, Set.of());
}
