package org.artifactory.storage.db.repo.entity;

import java.util.Arrays;
import java.util.Objects;

/**
 * Lightweight repository-config projection for Grid inventory streaming.
 *
 * @author Hussein Alshqerat
 */
public record DbRepoKeyConfigBlob(String repositoryKey, String repoType, String packageType, byte[] configBlob) {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DbRepoKeyConfigBlob other)) {
            return false;
        }
        return Objects.equals(repositoryKey, other.repositoryKey)
                && Objects.equals(repoType, other.repoType)
                && Objects.equals(packageType, other.packageType)
                && Arrays.equals(configBlob, other.configBlob);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repositoryKey, repoType, packageType, Arrays.hashCode(configBlob));
    }

    @Override
    public String toString() {
        return "DbRepoKeyConfigBlob[repositoryKey=" + repositoryKey
                + ", repoType=" + repoType
                + ", packageType=" + packageType
                + ", configBlob=" + Arrays.toString(configBlob) + "]";
    }
}
