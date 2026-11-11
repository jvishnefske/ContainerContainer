package org.artifactory.rest.services.retention;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.rest.common.exception.FailedToRetrieveArchiveStorageSummaryException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.storage.StorageSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Aviv Anidjar
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ArtifactRetentionArchiveStorageSummaryService implements RestService<StorageSummary> {
    private static final Logger log = LoggerFactory.getLogger(ArtifactRetentionArchiveStorageSummaryService.class);

    @Override
    public void execute(ArtifactoryRestRequest<StorageSummary> request, RestResponse response) {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        try {
            ArtifactRetentionAddon artifactRetentionAddon = addonsManager.addonByType(ArtifactRetentionAddon.class);
            StorageSummary archiveStorageSummary = artifactRetentionAddon.getArchiveStorageSummary();
            response.iModel(archiveStorageSummary);
        } catch (FailedToRetrieveArchiveStorageSummaryException e) {
            log.error(e.getMessage(), "Failed to retrieve archive storage summary");
            response.error(e.getMessage()).responseCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
