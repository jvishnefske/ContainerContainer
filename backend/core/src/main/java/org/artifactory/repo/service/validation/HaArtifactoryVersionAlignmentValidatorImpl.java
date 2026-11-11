package org.artifactory.repo.service.validation;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.converter.ConverterManager;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.version.VersionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Reloadable(beanClass = HaArtifactoryVersionAlignmentValidator.class)
@Component
public class HaArtifactoryVersionAlignmentValidatorImpl implements HaArtifactoryVersionAlignmentValidator {

    private final ArtifactoryServersCommonService serversService;
    private boolean isHaEnabled;

    @Autowired
    public HaArtifactoryVersionAlignmentValidatorImpl(ArtifactoryServersCommonService serversService) {
        this.serversService = serversService;
    }

    @Override
    public void init() {
        this.isHaEnabled =
                ContextHelper.get().beanForType(AddonsManager.class).addonByType(HaCommonAddon.class).isHaEnabled();
    }


    @Override
    public void validate() {
        if (isHaEnabled) {
            ArtifactoryContext artifactoryContext = ContextHelper.get();
            // If in the middle of conversions
            ConverterManager converterManager = artifactoryContext.getConverterManager();
            if (converterManager != null && converterManager.isConverting()) {
                return;
            }
            // Denied if found two nodes with different versions
            List<ArtifactoryServer> otherRunningHaMembers = serversService.getOtherRunningHaMembers();
            VersionProvider versionProvider = artifactoryContext.getVersionProvider();
            CompoundVersionDetails runningVersion = versionProvider.getRunningServiceVersion();
            for (ArtifactoryServer otherRunningHaMember : otherRunningHaMembers) {
                String otherArtifactoryVersion = otherRunningHaMember.getArtifactoryVersion();
                if (!runningVersion.getVersionName().equals(otherArtifactoryVersion)) {
                    throw new ArtifactoryVersionException(
                            "Unstable environment: Found one or more servers with different version");
                }
            }
        }
    }
}