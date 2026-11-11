package org.artifactory.repo.confighandler;

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.SerializationUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.ReferencedConfigChange;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManager;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManagerImpl;
import org.artifactory.repo.confighandler.manager.UpdateOrReloadStrategy;
import org.artifactory.repo.confighandler.manager.UpdateOrReloadStrategyFactory;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;
import org.artifactory.util.RepoLayoutUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType.ARTIFACTORY_CONFIG_DESCRIPTOR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author shayb
 */
class LayoutConfigChangeEventHandlerTest {
    private LocalRepoConfig localRepoNoChange;
    private LocalRepoConfig localRepoLayoutDelete;
    private FederatedRepoConfig fedRepoLayoutChange;
    private RemoteRepoConfig remoteRepoLayoutChange;
    private RemoteRepoConfig remoteRepoLayoutDelete;
    private VirtualRepoConfig virtualRepoLayoutDelete;
    private VirtualRepoConfig virtualRepoLayoutChange;
    private List<RepoConfig> allRepos;
    private UpdateOrReloadStrategy updateOrReloadStrategy;

    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private ArtifactoryApplicationContext context;
    @Mock
    private UpdateOrReloadStrategyFactory updateOrReloadStrategyFactory;
    private AutoCloseable mockitoClosable;

    @BeforeEach
    void setup() {
        this.mockitoClosable = MockitoAnnotations.openMocks(this);
        localRepoNoChange = LocalRepoConfig.create("l-repo-no-change", RepoType.Maven);
        localRepoLayoutDelete = LocalRepoConfig.create("l-repo-layout-delete", RepoType.Maven);
        fedRepoLayoutChange = FederatedRepoConfig.create("f-repo-layout-change", RepoType.Maven);
        remoteRepoLayoutDelete = RemoteRepoConfig.create("r-repo-layout-delete", RepoType.Maven);
        remoteRepoLayoutChange = RemoteRepoConfig.create("r-repo-layout-change", RepoType.Maven);
        virtualRepoLayoutDelete = VirtualRepoConfig.create("v-repo-layout-delete", RepoType.Maven);
        virtualRepoLayoutChange = VirtualRepoConfig.create("v-repo-layout-change", RepoType.Maven);

        allRepos = List.of(localRepoNoChange, localRepoLayoutDelete, fedRepoLayoutChange, remoteRepoLayoutDelete,
                remoteRepoLayoutChange, virtualRepoLayoutDelete, virtualRepoLayoutChange);

        updateOrReloadStrategy = mock(UpdateOrReloadStrategy.class);
        when(updateOrReloadStrategyFactory.create(any())).thenReturn(updateOrReloadStrategy);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void cleanup() throws Exception {
        this.mockitoClosable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testLayoutConfigEvent() {
        ConfigChangeEventHandlerManager
                configChangeEventHandlerManager = new ConfigChangeEventHandlerManagerImpl(
                List.of(new LayoutConfigChangeEventHandler()), updateOrReloadStrategyFactory);
        configChangeEventHandlerManager.init();
        RepoLayout layoutToCreate = RepoLayoutUtils.MAVEN_2_DEFAULT;
        RepoLayout layoutToDelete = RepoLayoutUtils.SIMPLE_DEFAULT;
        RepoLayout layoutToUpdate = RepoLayoutUtils.IVY_DEFAULT;
        SystemConfigChangeEvent systemConfigChangeEvent =
                prepareConfigEvent(layoutToUpdate, layoutToDelete, layoutToCreate);
        when(repositoryConfigService.getAllRepoConfigs()).thenReturn(allRepos);

        localRepoLayoutDelete.getBaseConfig().setRepoLayoutRef(layoutToDelete.getName());
        fedRepoLayoutChange.getBaseConfig().setRepoLayoutRef(layoutToUpdate.getName());
        remoteRepoLayoutDelete.getBaseConfig().setRepoLayoutRef(layoutToDelete.getName());
        remoteRepoLayoutDelete.getRepoTypeConfig().setRemoteRepoLayoutRef(layoutToDelete.getName());
        remoteRepoLayoutChange.getBaseConfig().setRepoLayoutRef(layoutToUpdate.getName());
        virtualRepoLayoutChange.getBaseConfig().setRepoLayoutRef(layoutToUpdate.getName());
        virtualRepoLayoutDelete.getBaseConfig().setRepoLayoutRef(layoutToDelete.getName());

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(systemConfigChangeEvent));

        // verify repos to reload are as expected
        Map<String, Set<ReferencedConfigChange.ConfigTrigger>> expectedReloadedRepos = Map.of(
                fedRepoLayoutChange.getKey(),
                Set.of(ReferencedConfigChange.ConfigTrigger.REFERENCED_LAYOUT_CHANGE),
                remoteRepoLayoutChange.getKey(),
                Set.of(ReferencedConfigChange.ConfigTrigger.REFERENCED_LAYOUT_CHANGE),
                virtualRepoLayoutChange.getKey(),
                Set.of(ReferencedConfigChange.ConfigTrigger.REFERENCED_LAYOUT_CHANGE)
        );

        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());
        Assertions.assertEquals(captor.getValue().getReposToReload(), expectedReloadedRepos);

        // verify repo to update are as expected
        Map<String, RepoConfig> reposToUpdate = captor.getValue().getReposToUpdate();

        LocalRepoConfig localRepoConfig =
                reposToUpdate.values().stream().filter(repoConfig -> repoConfig.getKey()
                                .equals(localRepoLayoutDelete.getKey())).findAny()
                        .map(LocalRepoConfig.class::cast)
                        .orElseThrow();
        Assertions.assertNotNull(localRepoConfig.getBaseConfig().getRepoLayoutRef());
        Assertions.assertEquals(RepoLayoutUtils.MAVEN_2_DEFAULT_NAME,
                localRepoConfig.getBaseConfig().getRepoLayoutRef());

        RemoteRepoConfig remoteRepoConfig = reposToUpdate.values().stream().filter(repoConfig -> repoConfig.getKey()
                        .equals(remoteRepoLayoutDelete.getKey())).findAny()
                .map(RemoteRepoConfig.class::cast)
                .orElseThrow();
        Assertions.assertNotNull(remoteRepoConfig.getBaseConfig().getRepoLayoutRef());
        Assertions.assertNull(remoteRepoConfig.getRepoTypeConfig().getRemoteRepoLayoutRef());

        VirtualRepoConfig virtualRepoConfig =
                reposToUpdate.values().stream().filter(repoConfig -> repoConfig.getKey()
                                .equals(virtualRepoLayoutDelete.getKey())).findAny()
                        .map(VirtualRepoConfig.class::cast)
                        .orElseThrow();
        Assertions.assertNull(virtualRepoConfig.getBaseConfig().getRepoLayoutRef());
    }

    @Nonnull
    private SystemConfigChangeEvent prepareConfigEvent(RepoLayout layoutToUpdate, RepoLayout layoutToDelete,
            RepoLayout layoutToCreate) {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();
        oldDescriptor.setRepoLayouts(Arrays.asList(layoutToUpdate, layoutToDelete));

        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);
        newDescriptor.setRepoLayouts(
                newDescriptor.getRepoLayouts().stream()
                        .filter(o -> !layoutToDelete.equals(o))
                        .collect(Collectors.toList())
        );
        newDescriptor.addRepoLayout(layoutToCreate);
        newDescriptor.getRepoLayout(layoutToUpdate.getName()).setArtifactPathPattern("*.*.jar");

        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }
}