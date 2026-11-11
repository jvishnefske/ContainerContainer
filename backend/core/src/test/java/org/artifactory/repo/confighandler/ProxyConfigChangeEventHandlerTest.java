package org.artifactory.repo.confighandler;

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.SerializationUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.ReferencedConfigChange;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManager;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManagerImpl;
import org.artifactory.repo.confighandler.manager.UpdateOrReloadStrategy;
import org.artifactory.repo.confighandler.manager.UpdateOrReloadStrategyFactory;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType.ARTIFACTORY_CONFIG_DESCRIPTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author shayb
 */
class ProxyConfigChangeEventHandlerTest {
    private AutoCloseable mockitoClosable;
    private RemoteRepoConfig remoteProxyChange;
    private RemoteRepoConfig remoteProxyDeleted;
    private FederatedRepoConfig fedProxyChange;
    private FederatedRepoConfig fedProxyDeleted;
    private List<RepoConfig> allRepos;
    private UpdateOrReloadStrategy updateOrReloadStrategy;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private UpdateOrReloadStrategyFactory updateOrReloadStrategyFactory;
    @Mock
    private ArtifactoryApplicationContext context;

    public static Stream<Arguments> provideDefaultProxyEvent() {
        return Stream.of(
                Arguments.of(createNewDefaultProxyEvent()),
                Arguments.of(createChangedDefaultProxyEvent()),
                Arguments.of(createRemovedDefaultProxyEvent())
        );
    }

    @BeforeEach
    void setup() {
        this.mockitoClosable = MockitoAnnotations.openMocks(this);
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.Maven);
        RemoteRepoConfig remoteNoProxy = RemoteRepoConfig.create("remote-should-have-no-proxy", RepoType.Maven);
        remoteProxyChange = RemoteRepoConfig.create("remote-should-have-proxy-change", RepoType.Maven);
        remoteProxyDeleted = RemoteRepoConfig.create("remote-should-have-proxy-deleted", RepoType.Maven);
        fedProxyChange = FederatedRepoConfig.create("federated-should-have-proxy-change", RepoType.Maven);
        fedProxyDeleted = FederatedRepoConfig.create("federated-should-have-proxy-deleted", RepoType.Maven);
        allRepos = List.of(
                localRepoConfig, remoteNoProxy, remoteProxyChange, remoteProxyDeleted, fedProxyChange, fedProxyDeleted);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        updateOrReloadStrategy = mock(UpdateOrReloadStrategy.class);
        when(updateOrReloadStrategyFactory.create(any())).thenReturn(updateOrReloadStrategy);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void cleanup() throws Exception {
        this.mockitoClosable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testProxyConfigEvent() {
        ConfigChangeEventHandlerManager configChangeEventHandlerManager = new ConfigChangeEventHandlerManagerImpl(
                List.of(new ProxyConfigChangeEventHandler()), updateOrReloadStrategyFactory);
        configChangeEventHandlerManager.init();
        String proxyKeyToChange = "my-proxy";
        String proxyKeyToDelete = "my-proxy2";
        String proxyKeyToCreate = "my-proxy3";
        SystemConfigChangeEvent systemConfigChangeEvent =
                prepareConfigEvent(proxyKeyToChange, proxyKeyToDelete, proxyKeyToCreate);
        remoteProxyChange.getRepoTypeConfig().setProxyRef(proxyKeyToChange);
        remoteProxyDeleted.getRepoTypeConfig().setProxyRef(proxyKeyToDelete);
        fedProxyChange.getRepoTypeConfig().setProxyRef(proxyKeyToChange);
        fedProxyDeleted.getRepoTypeConfig().setProxyRef(proxyKeyToDelete);

        when(repositoryConfigService.getAllRepoConfigs()).thenReturn(allRepos);

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(systemConfigChangeEvent));

        Map<String, Set<ReferencedConfigChange.ConfigTrigger>> expectedReloadedRepos = Map.of(
                remoteProxyChange.getKey(),
                Set.of(ReferencedConfigChange.ConfigTrigger.REFERENCED_PROXY_CHANGE),
                fedProxyChange.getKey(),
                Set.of(ReferencedConfigChange.ConfigTrigger.REFERENCED_PROXY_CHANGE)
        );

        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());
        assertEquals(expectedReloadedRepos, captor.getValue().getReposToReload());

        // validate only repos with proxy deletions marked to be updated with `null` as new proxy ref
        Map<String, RepoConfig> reposToUpdate = captor.getValue().getReposToUpdate();
        assertEquals(2, reposToUpdate.size());
        RemoteRepoConfig remoteRepoConfig =
                reposToUpdate.values().stream().filter(repoConfig -> repoConfig.getKey()
                                .equals(remoteProxyDeleted.getKey())).findAny()
                        .map(RemoteRepoConfig.class::cast)
                        .orElseThrow();
        assertNull(remoteRepoConfig.getRepoTypeConfig().getProxyRef());
        FederatedRepoConfig fedRepoConfig =
                reposToUpdate.values().stream().filter(repoConfig -> repoConfig.getKey()
                                .equals(fedProxyDeleted.getKey())).findAny()
                        .map(FederatedRepoConfig.class::cast)
                        .orElseThrow();
        assertNull(fedRepoConfig.getRepoTypeConfig().getProxyRef());
    }

    @ParameterizedTest
    @MethodSource("provideDefaultProxyEvent")
    void testChangeDefaultProxyMarksRepositoriesForReload(SystemConfigChangeEvent event) {
        ConfigChangeEventHandlerManager configChangeEventHandlerManager = new ConfigChangeEventHandlerManagerImpl(
                List.of(new ProxyConfigChangeEventHandler()), updateOrReloadStrategyFactory);
        configChangeEventHandlerManager.init();

        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote-repo-without-proxy", RepoType.NuGet);
        RemoteRepoConfig remoteRepoConfigWithProxyRef =
                RemoteRepoConfig.create("remote-repo-with-proxy", RepoType.NuGet);
        remoteRepoConfigWithProxyRef.getRepoTypeConfig().setProxyRef("my-proxy");
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("fed-repo-without-proxy", RepoType.Docker);

        when(repositoryConfigService.getAllRepoConfigs())
                .thenReturn(Set.of(remoteRepoConfig, remoteRepoConfigWithProxyRef, federatedRepoConfig));

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(event));

        // validate only repos containing no proxy ref were marked for reload
        Map<String, Set<ReferencedConfigChange.ConfigTrigger>> expectedReloadedRepos = Map.of(
                federatedRepoConfig.getKey(),
                Set.of(ReferencedConfigChange.ConfigTrigger.REFERENCED_PROXY_CHANGE),
                remoteRepoConfig.getKey(),
                Set.of(ReferencedConfigChange.ConfigTrigger.REFERENCED_PROXY_CHANGE)
        );

        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());
        assertEquals(expectedReloadedRepos, captor.getValue().getReposToReload());
    }

    @ParameterizedTest
    @MethodSource("provideDefaultProxyEvent")
    void testChangeDefaultProxyMarksRepositoriesForReloadIgnoredProxyDisabled(SystemConfigChangeEvent event) {
        ConfigChangeEventHandlerManager configChangeEventHandlerManager = new ConfigChangeEventHandlerManagerImpl(
                List.of(new ProxyConfigChangeEventHandler()), updateOrReloadStrategyFactory);
        configChangeEventHandlerManager.init();

        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote-repo-without-proxy", RepoType.NuGet);
        remoteRepoConfig.setDisableProxy(true);
        RemoteRepoConfig remoteRepoConfigWithProxyRef =
                RemoteRepoConfig.create("remote-repo-with-proxy", RepoType.NuGet);
        remoteRepoConfigWithProxyRef.getRepoTypeConfig().setProxyRef("my-proxy");
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("fed-repo-without-proxy", RepoType.Docker);
        federatedRepoConfig.setDisableProxy(true);

        when(repositoryConfigService.getAllRepoConfigs())
                .thenReturn(Set.of(remoteRepoConfig, remoteRepoConfigWithProxyRef, federatedRepoConfig));

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(event));

        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());

        // validate no repos were marked for reload since they were either flagged as proxy disabled or had a
        // non-null proxy ref
        assertEquals(Map.of(), captor.getValue().getReposToReload());
    }

    @Nonnull
    private SystemConfigChangeEvent prepareConfigEvent(String proxyKeyToChange, String proxyKeyToDelete,
            String proxyKeyToCreate) {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();
        // prepare old descriptor
        ProxyDescriptor proxyDescriptor = new ProxyDescriptor();
        proxyDescriptor.setKey(proxyKeyToDelete);
        proxyDescriptor.setHost("my-proxy.jfrog.io");
        proxyDescriptor.setPort(8888);
        ProxyDescriptor proxyDescriptor2 = new ProxyDescriptor();
        proxyDescriptor2.setKey(proxyKeyToChange);
        proxyDescriptor2.setHost("my-proxy-2.jfrog.io");
        proxyDescriptor2.setPort(9999);
        List<ProxyDescriptor> proxies = new ArrayList<>();
        proxies.add(proxyDescriptor);
        proxies.add(proxyDescriptor2);
        oldDescriptor.setProxies(proxies);

        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);
        newDescriptor.removeProxy(proxyKeyToDelete);
        newDescriptor.getProxy(proxyKeyToChange).setHost("new-host-for-proxy.jfrog.io");
        ProxyDescriptor proxyDescriptor3 = new ProxyDescriptor();
        proxyDescriptor3.setKey(proxyKeyToCreate);
        proxyDescriptor3.setHost("irrelevant-host-for-proxy.jfrog.io");
        proxyDescriptor3.setPort(12345);
        newDescriptor.getProxies().add(proxyDescriptor3);

        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }

    private static SystemConfigChangeEvent createNewDefaultProxyEvent() {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();

        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);
        ProxyDescriptor newDefaultProxy = new ProxyDescriptor();
        newDefaultProxy.setPlatformDefault(true);
        newDefaultProxy.setKey("defaultProxy");
        newDescriptor.getProxies().add(newDefaultProxy);

        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }

    private static SystemConfigChangeEvent createChangedDefaultProxyEvent() {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();
        ProxyDescriptor oldDefaultProxy = new ProxyDescriptor();
        oldDefaultProxy.setPlatformDefault(true);
        oldDefaultProxy.setKey("oldDefaultProxy");
        oldDescriptor.getProxies().add(oldDefaultProxy);

        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);
        ProxyDescriptor newDefaultProxy = new ProxyDescriptor();
        newDefaultProxy.setPlatformDefault(true);
        newDefaultProxy.setKey("newDefaultProxy");
        newDescriptor.getProxies().add(newDefaultProxy);

        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }

    private static SystemConfigChangeEvent createRemovedDefaultProxyEvent() {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();
        ProxyDescriptor oldDefaultProxy = new ProxyDescriptor();
        oldDefaultProxy.setPlatformDefault(true);
        oldDefaultProxy.setKey("defaultProxy");
        oldDescriptor.getProxies().add(oldDefaultProxy);

        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);
        newDescriptor.setProxies(List.of());

        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }

}