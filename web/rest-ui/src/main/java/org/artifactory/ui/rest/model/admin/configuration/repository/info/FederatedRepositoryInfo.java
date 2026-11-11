package org.artifactory.ui.rest.model.admin.configuration.repository.info;

import org.artifactory.common.repo.RepoType;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.replication.ReplicationBaseDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.ph.PackageSpecification;

public class FederatedRepositoryInfo extends RepositoryInfo {

    private Boolean replications;

    public FederatedRepositoryInfo() {
    }

    public FederatedRepositoryInfo(FederatedRepoConfig repoConfig, CentralConfigDescriptor centralConfigDescriptor,
            PackageSpecification packageSpecification) {
        super(repoConfig, packageSpecification, RepoType.LOCAL);
        replications = (centralConfigDescriptor.getMultiLocalReplications(repoKey).stream()
                .anyMatch(ReplicationBaseDescriptor::isEnabled));
    }

    public Boolean getReplications() {
        return replications;
    }

    public void setReplications(Boolean replications) {
        this.replications = replications;
    }
}
