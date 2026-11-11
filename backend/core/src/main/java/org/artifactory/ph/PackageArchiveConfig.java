package org.artifactory.ph;

import org.artifactory.common.repo.PackageType;

public record PackageArchiveConfig(PackageType packageType, boolean isSupportedNLatest) {
}
