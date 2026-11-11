package org.artifactory.repo.confighandler;

import org.apache.commons.lang3.SerializationUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.ReferencedConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManager;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManagerImpl;
import org.artifactory.repo.confighandler.manager.UpdateOrReloadStrategy;
import org.artifactory.repo.confighandler.manager.UpdateOrReloadStrategyFactory;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType.ARTIFACTORY_CONFIG_DESCRIPTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author Uriah Levy
 */
class KeyPairsConfigChangeEventHandlerTest {

    @Mock
    private ArtifactoryApplicationContext context;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private UpdateOrReloadStrategyFactory updateOrReloadStrategyFactory;
    @InjectMocks
    private KeyPairsConfigChangeEventHandler keyPairsConfigChangeEventHandler;
    private UpdateOrReloadStrategy updateOrReloadStrategy;
    private AutoCloseable mockitoAutoCloseable;

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        updateOrReloadStrategy = mock(UpdateOrReloadStrategy.class);
        when(updateOrReloadStrategyFactory.create(any())).thenReturn(updateOrReloadStrategy);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testReloadOnKeyPairChange() {
        ConfigChangeEventHandlerManager configChangeEventHandlerManager = new ConfigChangeEventHandlerManagerImpl(
                List.of(keyPairsConfigChangeEventHandler), updateOrReloadStrategyFactory);
        configChangeEventHandlerManager.init();

        KeyPairDescriptor keyPairDescriptor = new KeyPairDescriptor();
        keyPairDescriptor.setKey("a-key-pair");

        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo-with-keypair", RepoType.Debian);
        localRepoConfig.getSecurityConfig().setPrimaryKeyPairRef(keyPairDescriptor.getKey());
        FederatedRepoConfig federatedRepoConfig =
                FederatedRepoConfig.create("federated-repo-with-keypair", RepoType.Alpine);
        federatedRepoConfig.getSecurityConfig().setPrimaryKeyPairRef(keyPairDescriptor.getKey());
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo-with-keypair", RepoType.YUM);
        virtualRepoConfig.getSecurityConfig().setPrimaryKeyPairRef(keyPairDescriptor.getKey());
        LocalRepoConfig anotherLocalRepoConfigWithoutRef =
                LocalRepoConfig.create("local-repo-without-keypair", RepoType.Maven);

        SystemConfigChangeEvent systemConfigChangeEvent = createSimpleKeyPairModifiedEvent(keyPairDescriptor);

        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)).thenReturn(
                List.of(localRepoConfig, anotherLocalRepoConfigWithoutRef));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED)).thenReturn(
                List.of(federatedRepoConfig));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL)).thenReturn(
                List.of(virtualRepoConfig));

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(systemConfigChangeEvent));

        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());

        // verify repos to reload are as expected
        Map<String, Set<ReferencedConfigChange.ConfigTrigger>> expectedReloadedRepos = Map.of(
                localRepoConfig.getKey(),
                Set.of(ReferencedConfigChange.ConfigTrigger.REFERENCED_KEYPAIR_CHANGE),
                federatedRepoConfig.getKey(),
                Set.of(ReferencedConfigChange.ConfigTrigger.REFERENCED_KEYPAIR_CHANGE),
                virtualRepoConfig.getKey(),
                Set.of(ReferencedConfigChange.ConfigTrigger.REFERENCED_KEYPAIR_CHANGE)
        );
        assertEquals(expectedReloadedRepos, captor.getValue().getReposToReload());
    }

    @Test
    void testUpdateOnKeyPairRemoved() {
        ConfigChangeEventHandlerManager configChangeEventHandlerManager = new ConfigChangeEventHandlerManagerImpl(
                List.of(keyPairsConfigChangeEventHandler), updateOrReloadStrategyFactory);
        configChangeEventHandlerManager.init();

        KeyPairDescriptor secondaryKeyPairDescriptor = new KeyPairDescriptor();
        secondaryKeyPairDescriptor.setKey("secondaryKeyPair");
        KeyPairDescriptor primaryKeyPairDescriptor = new KeyPairDescriptor();
        primaryKeyPairDescriptor.setKey("primaryKeyPair");

        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo-with-keypair", RepoType.Debian);
        localRepoConfig.getSecurityConfig().setPrimaryKeyPairRef(primaryKeyPairDescriptor.getKey());
        localRepoConfig.getSecurityConfig().setSecondaryKeyPairRef(secondaryKeyPairDescriptor.getKey());

        FederatedRepoConfig federatedRepoConfig =
                FederatedRepoConfig.create("federated-repo-with-keypair", RepoType.Alpine);
        federatedRepoConfig.getSecurityConfig().setPrimaryKeyPairRef(primaryKeyPairDescriptor.getKey());
        federatedRepoConfig.getSecurityConfig().setSecondaryKeyPairRef(secondaryKeyPairDescriptor.getKey());

        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo-with-keypair", RepoType.YUM);
        virtualRepoConfig.getSecurityConfig().setPrimaryKeyPairRef(primaryKeyPairDescriptor.getKey());
        virtualRepoConfig.getSecurityConfig().setSecondaryKeyPairRef(secondaryKeyPairDescriptor.getKey());

        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)).thenReturn(
                List.of(localRepoConfig));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED)).thenReturn(
                List.of(federatedRepoConfig));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL)).thenReturn(
                List.of(virtualRepoConfig));

        SystemConfigChangeEvent systemConfigChangeEvent =
                createKeyPairRemovedEvent(primaryKeyPairDescriptor, secondaryKeyPairDescriptor);

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(systemConfigChangeEvent));

        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());

        Map<String, RepoConfig> reposToUpdate = captor.getValue().getReposToUpdate();
        assertEquals(3, reposToUpdate.size());
        // The secondary key referenced was promoted to primary
        assertTrue(reposToUpdate.values().stream().allMatch(
                config ->
                        secondaryKeyPairDescriptor.getKey().equals(config.getSecurityConfig().getPrimaryKeyPairRef()) &&
                                config.getSecurityConfig().getSecondaryKeyPairRef() == null));
    }

    private SystemConfigChangeEvent createSimpleKeyPairModifiedEvent(KeyPairDescriptor keyPairDescriptor) {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();
        oldDescriptor.addKeyPair(keyPairDescriptor);
        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);

        newDescriptor.removeKeyPair(keyPairDescriptor.getKey());
        KeyPairDescriptor modifiedKeyPair = new KeyPairDescriptor();
        modifiedKeyPair.setKey(keyPairDescriptor.getKey());
        modifiedKeyPair.setPassphrase("1234");
        newDescriptor.addKeyPair(modifiedKeyPair);

        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }

    private SystemConfigChangeEvent createKeyPairRemovedEvent(KeyPairDescriptor primaryKeyPair,
            KeyPairDescriptor secondaryKePair) {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();
        oldDescriptor.addKeyPair(primaryKeyPair);
        oldDescriptor.addKeyPair(secondaryKePair);

        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);

        newDescriptor.removeKeyPair(primaryKeyPair.getKey());

        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }
}