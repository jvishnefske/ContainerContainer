package org.artifactory.repo.confighandler;

import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManager;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManagerImpl;
import org.artifactory.repo.confighandler.manager.UpdateOrReloadStrategy;
import org.artifactory.repo.confighandler.manager.UpdateOrReloadStrategyFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
class KeyStoreConfigChangeEventHandlerImplTest {

    private AutoCloseable mockitoAutoCloseable;
    private UpdateOrReloadStrategy updateOrReloadStrategy;
    private ConfigChangeEventHandlerManager configChangeEventHandlerManager;

    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private ArtifactoryApplicationContext applicationContext;
    @Mock
    private UpdateOrReloadStrategyFactory updateOrReloadStrategyFactory;
    @InjectMocks
    private KeyStoreConfigChangeEventHandlerImpl keyStoreConfigChangeEventHandler;

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        when(applicationContext.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        updateOrReloadStrategy = mock(UpdateOrReloadStrategy.class);
        when(updateOrReloadStrategyFactory.create(any())).thenReturn(updateOrReloadStrategy);
        configChangeEventHandlerManager = new ConfigChangeEventHandlerManagerImpl(
                List.of(keyStoreConfigChangeEventHandler), updateOrReloadStrategyFactory);
        configChangeEventHandlerManager.init();
        ArtifactoryContextThreadBinder.bind(applicationContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void removeKeyPairEventNoReposNoop() {
        String keyPair = "keyPair";
        SystemConfigChangeEvent configChangeEvent = new SystemConfigChangeEvent(
                SystemConfigChangeEvent.SystemConfigChangeEventType.KEYSTORE_CONFIG_REMOVE_KEY_PAIR, keyPair,
                null);

        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE)).thenReturn(List.of());
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL)).thenReturn(List.of());

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(configChangeEvent));

        verifyNoInteractions(repositoryService);
        verify(repositoryConfigService, times(2)).getRepoConfigsByType(any(RepoDetailsType.class));
    }

    @Test
    void removeKeyPairEventNoKeyPairsNoop() {
        SystemConfigChangeEvent configChangeEvent = new SystemConfigChangeEvent(
                SystemConfigChangeEvent.SystemConfigChangeEventType.KEYSTORE_CONFIG_REMOVE_KEY_PAIR, "keyPair",
                null);

        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remoteRepoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setClientTlsCertificate(null);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", RepoType.Generic);
        virtualRepoConfig.getSecurityConfig().setKeyPair(null);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE)).thenReturn(
                List.of(remoteRepoConfig));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL)).thenReturn(
                List.of(virtualRepoConfig));

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(configChangeEvent));

        verifyNoInteractions(repositoryService);
        verify(repositoryConfigService, times(2)).getRepoConfigsByType(any(RepoDetailsType.class));
    }

    @Test
    void removeKeyPairEventKeyPairRemovedFromReposNotHandledByPrevHandlers() {
        SystemConfigChangeEvent configChangeEvent = new SystemConfigChangeEvent(
                SystemConfigChangeEvent.SystemConfigChangeEventType.KEYSTORE_CONFIG_REMOVE_KEY_PAIR,
                ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX + "keyPair",
                null);

        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remoteRepoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setClientTlsCertificate("keyPair");
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", RepoType.Generic);
        virtualRepoConfig.getSecurityConfig().setKeyPair(ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX + "keyPair");

        // Conditions for key pair removal from remote repos
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE)).thenReturn(
                List.of(remoteRepoConfig));

        // Conditions for key pair removal from virtual repos
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL)).thenReturn(
                List.of(virtualRepoConfig));
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(repositoryService.virtualRepositoryByKey("virtualRepoKey")).thenReturn(virtualRepo);
        when(applicationContext.beanForType(KeyStoreConfigChangeEventHandler.class)).thenReturn(
                keyStoreConfigChangeEventHandler);

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(configChangeEvent));

        verify(repositoryService).virtualRepositoryByKey("virtualRepoKey");
        verify(repositoryConfigService, times(2)).getRepoConfigsByType(any(RepoDetailsType.class));
        verify(virtualRepo).undeploy(any(DeleteContext.class));

        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());

        Assertions.assertEquals(2, captor.getValue().getReposToUpdate().size());
        Assertions.assertTrue(captor.getValue().getReposToUpdate().containsKey(remoteRepoConfig.getKey()));
        Assertions.assertTrue(captor.getValue().getReposToUpdate().containsKey(virtualRepoConfig.getKey()));
        Assertions.assertNull(remoteRepoConfig.getRepoTypeConfig().getClientTlsCertificate());
        Assertions.assertNull(virtualRepoConfig.getSecurityConfig().getKeyPair());
    }

    @Test
    void removeKeyPairEventKeyPairRemovedFromReposHandledByPrevHandlers() {
        SystemConfigChangeEvent configChangeEvent = new SystemConfigChangeEvent(
                SystemConfigChangeEvent.SystemConfigChangeEventType.KEYSTORE_CONFIG_REMOVE_KEY_PAIR,
                ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX + "keyPair", null);

        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remoteRepoKey", RepoType.Generic);
        remoteRepoConfig.getBaseConfig().setRepoLayoutRef("newLayoutRef");
        remoteRepoConfig.getRepoTypeConfig().setClientTlsCertificate("keyPair");
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", RepoType.Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("newRepoRef"));
        virtualRepoConfig.getSecurityConfig().setKeyPair(ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX + "keyPair");

        // Conditions for key pair removal from remote repos
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE)).thenReturn(
                List.of(remoteRepoConfig));

        // Conditions for key pair removal from virtual repos
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL)).thenReturn(
                List.of(virtualRepoConfig));
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(repositoryService.virtualRepositoryByKey("virtualRepoKey")).thenReturn(virtualRepo);
        when(applicationContext.beanForType(KeyStoreConfigChangeEventHandler.class)).thenReturn(
                keyStoreConfigChangeEventHandler);

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(configChangeEvent));

        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());
        verify(repositoryService, times(1)).virtualRepositoryByKey("virtualRepoKey");
        verify(repositoryConfigService, times(2)).getRepoConfigsByType(any(RepoDetailsType.class));
        verify((virtualRepo), times(1)).undeploy(any(DeleteContext.class));

        Map<String, RepoConfig> reposToUpdate = captor.getValue().getReposToUpdate();
        Assertions.assertEquals(2, captor.getValue().getReposToUpdate().size());
        Assertions.assertTrue(captor.getValue().getReposToUpdate().containsKey(remoteRepoConfig.getKey()));
        Assertions.assertTrue(captor.getValue().getReposToUpdate().containsKey(virtualRepoConfig.getKey()));

        RemoteRepoConfig remoteRepoConfigFromContext = (RemoteRepoConfig) reposToUpdate.get(remoteRepoConfig.getKey());
        VirtualRepoConfig virtualRepoConfigFromContext =
                (VirtualRepoConfig) reposToUpdate.get(virtualRepoConfig.getKey());

        Assertions.assertNull(remoteRepoConfigFromContext.getRepoTypeConfig().getClientTlsCertificate());
        Assertions.assertNull(virtualRepoConfigFromContext.getSecurityConfig().getKeyPair());

        Assertions.assertEquals("newLayoutRef", remoteRepoConfigFromContext.getBaseConfig().getRepoLayoutRef());
        Assertions.assertNotNull(virtualRepoConfigFromContext.getRepoTypeConfig().getRepositoryRefs());
        Assertions.assertFalse(virtualRepoConfigFromContext.getRepoTypeConfig().getRepositoryRefs().isEmpty());
        Assertions.assertEquals("newRepoRef",
                virtualRepoConfigFromContext.getRepoTypeConfig().getRepositoryRefs().get(0));
    }

    @Test
    void removeKeyStoreEventKeyPairRemovedFromVirtualReposNotHandledByPrevHandlers() {
        SystemConfigChangeEvent configChangeEvent = new SystemConfigChangeEvent(
                SystemConfigChangeEvent.SystemConfigChangeEventType.KEYSTORE_CONFIG_REMOVE_KEY_STORE, "keystore",
                null);

        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", RepoType.Generic);
        virtualRepoConfig.getSecurityConfig().setKeyPair("keyPair");

        // Conditions for key pair removal from virtual repos
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL)).thenReturn(
                List.of(virtualRepoConfig));

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(configChangeEvent));
        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);

        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());
        verify(repositoryConfigService, times(1)).getRepoConfigsByType(any(RepoDetailsType.class));

        Assertions.assertEquals(1, captor.getValue().getReposToUpdate().size());
        Assertions.assertTrue(captor.getValue().getReposToUpdate().containsKey(virtualRepoConfig.getKey()));
        Assertions.assertNull(virtualRepoConfig.getSecurityConfig().getKeyPair());
    }
}