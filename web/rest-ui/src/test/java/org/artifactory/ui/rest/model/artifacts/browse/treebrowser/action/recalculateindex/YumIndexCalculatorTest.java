package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.recalculateindex;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.yum.YumAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
public class YumIndexCalculatorTest {

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testCalculateIndex() throws Exception {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        AddonsManager addonsManager = mock(AddonsManager.class);
        YumAddon yumAddon = mock(YumAddon.class);
        when(addonsManager.addonByType(YumAddon.class)).thenReturn(yumAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);

        RepositoryConfigService repositoryService = mock(RepositoryConfigService.class);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryService);

        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);

        String repoName = "repo";
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create(repoName, RepoType.Generic);
        when(repositoryService.getLocalOrFederatedRepoConfigByKey(repoName)).thenReturn(federatedRepoConfig);

        CentralConfigService service = mock(CentralConfigService.class);
        when(service.getDescriptor()).thenReturn(descriptor);
        when(context.getCentralConfig()).thenReturn(service);

        YumIndexCalculator indexCalculator = new YumIndexCalculator();
        indexCalculator.setRepoKey(repoName);
        indexCalculator.calculateIndex();
        verify(yumAddon, times(1)).requestRpmMetadataCalculation(federatedRepoConfig, null, true);
    }
}