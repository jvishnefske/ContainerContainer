package org.artifactory.repo.confighandler;

import org.apache.commons.lang3.SerializationUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.repo.ReferencedConfigChange;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManager;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManagerImpl;
import org.artifactory.repo.confighandler.manager.UpdateOrReloadStrategy;
import org.artifactory.repo.confighandler.manager.UpdateOrReloadStrategyFactory;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType.ARTIFACTORY_CONFIG_DESCRIPTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tamar Rosen
 */
class BaseUrlConfigChangeEventHandlerTest {
    private static final String NEW_BASE_URL = "https://test-new:8082";
    private static final String OLD_BASE_URL = "https://test-old:8082";
    private AutoCloseable mockitoClosable;

    @Mock
    private ArtifactoryContext context;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private UpdateOrReloadStrategy updateOrReloadStrategy;
    @Mock
    private UpdateOrReloadStrategyFactory updateOrReloadStrategyFactory;

    @BeforeEach
    void setup() {
        this.mockitoClosable = MockitoAnnotations.openMocks(this);
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

    @ParameterizedTest
    @MethodSource("provideBaseUrlEvent")
    void testChangeBaseUrlMarksRepositoriesForReload(SystemConfigChangeEvent event) {
        ConfigChangeEventHandlerManager configChangeEventHandlerManager = new ConfigChangeEventHandlerManagerImpl(
                List.of(new BaseUrlConfigChangeEventHandler()), updateOrReloadStrategyFactory);
        configChangeEventHandlerManager.init();

        FederatedRepoConfig fedRepo = FederatedRepoConfig.create("fed-repo", RepoType.NuGet);
        when(repositoryConfigService.getRepoConfigsByType(FEDERATED)).thenReturn(List.of(fedRepo));

        configChangeEventHandlerManager.handle(new ConfigChangeWorkItem(event));

        Map<String, Set<ReferencedConfigChange.ConfigTrigger>> expectedReloadedRepos = Map.of(
                fedRepo.getKey(),
                Set.of(ReferencedConfigChange.ConfigTrigger.REFERENCED_URL_BASE_CHANGE)
        );

        ArgumentCaptor<ConfigChangeEventContext> captor = ArgumentCaptor.forClass(ConfigChangeEventContext.class);
        verify(updateOrReloadStrategy, Mockito.times(1)).updateOrReload(captor.capture());
        assertEquals(expectedReloadedRepos, captor.getValue().getReposToReload());
    }

    public static Stream<Arguments> provideBaseUrlEvent() {
        return Stream.of(
                Arguments.of(createNewBaseUrlEvent(false)),
                Arguments.of(createNewBaseUrlEvent(true)),
                Arguments.of(createChangedBaseUrlEvent(false)),
                Arguments.of(createChangedBaseUrlEvent(true)),
                Arguments.of(createRemovedBaseUrlEvent(false)),
                Arguments.of(createRemovedBaseUrlEvent(true))
        );
    }

    private static SystemConfigChangeEvent createNewBaseUrlEvent(boolean fedBaseUrl) {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();
        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);
        if (fedBaseUrl) {
            newDescriptor.setFederatedRepoUrlBase(NEW_BASE_URL);
        } else {
            newDescriptor.setUrlBase(NEW_BASE_URL);
        }
        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }

    private static SystemConfigChangeEvent createChangedBaseUrlEvent(boolean fedBaseUrl) {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();
        if (fedBaseUrl) {
            oldDescriptor.setFederatedRepoUrlBase(OLD_BASE_URL);
        } else {
            oldDescriptor.setUrlBase(OLD_BASE_URL);
        }
        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);
        if (fedBaseUrl) {
            newDescriptor.setFederatedRepoUrlBase(NEW_BASE_URL);
        } else {
            newDescriptor.setUrlBase(NEW_BASE_URL);
        }
        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }

    private static SystemConfigChangeEvent createRemovedBaseUrlEvent(boolean fedBaseUrl) {
        CentralConfigDescriptorImpl oldDescriptor = new CentralConfigDescriptorImpl();
        if (fedBaseUrl) {
            oldDescriptor.setFederatedRepoUrlBase(OLD_BASE_URL);
        } else {
            oldDescriptor.setUrlBase(OLD_BASE_URL);
        }
        CentralConfigDescriptorImpl newDescriptor = SerializationUtils.clone(oldDescriptor);
        if (fedBaseUrl) {
            oldDescriptor.setFederatedRepoUrlBase(null);
        } else {
            oldDescriptor.setUrlBase(null);
        }
        return new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
    }
}