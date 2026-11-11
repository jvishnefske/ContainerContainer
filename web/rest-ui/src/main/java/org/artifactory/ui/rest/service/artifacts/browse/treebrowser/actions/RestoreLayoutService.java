package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import com.google.common.annotations.VisibleForTesting;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.repo.PackageType;

import org.artifactory.rest.common.model.restore.RestoreLayoutRepo;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * @author shahafg
 */
@Component
public class RestoreLayoutService implements RestService<RestoreLayoutRepo> {

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RepositoryConfigService configService;

    @Autowired
    private AddonsManager addonsManager;

    private SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest<RestoreLayoutRepo> request, RestResponse response) {
        RestoreLayoutRepo restoreLayoutRepo = request.getImodel();
        if (!ConstantValues.huggingFaceMLEnableFrogMLLayout.getBoolean()) {
            response.error("Hugging Face ML layout is disabled").responseCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }
        // only admin is allowed to run the migration because it changes some repo settings.
        if (!securityService.isAdmin()) {
            response.error("Forbidden").responseCode(HttpStatus.SC_FORBIDDEN);
            return;
        }

        if (restoreLayoutRepo != null) {
            PackageType packageType = restoreLayoutRepo.packageType();

            if (packageType == PackageType.HUGGINGFACEML) {
                final RestoreHFRepositoryLayoutService restoreService = createRestoreService();
                restoreService.execute(request, response);
            } else {
                response.error("Allowed for HUGGING_FACE_ML only").responseCode(HttpStatus.SC_BAD_REQUEST);
            }
        }
    }


    @VisibleForTesting
    RestoreHFRepositoryLayoutService createRestoreService() {
        return new RestoreHFRepositoryLayoutService(repositoryService, addonsManager, securityService);
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }
}
