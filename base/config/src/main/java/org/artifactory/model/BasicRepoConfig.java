package org.artifactory.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.federated.FederatedMember;

import java.util.List;

@Getter
@EqualsAndHashCode(
        of = {"repoKey", "modification", "repoType", "packageType"},
        cacheStrategy = EqualsAndHashCode.CacheStrategy.LAZY
)
public class BasicRepoConfig {
    private final String repoKey;
    private final long modification;
    private final RepoDetailsType repoType;
    private final RepoType packageType;
    private final int memberCount;

    public BasicRepoConfig(String repoKey, long modification,
            RepoDetailsType repoType, RepoType packageType) {
        this(repoKey, modification, repoType, packageType, 0);
    }

    public BasicRepoConfig(String repoKey, long modification,
            RepoDetailsType repoType, RepoType packageType,
            int memberCount) {
        this.repoKey = repoKey;
        this.modification = modification;
        this.repoType = repoType;
        this.packageType = packageType;
        this.memberCount = memberCount;
    }

    public BasicRepoConfig(RepoConfig repoConfig) {
        this.repoKey = repoConfig.getKey();
        this.modification = repoConfig.getModification();
        this.repoType = repoConfig.getRepoType();
        this.packageType = repoConfig.getPackageType();
        this.memberCount = resolveMemberCount(repoConfig);
    }

    private static int resolveMemberCount(RepoConfig repoConfig) {
        if (repoConfig instanceof VirtualRepoConfig v
                && v.getRepoTypeConfig() != null
                && v.getRepoTypeConfig().getRepositoryRefs() != null) {
            return v.getRepoTypeConfig().getRepositoryRefs().size();
        }
        if (repoConfig instanceof FederatedRepoConfig f) {
            List<FederatedMember> members = f.getFederatedMembers();
            return members == null ? 0 : members.size();
        }
        return 0;
    }
}