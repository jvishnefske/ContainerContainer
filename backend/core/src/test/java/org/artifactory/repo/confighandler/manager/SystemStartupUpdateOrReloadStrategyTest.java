package org.artifactory.repo.confighandler.manager;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfigPropagationModel;
import org.artifactory.repo.confighandler.ConfigChangeEventContext;
import org.artifactory.repo.service.RepoConfigBootstrapStorageHelper;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
class SystemStartupUpdateOrReloadStrategyTest {

    @Mock
    private RepoConfigBootstrapStorageHelper repoConfigBootstrapStorageHelper;
    @Mock
    private ArtifactoryApplicationContext context;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private HaAddon haAddon;
    @InjectMocks
    private SystemStartupUpdateOrReloadStrategy systemStartupUpdateOrReloadStrategy;

    private AutoCloseable mockitoAutoCloseable;

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void noReposToUpdateNoop() {
        ConfigChangeEventContext context = mock(ConfigChangeEventContext.class);
        when(context.getReposToUpdate()).thenReturn(Map.of());

        systemStartupUpdateOrReloadStrategy.updateOrReload(context);

        verifyNoInteractions(repoConfigBootstrapStorageHelper);
        verifyNoInteractions(addonsManager);
    }

    @Test
    void updateAndReloadConfigAndPropagateToHA() {
        ConfigChangeEventContext context = mock(ConfigChangeEventContext.class);
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        when(context.getReposToUpdate()).thenReturn(Map.of("repoKey", localRepoConfig));

        systemStartupUpdateOrReloadStrategy.updateOrReload(context);

        verify(repoConfigBootstrapStorageHelper).bulkUpdateRepositoryConfigs(Set.of(localRepoConfig));
        verify(repoConfigBootstrapStorageHelper).reloadRepoConfigurations();

        ArgumentCaptor<RepoConfigPropagationModel> modelArgumentCaptor =
                ArgumentCaptor.forClass(RepoConfigPropagationModel.class);

        verify(haAddon).propagateRepoConfigsChanges(modelArgumentCaptor.capture());
        RepoConfigPropagationModel repoConfigPropagationModel = modelArgumentCaptor.getValue();
        Assertions.assertFalse(repoConfigPropagationModel.isEmpty());
        Assertions.assertNotNull(repoConfigPropagationModel.getRepoConfigCreateOrUpdate());
        Assertions.assertEquals("repoKey",
                repoConfigPropagationModel.getRepoConfigCreateOrUpdate().iterator().next().getRepoKey());
    }
}