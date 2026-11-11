package org.artifactory.repo.confighandler.manager;

import org.apache.commons.lang3.SerializationUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.confighandler.ConfigChangeEventContext;
import org.artifactory.repo.confighandler.ConfigChangeEventHandler;
import org.artifactory.repo.confighandler.ConfigChangeWorkItem;
import org.artifactory.repo.confighandler.KeyPairsConfigChangeEventHandler;
import org.artifactory.repo.confighandler.KeyStoreConfigChangeEventHandlerImpl;
import org.artifactory.repo.confighandler.LayoutConfigChangeEventHandler;
import org.artifactory.repo.confighandler.PropertySetsConfigHandler;
import org.artifactory.repo.confighandler.ProxyConfigChangeEventHandler;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.artifactory.repo.confighandler.ConfigChangeType.CREATED;
import static org.artifactory.repo.confighandler.ConfigChangeType.DELETED;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType.ARTIFACTORY_CONFIG_DESCRIPTOR;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType.KEYSTORE_CONFIG_REMOVE_KEY_PAIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
class ConfigChangeEventHandlerManagerImplTest {

    private AutoCloseable mockitoAutoCloseable;
    @Mock
    private UpdateOrReloadStrategy updateOrReloadStrategy;
    @Mock
    private UpdateOrReloadStrategyFactory updateOrReloadStrategyFactory;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private ArtifactoryApplicationContext applicationContext;
    private KeyStoreConfigChangeEventHandlerImpl keyStoreConfigChangeEventHandler;
    private ProxyConfigChangeEventHandler proxyConfigChangeEventHandler;
    private KeyPairsConfigChangeEventHandler keyPairsConfigChangeEventHandler;
    private ConfigChangeEventHandlerManagerImpl configChangeEventHandlerManager;
    private PropertySetsConfigHandler propertySetsConfigHandler;
    private LayoutConfigChangeEventHandler layoutConfigChangeEventHandler;

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        this.keyStoreConfigChangeEventHandler = spy(new KeyStoreConfigChangeEventHandlerImpl(repositoryService));
        this.proxyConfigChangeEventHandler = spy(new ProxyConfigChangeEventHandler());
        this.keyPairsConfigChangeEventHandler = spy(new KeyPairsConfigChangeEventHandler());
        this.propertySetsConfigHandler = spy(new PropertySetsConfigHandler());
        this.layoutConfigChangeEventHandler = spy(new LayoutConfigChangeEventHandler());

        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE)).thenReturn(List.of());
        when(applicationContext.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        List<ConfigChangeEventHandler> configChangeEventHandlers =
                List.of(keyStoreConfigChangeEventHandler, proxyConfigChangeEventHandler,
                        keyPairsConfigChangeEventHandler, propertySetsConfigHandler);
        when(updateOrReloadStrategyFactory.create(any())).thenReturn(updateOrReloadStrategy);
        configChangeEventHandlerManager =
                new ConfigChangeEventHandlerManagerImpl(configChangeEventHandlers, updateOrReloadStrategyFactory);
        configChangeEventHandlerManager.init();
        ArtifactoryContextThreadBinder.bind(applicationContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testHandleConfigChangeEvent() {
        ConfigChangeWorkItem configChangeWorkItem = mock(ConfigChangeWorkItem.class);
        when(configChangeWorkItem.getConfigChangeEvent()).thenReturn(
                new SystemConfigChangeEvent(KEYSTORE_CONFIG_REMOVE_KEY_PAIR, null, null));

        configChangeEventHandlerManager.handle(configChangeWorkItem);

        verify(keyStoreConfigChangeEventHandler, Mockito.times(1)).handle(any(ConfigChangeEventContext.class));
        List.of(proxyConfigChangeEventHandler, keyPairsConfigChangeEventHandler, propertySetsConfigHandler,
                layoutConfigChangeEventHandler).forEach(handler ->
                verify(handler, Mockito.times(0)).handle(any(ConfigChangeEventContext.class)));
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(any(ConfigChangeEventContext.class));

        configChangeEventHandlerManager.handle(configChangeWorkItem);

        verify(keyStoreConfigChangeEventHandler, Mockito.times(2)).handle(any(ConfigChangeEventContext.class));
        List.of(proxyConfigChangeEventHandler, keyPairsConfigChangeEventHandler, propertySetsConfigHandler,
                layoutConfigChangeEventHandler).forEach(handler ->
                verify(handler, Mockito.times(0)).handle(any(ConfigChangeEventContext.class)));
        verify(updateOrReloadStrategy, Mockito.times(2)).updateOrReload(any(ConfigChangeEventContext.class));
    }

    @Test
    void testHandleConfigDeleteChangeEvent() {
        ConfigChangeWorkItem configChangeWorkItem = new ConfigChangeWorkItem(createDeleteAndCreateChangeEvent());
        ConfigDescriptorChangeWorkItem changesLeftWorkItem =
                configChangeEventHandlerManager.handleOnlyConfigDescriptorDeleteChange(configChangeWorkItem);

        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());
        assertNotNull(captor.getValue().getDescriptorChanges().get(keyPairsConfigChangeEventHandler).get(DELETED));
        assertNull(captor.getValue().getDescriptorChanges().get(propertySetsConfigHandler));
        List.of(proxyConfigChangeEventHandler, keyStoreConfigChangeEventHandler, layoutConfigChangeEventHandler)
                .forEach(handler -> verify(handler, Mockito.times(0)).handle(any(ConfigChangeEventContext.class)));

        assertFalse(changesLeftWorkItem.getDescriptorChanges().isEmpty());
        assertNull(changesLeftWorkItem.getDescriptorChanges().get(keyPairsConfigChangeEventHandler));

        var keysOfCreateChange = changesLeftWorkItem.getDescriptorChanges().get(propertySetsConfigHandler).get(CREATED);
        assertNotNull(keysOfCreateChange);
        assertEquals(1, keysOfCreateChange.size());
        assertEquals("foo", keysOfCreateChange.get(0));

        configChangeEventHandlerManager.handle(changesLeftWorkItem);
        verify(propertySetsConfigHandler, Mockito.times(1)).handle(captor.capture());
        assertNotNull(captor.getValue().getDescriptorChanges().get(propertySetsConfigHandler));
        assertNotNull(captor.getValue().getDescriptorChanges().get(propertySetsConfigHandler).get(CREATED));
        verify(updateOrReloadStrategy, Mockito.times(2)).updateOrReload(any());
    }

    private SystemConfigChangeEvent createDeleteAndCreateChangeEvent() {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();
        oldDescriptor.setPropertySets(List.of());
        KeyPairDescriptor primaryKeyPairDescriptor = new KeyPairDescriptor();
        primaryKeyPairDescriptor.setKey("primaryKeyPair");
        oldDescriptor.addKeyPair(primaryKeyPairDescriptor);

        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);
        // property set change of type create
        PropertySet propertySet = new PropertySet();
        propertySet.setName("foo");
        newDescriptor.setPropertySets(List.of(propertySet));
        //key pair change of type delete
        newDescriptor.removeKeyPair(primaryKeyPairDescriptor.getKey());

        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }
}