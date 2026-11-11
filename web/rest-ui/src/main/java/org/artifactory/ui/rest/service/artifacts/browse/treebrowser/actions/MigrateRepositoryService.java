package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.repo.PackageType;
import org.artifactory.rest.common.model.migrate.MigrateRepo;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Volodymyr Shulga
 */
@Slf4j
@Component
public class MigrateRepositoryService implements RestService<MigrateRepo> {
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RepositoryConfigService configService;
    @Autowired
    private AqlService aqlService;
    @Autowired
    private AddonsManager addonsManager;
    @Autowired
    private MeterRegistry meterRegistry;
    private SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest<MigrateRepo> request, RestResponse response) {
        MigrateRepo migrateRepo = request.getImodel();
        if (!ConstantValues.huggingFaceMLEnableFrogMLLayout.getBoolean()) {
            response.error("Hugging Face ML layout is disabled").responseCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }
        // only admin is allowed to run the migration because it changes some repo settings.
        if (!securityService.isAdmin()) {
            response.error("Forbidden").responseCode(HttpStatus.SC_FORBIDDEN);
            return;
        }
        PackageType packageType = migrateRepo.packageType();

        if (packageType == PackageType.HUGGINGFACEML) {
            final MigrateHFRepositoryService migrateService = createMigrateService();
            migrateService.execute(request, response);
        } else {
            response.error("Allowed for HUGGING_FACE_ML only").responseCode(HttpStatus.SC_BAD_REQUEST);
        }
    }

    @VisibleForTesting
    MigrateHFRepositoryService createMigrateService() {
        return new MigrateHFRepositoryService(repositoryService, aqlService, addonsManager,
                securityService, meterRegistry);
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }
}
