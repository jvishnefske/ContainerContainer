package org.artifactory.rest.services.retention;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.rest.retention.ArtifactRetentionRestoreRequest;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.artifactory.addon.retention.ArtifactRetentionConstants.RESTORE_OPERATION_INITIALIZED_SUCCESSFULLY;

/**
 * @author Omri Ziv
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ArtifactRetentionRestoreService implements RestService<ArtifactRetentionRestoreRequest> {

    private static final Logger log = LoggerFactory.getLogger(ArtifactRetentionRestoreService.class);

    @Autowired
    private AddonsManager addonsManager;

    @Override
    public void execute(ArtifactoryRestRequest<ArtifactRetentionRestoreRequest> request, RestResponse response) {
        ArtifactRetentionRestoreRequest restoreRequest = request.getImodel();
        try {
            BasicStatusHolder statusHolder = new BasicStatusHolder();
            addonsManager.addonByType(ArtifactRetentionAddon.class).restore(restoreRequest, statusHolder);
            response.info(RESTORE_OPERATION_INITIALIZED_SUCCESSFULLY);
            response.responseCode(HttpServletResponse.SC_ACCEPTED);
        } catch (IOException e) {
            log.error("Failed to restore, caught exception: ", e);
            response.error("Failed to restore - " + e.getMessage());
        }
    }
}