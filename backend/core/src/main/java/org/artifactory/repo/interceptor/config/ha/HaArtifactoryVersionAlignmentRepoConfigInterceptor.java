package org.artifactory.repo.interceptor.config.ha;

import org.artifactory.config.ConfigurationException;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.service.validation.ArtifactoryVersionException;
import org.artifactory.repo.service.validation.HaArtifactoryVersionAlignmentValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;

@Component
public class HaArtifactoryVersionAlignmentRepoConfigInterceptor implements RepoConfigChangeInterceptor {

    private final HaArtifactoryVersionAlignmentValidator haArtifactoryVersionAlignmentValidator;

    @Autowired
    public HaArtifactoryVersionAlignmentRepoConfigInterceptor(
            HaArtifactoryVersionAlignmentValidator haArtifactoryVersionAlignmentValidator) {
        this.haArtifactoryVersionAlignmentValidator = haArtifactoryVersionAlignmentValidator;
    }

    /**
     * @deprecated Implement {@link RepoConfigChangeInterceptor#onBeforeSave(RepoConfigChanges)} instead
     */
    @Deprecated(since = "7.104.x")
    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        try {
            haArtifactoryVersionAlignmentValidator.validate();
        } catch (ArtifactoryVersionException e) {
            throw new ConfigurationException(e.getMessage(), SC_METHOD_NOT_ALLOWED);
        }
    }
}