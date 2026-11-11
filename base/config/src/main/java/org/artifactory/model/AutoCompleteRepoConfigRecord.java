package org.artifactory.model;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;

import java.util.List;

public record AutoCompleteRepoConfigRecord(
        String repoKey,
        RepoDetailsType repoType,
        RepoType packageType,
        List<String> stages) {

    public static AutoCompleteRepoConfigRecord valueOf(RepoConfig repoConfig) {
        return new AutoCompleteRepoConfigRecord(
                repoConfig.getKey(), repoConfig.getRepoType(),
                repoConfig.getPackageType(), null);
    }

    public AutoCompleteRepoConfigRecord withStages(List<String> stages) {
        return new AutoCompleteRepoConfigRecord(
                repoKey, repoType, packageType, stages);
    }
}
