package org.artifactory.ui.rest.service.admin.configuration.general;

import jakarta.annotation.Nonnull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.descriptor.addon.AddonSettings;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.distribution.ReleaseBundlesConfig;
import org.artifactory.descriptor.download.DownloadRedirectConfigDescriptor;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.descriptor.replication.GlobalReplicationsConfigDescriptor;
import org.artifactory.descriptor.rlm.RlmConfig;
import org.artifactory.descriptor.signature.SignedUrlConfig;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.mockito.Mockito;

/**
 * @author Alexei Vainshtein
 */
public class UpdateConfigUtils {

    @Nonnull
    public static MutableCentralConfigDescriptor prepareMutableCentralConfigDescriptor() {
        MutableCentralConfigDescriptor descriptor = Mockito.mock(CentralConfigDescriptorImpl.class);
        Mockito.when(descriptor.getFolderDownloadConfig())
                .thenReturn(Mockito.mock(FolderDownloadConfigDescriptor.class));
        Mockito.when(descriptor.getTrashcanConfig()).thenReturn(Mockito.mock(TrashcanConfigDescriptor.class));
        Mockito.when(descriptor.getReplicationsConfig())
                .thenReturn(Mockito.mock(GlobalReplicationsConfigDescriptor.class));
        Mockito.when(descriptor.getSumoLogicConfig()).thenReturn(Mockito.mock(SumoLogicConfigDescriptor.class));
        Mockito.when(descriptor.getReleaseBundlesConfig()).thenReturn(Mockito.mock(ReleaseBundlesConfig.class));
        Mockito.when(descriptor.getRlmConfig()).thenReturn(Mockito.mock(RlmConfig.class));
        Mockito.when(descriptor.getSignedUrlConfig()).thenReturn(Mockito.mock(SignedUrlConfig.class));
        Mockito.when(descriptor.getDownloadRedirectConfig())
                .thenReturn(Mockito.mock(DownloadRedirectConfigDescriptor.class));

        // Repositories general config - required for RPM validation in UpdateGeneralConfigService
        RepositoriesGeneralConfigDescriptor reposGeneralConfig =
                new RepositoriesGeneralConfigDescriptor();
        // Ensure a non-null RPM config inside the repositories config (defaults already set in constructor)
        Mockito.when(descriptor.getRepositoriesGeneralConfig()).thenReturn(reposGeneralConfig);

        AddonSettings addonSettings = Mockito.mock(AddonSettings.class);
        Mockito.when(descriptor.getAddons()).thenReturn(addonSettings);
        return descriptor;
    }

    @Nonnull
    public static AddonsManager prepareAddonsManager() {
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        Mockito.when(addonsManager.addonByType(ReleaseBundleAddon.class))
                .thenReturn(Mockito.mock(ReleaseBundleAddon.class));
        return addonsManager;
    }
}
