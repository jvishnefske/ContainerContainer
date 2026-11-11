package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.releasebundle.ReleaseBundlesRepoDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;

import java.util.Objects;

public enum RepositoryType {

    LOCAL("Local"), FEDERATED("Federated"), REMOTE("Remote"), VIRTUAL("Virtual"),
    CACHED("Cached"), RELEASE_BUNDLES("ReleaseBundles"), SUPPORT_BUNDLES("SupportBundles");

    private final String typeName;

    RepositoryType(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getTypeNameLoweCase() {
        return getTypeName().toLowerCase();
    }

    public static RepositoryType byNativeName(String nativeName) {
        for (RepositoryType repoType : values()) {
            if (repoType.getTypeNameLoweCase().equals(nativeName.trim())) {
                return repoType;
            }
        }
        return null;
    }

    public static RepositoryType byConfig(RepoDescriptor descriptor) {
        if (descriptor instanceof LocalCacheRepoDescriptor) {
            return RepositoryType.CACHED;
        }
        if (descriptor instanceof ReleaseBundlesRepoDescriptor) {
            return RepositoryType.RELEASE_BUNDLES;
        }
        if (descriptor instanceof FederatedRepoDescriptor) {
            return RepositoryType.FEDERATED;
        }
        if (descriptor instanceof LocalRepoDescriptor) {
            return RepositoryType.LOCAL;
        }
        if (descriptor instanceof RemoteRepoDescriptor) {
            return RepositoryType.REMOTE;
        }
        if (descriptor instanceof VirtualRepoDescriptor) {
            return RepositoryType.VIRTUAL;
        }
        return RepositoryType.LOCAL;
    }

    public static RepositoryType byConfig(RepoConfig repoConfig) {
        if (Objects.equals(repoConfig.getRepoType(), RepoDetailsType.REMOTE)) {
            return RepositoryType.REMOTE;
        } else if (Objects.equals(repoConfig.getRepoType(), RepoDetailsType.VIRTUAL)) {
            return RepositoryType.VIRTUAL;
        } else if (Objects.equals(repoConfig.getRepoType(), RepoDetailsType.RELEASE_BUNDLE)) {
            return RepositoryType.RELEASE_BUNDLES;
        } else if (Objects.equals(repoConfig.getRepoType(), RepoDetailsType.FEDERATED)) {
            return RepositoryType.FEDERATED;
        } else if (Objects.equals(repoConfig.getRepoType(), RepoDetailsType.CACHED_REPO)) {
            return RepositoryType.CACHED;
        }
        return RepositoryType.LOCAL;
    }
}
