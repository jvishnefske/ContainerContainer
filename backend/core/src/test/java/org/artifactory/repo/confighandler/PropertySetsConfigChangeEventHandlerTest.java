package org.artifactory.repo.confighandler;

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.SerializationUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.property.PredefinedValue;
import org.artifactory.descriptor.property.Property;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType.ARTIFACTORY_CONFIG_DESCRIPTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Uriah Levy
 */
class PropertySetsConfigChangeEventHandlerTest {

    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private ArtifactoryApplicationContext applicationContext;
    @Mock
    private UpdateOrReloadStrategyFactory updateOrReloadStrategyFactory;
    private UpdateOrReloadStrategy updateOrReloadStrategy;
    private AutoCloseable mockitoAutoCloseable;

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        when(applicationContext.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        updateOrReloadStrategy = mock(UpdateOrReloadStrategy.class);
        when(updateOrReloadStrategyFactory.create(any())).thenReturn(updateOrReloadStrategy);
        ArtifactoryContextThreadBinder.bind(applicationContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testDeletedPropertySetIsDereferenced() {
        ConfigChangeEventHandlerManager configChangeEventHandlerManager = new ConfigChangeEventHandlerManagerImpl(
                List.of(new PropertySetsConfigHandler()), updateOrReloadStrategyFactory);
        configChangeEventHandlerManager.init();

        PropertySet propertySet = new PropertySet();
        propertySet.setName("a-prop-set");

        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo-with-property-set", RepoType.Maven);
        localRepoConfig.getRepoTypeConfig().setPropertySetRefs(List.of(propertySet.getName()));
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote-repo-with-property-set", RepoType.Docker);
        remoteRepoConfig.getRepoTypeConfig().setPropertySetRefs(List.of(propertySet.getName()));
        LocalRepoConfig anotherLocalRepoConfigWithoutRef =
                LocalRepoConfig.create("local-repo-without-property-set", RepoType.Maven);

        SystemConfigChangeEvent systemConfigChangeEvent = createSimplePropertySetRemovedEvent(propertySet);

        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)).thenReturn(
                List.of(localRepoConfig, anotherLocalRepoConfigWithoutRef));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE)).thenReturn(
                List.of(remoteRepoConfig));

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(systemConfigChangeEvent));

        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());
        assertEquals(0, captor.getValue().getReposToReload().size());
        assertEquals(2, captor.getValue().getReposToUpdate().size());

        Map<String, RepoConfig> reposToUpdate = captor.getValue().getReposToUpdate();

        LocalRepoConfig updatedLocalRepoConfig =
                reposToUpdate.values().stream()
                        .filter(repoConfig -> repoConfig.getKey().equals(localRepoConfig.getKey())).findAny()
                        .map(LocalRepoConfig.class::cast)
                        .orElseThrow();
        // The prop ref was removed
        assertTrue(updatedLocalRepoConfig.getRepoTypeConfig().getPropertySetRefs().isEmpty());

        RemoteRepoConfig updatedRemoteRepoConfig =
                reposToUpdate.values().stream()
                        .filter(repoConfig -> repoConfig.getKey().equals(remoteRepoConfig.getKey())).findAny()
                        .map(RemoteRepoConfig.class::cast)
                        .orElseThrow();
        // The prop ref was removed
        assertTrue(updatedRemoteRepoConfig.getRepoTypeConfig().getPropertySetRefs().isEmpty());
    }


    @Test
    void testRepoMarkedForReloadAfterPropertySetChange() {
        ConfigChangeEventHandlerManager configChangeEventHandlerManager = new ConfigChangeEventHandlerManagerImpl(
                List.of(new PropertySetsConfigHandler()), updateOrReloadStrategyFactory);
        configChangeEventHandlerManager.init();

        PropertySet propertySet = new PropertySet();
        propertySet.setName("foo");

        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo-with-property-set", RepoType.Maven);
        localRepoConfig.getRepoTypeConfig().setPropertySetRefs(List.of(propertySet.getName()));
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote-repo-with-property-set", RepoType.Docker);
        remoteRepoConfig.getRepoTypeConfig().setPropertySetRefs(List.of(propertySet.getName()));
        LocalRepoConfig anotherLocalRepoConfigWithoutRef =
                LocalRepoConfig.create("local-repo-without-property-set", RepoType.Maven);

        SystemConfigChangeEvent systemConfigChangeEvent = createSimplePropertySetChangedEvent(propertySet);

        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)).thenReturn(
                List.of(localRepoConfig, anotherLocalRepoConfigWithoutRef));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE)).thenReturn(
                List.of(remoteRepoConfig));

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(systemConfigChangeEvent));
        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());
        assertEquals(2, captor.getValue().getReposToReload().size());
        assertEquals(0, captor.getValue().getReposToUpdate().size());

        Map<String, Set<ReferencedConfigChange.ConfigTrigger>> expectedReposMarkedForReload = Map.of(
                localRepoConfig.getKey(),
                Set.of(ReferencedConfigChange.ConfigTrigger.REFERENCED_PROPERTY_SET_CHANGE),
                remoteRepoConfig.getKey(),
                Set.of(ReferencedConfigChange.ConfigTrigger.REFERENCED_PROPERTY_SET_CHANGE)
        );

        assertEquals(captor.getValue().getReposToReload(), expectedReposMarkedForReload);
    }

    @Test
    void testNestedPropertySetChangeDeferredForReloadOrUpdate() {
        ConfigChangeEventHandlerManager configChangeEventHandlerManager = new ConfigChangeEventHandlerManagerImpl(
                List.of(new PropertySetsConfigHandler()), updateOrReloadStrategyFactory);
        configChangeEventHandlerManager.init();

        PropertySet propertySet = new PropertySet();
        propertySet.setName("foo");

        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo-with-property-set", RepoType.Maven);
        localRepoConfig.getRepoTypeConfig().setPropertySetRefs(List.of(propertySet.getName()));
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote-repo-with-property-set", RepoType.Docker);
        remoteRepoConfig.getRepoTypeConfig().setPropertySetRefs(List.of(propertySet.getName()));
        LocalRepoConfig anotherLocalRepoConfigWithoutRef =
                LocalRepoConfig.create("local-repo-without-property-set", RepoType.Maven);

        SystemConfigChangeEvent systemConfigChangeEvent = createNewPropertySetAddedEvent(propertySet);

        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)).thenReturn(
                List.of(localRepoConfig, anotherLocalRepoConfigWithoutRef));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE)).thenReturn(
                List.of(remoteRepoConfig));

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(systemConfigChangeEvent));

        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());
        // No update done since this is a branch new property set
        assertEquals(0, captor.getValue().getReposToUpdate().size());
    }

    @Nonnull
    private SystemConfigChangeEvent createSimplePropertySetRemovedEvent(PropertySet propertySet) {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();
        oldDescriptor.setPropertySets(List.of(propertySet));

        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);
        newDescriptor.setPropertySets(List.of());

        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }

    @Nonnull
    private SystemConfigChangeEvent createSimplePropertySetChangedEvent(PropertySet propertySet) {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();
        oldDescriptor.setPropertySets(List.of(propertySet));

        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);
        PropertySet mutatedSet = new PropertySet();
        mutatedSet.setName(propertySet.getName());
        Property aNewProp = new Property();
        aNewProp.setName("newProp");
        aNewProp.setPredefinedValues(List.of(new PredefinedValue("foo", true)));
        mutatedSet.addProperty(aNewProp);
        newDescriptor.setPropertySets(List.of(mutatedSet));

        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }

    @Nonnull
    private SystemConfigChangeEvent createNewPropertySetAddedEvent(PropertySet propertySet) {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();
        oldDescriptor.setPropertySets(List.of());

        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);
        newDescriptor.setPropertySets(List.of(propertySet));

        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }
}