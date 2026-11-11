package org.artifactory.federation;

import org.artifactory.descriptor.repo.RepoType;

public interface FederationLicenseService {
    void validateFederationLicenseInstalled();
    void validateFederationPackageTypeSupported(RepoType repoType);
}
