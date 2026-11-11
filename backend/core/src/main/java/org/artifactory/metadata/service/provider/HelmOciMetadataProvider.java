package org.artifactory.metadata.service.provider;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;

/**
 * @author asafz
 */
@Component
public class HelmOciMetadataProvider extends DockerMetadataProvider {

    private static final RepoType type = RepoType.HelmOCI;

    public HelmOciMetadataProvider() {
        super(type);
    }

    /**
     * All images in helm oci repo should be considered as helm charts
     */
    @Override
    protected String getType(@Nonnull Properties properties) {
        return RepoType.Helm.getType();
    }
}
