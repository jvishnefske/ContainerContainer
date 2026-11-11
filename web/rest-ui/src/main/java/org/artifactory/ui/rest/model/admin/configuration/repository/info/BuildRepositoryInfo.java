package org.artifactory.ui.rest.model.admin.configuration.repository.info;

import org.artifactory.descriptor.repo.RepoDescriptor;

public class BuildRepositoryInfo extends RepositoryInfo {

    public BuildRepositoryInfo(RepoDescriptor descriptor) {
        this.repoKey = descriptor.getKey();
        this.repoType = descriptor.getType().toString();
    }
}
