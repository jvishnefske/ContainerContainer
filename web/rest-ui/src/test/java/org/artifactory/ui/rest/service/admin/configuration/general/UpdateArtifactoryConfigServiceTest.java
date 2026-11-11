package org.artifactory.ui.rest.service.admin.configuration.general;

import jakarta.annotation.Nonnull;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.ui.rest.model.admin.configuration.generalconfig.ArtifactoryConfig;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.artifactory.ui.rest.service.admin.configuration.general.UpdateConfigUtils.prepareAddonsManager;
import static org.artifactory.ui.rest.service.admin.configuration.general.UpdateConfigUtils.prepareMutableCentralConfigDescriptor;

/**
 * @author Alexei Vainshtein
 */
public class UpdateArtifactoryConfigServiceTest {

    @Test
    public void testUpdateDescriptorAndSave() {
        MutableCentralConfigDescriptor descriptor = prepareMutableCentralConfigDescriptor();
        CentralConfigService testCentralConfigService = Mockito.mock(CentralConfigService.class);
        Mockito.when(testCentralConfigService.getMutableDescriptor()).thenReturn(descriptor);
        UpdateArtifactoryConfigService service =
                new UpdateArtifactoryConfigService(testCentralConfigService, prepareAddonsManager());

        service.updateDescriptorAndSave(prepareConfig());
    }

    @Nonnull
    public ArtifactoryConfig prepareConfig() {
        ArtifactoryConfig config = new ArtifactoryConfig();
        config.setFileUploadMaxSize(10);
        config.setItemsBrowserPagingSize(500);
        config.setShowAddonSettings(false);
        config.setGlobalOfflineMode(true);
        config.setFolderDownloadEnabled(false);
        config.setFolderDownloadEnabled(false);
        return config;
    }
}