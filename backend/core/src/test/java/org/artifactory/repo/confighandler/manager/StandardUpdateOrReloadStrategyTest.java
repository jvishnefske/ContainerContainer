package org.artifactory.repo.confighandler.manager;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.ReferencedConfigChange;
import org.artifactory.repo.confighandler.ConfigChangeEventContext;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.spring.RepoConfigChangeListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
class StandardUpdateOrReloadStrategyTest {

    private AutoCloseable mockitoAutoCloseable;

    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private RepoConfigChangeListener repoConfigChangeListener;
    @Mock
    private ArtifactoryApplicationContext applicationContext;

    private StandardUpdateOrReloadStrategy standardUpdateOrReloadStrategy;

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        Map<String, RepoConfigChangeListener> beansForTypeMap =
                Map.of("repoConfigChangeReloadableListener", repoConfigChangeListener);
        standardUpdateOrReloadStrategy =
                new StandardUpdateOrReloadStrategy(repositoryService);
        ArtifactoryContextThreadBinder.bind(applicationContext);

        when(applicationContext.beansForType(RepoConfigChangeListener.class)).thenReturn(beansForTypeMap);
        standardUpdateOrReloadStrategy.init();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void emptyReposToUpdateAndReloadNoop() {
        ConfigChangeEventContext context = mock(ConfigChangeEventContext.class);
        when(context.getReposToUpdate()).thenReturn(Map.of());
        when(context.getReposToReload()).thenReturn(Map.of());

        standardUpdateOrReloadStrategy.updateOrReload(context);

        verifyNoInteractions(repositoryService);
        verifyNoInteractions(repositoryService);
        verifyNoInteractions(repoConfigChangeListener);
    }

    @Test
    void updateRepoConfigs() {
        ConfigChangeEventContext context = mock(ConfigChangeEventContext.class);
        LocalRepoConfig repoConfigToUpdate = LocalRepoConfig.create("repoKey", RepoType.Generic);
        when(context.getReposToUpdate()).thenReturn(
                Map.of("repoKey", repoConfigToUpdate));
        when(context.getReposToReload()).thenReturn(Map.of());

        standardUpdateOrReloadStrategy.updateOrReload(context);

        verify(repositoryService).bulkUpsertRepoConfigs(List.of(repoConfigToUpdate), true);
        verifyNoInteractions(repoConfigChangeListener);
    }

    @Test
    void reloadRepoConfigs() {
        ConfigChangeEventContext context = mock(ConfigChangeEventContext.class);
        ReferencedConfigChange.ConfigTrigger configTriggerToReload =
                ReferencedConfigChange.ConfigTrigger.REFERENCED_PROXY_CHANGE;
        when(context.getReposToUpdate()).thenReturn(Map.of());
        when(context.getReposToReload()).thenReturn(Map.of("repoKey", Set.of(configTriggerToReload)));

        standardUpdateOrReloadStrategy.updateOrReload(context);

        List<ReferencedConfigChange> referencedConfigChanges =
                List.of(new ReferencedConfigChange("repoKey", Set.of(configTriggerToReload)));
        verify(repositoryService).reloadOnReferencedConfigChange(referencedConfigChanges);
        verify(repoConfigChangeListener).reloadOnReferencedConfigChange(referencedConfigChanges);
    }

    @Test
    void updateAndReloadRepoConfigs() {
        ConfigChangeEventContext context = mock(ConfigChangeEventContext.class);
        ReferencedConfigChange.ConfigTrigger configTriggerToReload =
                ReferencedConfigChange.ConfigTrigger.REFERENCED_PROXY_CHANGE;
        LocalRepoConfig repoConfigToUpdate = LocalRepoConfig.create("repoKey", RepoType.Generic);
        when(context.getReposToUpdate()).thenReturn(
                Map.of("repoKey", repoConfigToUpdate));
        when(context.getReposToReload()).thenReturn(Map.of("repoKey", Set.of(configTriggerToReload)));

        standardUpdateOrReloadStrategy.updateOrReload(context);

        verify(repositoryService).bulkUpsertRepoConfigs(List.of(repoConfigToUpdate), true);
        List<ReferencedConfigChange> referencedConfigChanges =
                List.of(new ReferencedConfigChange("repoKey", Set.of(configTriggerToReload)));
        verify(repositoryService).reloadOnReferencedConfigChange(referencedConfigChanges);
        verify(repoConfigChangeListener).reloadOnReferencedConfigChange(referencedConfigChanges);
    }
}