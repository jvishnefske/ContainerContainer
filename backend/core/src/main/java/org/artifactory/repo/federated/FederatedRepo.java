package org.artifactory.repo.federated;

import org.artifactory.api.config.ImportableExportable;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.StoringRepo;

public interface FederatedRepo extends LocalRepo<FederatedRepoDescriptor, FederatedRepoConfig>,
        StoringRepo<FederatedRepoDescriptor, FederatedRepoConfig>, ImportableExportable {

    boolean isActive();
}
