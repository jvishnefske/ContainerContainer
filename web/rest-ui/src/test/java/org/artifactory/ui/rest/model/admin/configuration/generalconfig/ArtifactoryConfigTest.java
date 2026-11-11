package org.artifactory.ui.rest.model.admin.configuration.generalconfig;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.addon.AddonSettings;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.distribution.ReleaseBundlesConfig;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.descriptor.replication.GlobalReplicationsConfigDescriptor;
import org.artifactory.descriptor.signature.SignedUrlConfig;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.mockito.Mockito;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

/**
 * @author Alexei Vainshtein
 */
public class ArtifactoryConfigTest {

    @Test
    public void testConstructor() {
        CentralConfigDescriptor mutableDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        FolderDownloadConfigDescriptor folderDownloadDescriptor = Mockito.mock(FolderDownloadConfigDescriptor.class);
        TrashcanConfigDescriptor trashcanConfigDescriptor = Mockito.mock(TrashcanConfigDescriptor.class);
        GlobalReplicationsConfigDescriptor replicationsConfig = Mockito.mock(GlobalReplicationsConfigDescriptor.class);
        SumoLogicConfigDescriptor sumoLogicConfig = Mockito.mock(SumoLogicConfigDescriptor.class);
        SignedUrlConfig signedUrlConfig = Mockito.mock(SignedUrlConfig.class);
        Mockito.when(mutableDescriptor.getFolderDownloadConfig()).thenReturn(folderDownloadDescriptor);
        Mockito.when(mutableDescriptor.getTrashcanConfig()).thenReturn(trashcanConfigDescriptor);
        Mockito.when(mutableDescriptor.getReplicationsConfig()).thenReturn(replicationsConfig);
        Mockito.when(mutableDescriptor.getSumoLogicConfig()).thenReturn(sumoLogicConfig);
        Mockito.when(mutableDescriptor.getSignedUrlConfig()).thenReturn(signedUrlConfig);
        ReleaseBundlesConfig releaseBundlesConfig = Mockito.mock(ReleaseBundlesConfig.class);
        Mockito.when(mutableDescriptor.getReleaseBundlesConfig()).thenReturn(releaseBundlesConfig);
        AddonSettings addonSettings = Mockito.mock(AddonSettings.class);
        Mockito.when(mutableDescriptor.getAddons()).thenReturn(addonSettings);
        ArtifactoryContext context = Mockito.mock(ArtifactoryContext.class);
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        ArtifactoryBinaryService service = Mockito.mock(ArtifactoryBinaryService.class);
        Mockito.when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        Mockito.when(context.beanForType(ArtifactoryBinaryService.class)).thenReturn(service);
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryConfig config = new ArtifactoryConfig(mutableDescriptor);
    }

    @AfterClass
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

}