package org.artifactory.rest.services.retention;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Tamar Rosen
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetArtifactRetentionArchiveRepositoriesService implements RestService {
    private final AddonsManager addonsManager;

    public GetArtifactRetentionArchiveRepositoriesService(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        ArtifactRetentionAddon artifactRetentionAddon = addonsManager.addonByType(ArtifactRetentionAddon.class);
        List<String> archiveRepos = artifactRetentionAddon.getNamespacedArchiveRepositories();
        response.iModel(archiveRepos);
    }
}
