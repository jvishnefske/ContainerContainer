package org.artifactory.ph;

import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;

import java.util.EnumSet;

/**
 * Utility mapper between {@link org.artifactory.descriptor.repo.RepoType} to {@link PackageType}
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PackageRepoTypeMapper {

    static final EnumSet<RepoType> EXCLUDED_REPO_TYPES = EnumSet.of(RepoType.Distribution);

    @Nullable
    static PackageType packageType(RepoConfig repoConfig) {
        return packageType(repoConfig.getPackageType());
    }

    @Nullable
    static PackageType packageType(RepoType repoType) {
        if (EXCLUDED_REPO_TYPES.contains(repoType)) {
            return null;
        }
        try {
            return PackageType.valueOf(repoType.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
