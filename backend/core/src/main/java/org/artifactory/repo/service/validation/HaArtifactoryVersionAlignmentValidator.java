package org.artifactory.repo.service.validation;

import org.artifactory.spring.ReloadableBean;

public interface HaArtifactoryVersionAlignmentValidator extends ReloadableBean {

    /**
     * Validate whether all servers (HA members) have the same artifactory version
     *
     * @throws ArtifactoryVersionException if exists a server that is not aligned to the artifactory version
     */
    void validate();
}
