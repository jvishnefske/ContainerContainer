package org.artifactory.repo.validator.virtual;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.exception.InvalidOperationException;
import org.artifactory.model.VirtualRepoConfig;

/**
 * Interface for validating package-specific configurations of virtual repositories.
 * Concrete implementations should specify the package type they are responsible for (must be unique), returned on:
 * {@link VirtualRepoPackageSpecificValidator#getRepoType}.
 * It will be auto-injected into: {@link VirtualRepoPackageValidator}
 * @author Einas Ashkar
 */
public interface VirtualRepoPackageSpecificValidator {
    void validate(VirtualRepoConfig virtualRepoConfig) throws
            InvalidOperationException;

    RepoType getRepoType();
}
