package org.artifactory.application.service;

import com.google.common.annotations.VisibleForTesting;
import com.jfrog.apptrust.client.ApptrustClient;
import com.jfrog.apptrust.client.ApptrustClientBuilderImpl;
import com.jfrog.apptrust.client.token.BasicTokenProviderImpl;
import com.jfrog.apptrust.v1.packages.PackageVersionCreatedRequest;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.application.exception.ApplicationServiceDisableException;
import org.artifactory.application.exception.ApplicationServiceException;
import org.artifactory.application.exception.NotifyExistsPackageException;
import org.artifactory.application.exception.NotifyPackageCreatedException;
import org.artifactory.application.model.ApplicationServiceSBOM;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.event.service.metadata.model.MetadataEvent;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.tenant.ArtifactoryDefaultTenantContextProvider;
import org.artifactory.update.md.MetadataConverterUtils;
import org.jfrog.metadata.client.model.MetadataPackage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

import static org.artifactory.util.application.ApplicationServiceConfigurationUtil.APPLICATION_SERVICE_BRANCH_PROP_KEY;
import static org.artifactory.util.application.ApplicationServiceConfigurationUtil.APPLICATION_SERVICE_REVISION_PROP_KEY;
import static org.artifactory.util.application.ApplicationServiceConfigurationUtil.APPLICATION_SERVICE_VCS_URL_PROP_KEY;

/**
 * @author Natalie Berni
 */
@Service
@Slf4j
@Reloadable(beanClass = ApplicationService.class, initAfter = {AccessService.class})
public class ApplicationServiceImpl implements ApplicationService {

    @Getter
    private ApptrustClient apptrustClient;

    @Setter(onMethod_ = {@Autowired})
    private AccessService accessService;

    @Setter(onMethod_ = {@Autowired})
    private ArtifactoryServersCommonService artifactoryServersCommonService;

    @Override
    public Optional<ApplicationServiceSBOM> createApplicationServiceSBOM(MetadataEvent metadataEvent,
            MetadataPackage metadataPackage) {
        if (StringUtils.isBlank(metadataEvent.getApplicationKey())) {
            log.debug("Application key is missing in metadata event, no Application Service SBOM will be created");
            return Optional.empty();
        }

        String version =
                metadataPackage.getVersions().isEmpty() ? null : metadataPackage.getVersions().getFirst().getName();

        String[] uniquePackageKeyPart = MetadataConverterUtils.getPackageUniqueKey(metadataPackage);

        if (uniquePackageKeyPart.length != 2) {
            log.error("Could not get package unique key  key base on package id {}", metadataPackage.getPkgid());
            return Optional.empty();
        }

        ApplicationServiceSBOM applicationServiceSBOM = new ApplicationServiceSBOM(metadataEvent.getApplicationKey(),
                uniquePackageKeyPart[0], uniquePackageKeyPart[1], version, metadataEvent.getApplicationProperties());

        log.debug("Application Service SBOM created for application {} with id {}",
                applicationServiceSBOM.applicationKey(), applicationServiceSBOM.getPackageDetails());
        return Optional.of(applicationServiceSBOM);
    }

    @Override
    public void notifyPackageCreated(ApplicationServiceSBOM applicationServiceSBOM) throws ApplicationServiceException {
        log.debug("Notifying application service for application {} with id {}",
                applicationServiceSBOM.applicationKey(), applicationServiceSBOM.getPackageDetails());

        if (!isApplicationServiceEnabled()) {
            throw new ApplicationServiceDisableException("Application service is not enabled");
        }
        try {
            apptrustClient.packagesClient().notifyPackageCreated(
                    getPackageVersionCreatedRequest(applicationServiceSBOM));
            log.debug("Application {} was successfully notified for package {}",
                    applicationServiceSBOM.applicationKey(), applicationServiceSBOM.getPackageDetails());
        } catch (Exception e) {
            if (e.getMessage().contains("already exists")) {
                String msg = String.format("Package %s already exists", applicationServiceSBOM.getPackageDetails());
                throw new NotifyExistsPackageException(msg, e);
            }
            throw new NotifyPackageCreatedException("Failed to notify package created", e);
        }
    }

    private @NonNull PackageVersionCreatedRequest getPackageVersionCreatedRequest(
            ApplicationServiceSBOM applicationServiceSBOM) {
        return PackageVersionCreatedRequest.newBuilder()
                .setApplicationKey(applicationServiceSBOM.applicationKey())
                .setPackageType(applicationServiceSBOM.packageType())
                .setPackageName(applicationServiceSBOM.packageName())
                .setPackageVersion(applicationServiceSBOM.packageVersion())
                .setBranch(applicationServiceSBOM.props()
                        .getOrDefault(APPLICATION_SERVICE_BRANCH_PROP_KEY, StringUtils.EMPTY))
                .setRevision(applicationServiceSBOM.props()
                        .getOrDefault(APPLICATION_SERVICE_REVISION_PROP_KEY, StringUtils.EMPTY))
                .setVcsUrl(applicationServiceSBOM.props()
                        .getOrDefault(APPLICATION_SERVICE_VCS_URL_PROP_KEY, StringUtils.EMPTY))
                .build();
    }

    @Override
    public boolean isApplicationServiceEnabled() {
        return ConstantValues.applicationServiceFeatureEnabled.getBoolean() && apptrustClient != null &&
                apptrustClient.ping();
    }

    /**
     * Initializes the application lifecycle client.
     *
     * <p>This method sets up the necessary configurations and connections
     * for the application lifecycle client.</p>
     *
     * <p>The following points highlight the relationship between the
     * application lifecycle client and the application service:</p>
     *
     * <ul>
     *     <li>The {@code applicationLifecycleClient} is designed to operate independently
     *         of the application service.</li>
     *     <li>Even if the application service is down or undergoing a restart,
     *         the application lifecycle client remains operational.</li>
     *     <li>The client maintains its state and functionality, allowing for a
     *         seamless experience regardless of the application service's status.</li>
     * </ul>
     */
    @Override
    public void init() {
        apptrustClient = createApplicationLifecycleClient();
    }

    @VisibleForTesting
    ApptrustClient createApplicationLifecycleClient() {
        return ApptrustClientBuilderImpl.newBuilder()
                .nodeId(artifactoryServersCommonService.getArtifactoryServer(
                                ContextHelper.get().getServerId())
                        .getServerId()).
                tokenProvider(new BasicTokenProviderImpl(accessService.getAccessClient())).
                tenantProvider(new ArtifactoryDefaultTenantContextProvider(accessService)).
                serviceId(accessService.getArtifactoryServiceId().getFormattedName()).
                routerPort(ArtifactoryHome.get().getSysConfig().helper().getRouterPort()).
                create();
    }

    @Override
    public void destroy() {
        if (apptrustClient != null) {
            try {
                log.debug("ApplicationServiceImpl is being destroyed, closing application lifecycle client");
                apptrustClient.close();
            } catch (IOException e) {
                log.error("Failed to close application lifecycle client", e);
            } finally {
                apptrustClient = null;
            }
        }
    }
}
