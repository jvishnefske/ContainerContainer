package org.artifactory.ui.rest.service.onboarding;

import com.google.common.collect.Lists;
import junit.framework.Assert;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.addon.OssAddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.onboarding.OnboardingRepoState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.ui.rest.model.onboarding.OnboardingRepoState.ALREADY_SET;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author dudim
 */
public class GetUnsetReposServiceTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactoryContext artifactoryContext;

    @Mock
    ArtifactorySystemProperties artifactorySystemProperties;

    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);

        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.aieditorExtensionsRepositoryEnabled))
                .thenReturn(true);
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.jetbrainsPluginsRepositoryEnabled))
                .thenReturn(true);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    public void cleanUp() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testGetSupportedRepoTypesMapJcrTypeWithoutConan() {
        OssAddonsManager addonsManagerSpy = spy(new OssAddonsManager());
        CentralConfigService configService = mock(CentralConfigService.class);
        MutableCentralConfigDescriptor descriptor = mock(MutableCentralConfigDescriptor.class);
        when(descriptor.getUrlBase()).thenReturn(null);
        when(configService.getMutableDescriptor()).thenReturn(descriptor);
        GetUnsetReposService getUnsetReposService =
                new GetUnsetReposService(null, addonsManagerSpy, configService, null);
        doReturn(ArtifactoryRunningMode.JCR).when(addonsManagerSpy).getArtifactoryRunningMode();
        Map<RepoType, OnboardingRepoState> supportedRepoTypesMap = getUnsetReposService.getSupportedRepoTypesMap();
        Assertions.assertEquals(supportedRepoTypesMap.get(RepoType.Generic), OnboardingRepoState.UNSET);
        Assertions.assertEquals(supportedRepoTypesMap.get(RepoType.Docker), OnboardingRepoState.UNSET);
        Assertions.assertEquals(supportedRepoTypesMap.get(RepoType.Helm), OnboardingRepoState.UNSET);
        Assertions.assertFalse(supportedRepoTypesMap.containsKey(RepoType.Cargo));
        List<RepoType> repoTypesList = new ArrayList<>(Arrays.asList(RepoType.values()));
        repoTypesList.removeAll(Lists.newArrayList(RepoType.Helm, RepoType.Docker, RepoType.Generic));
        repoTypesList.forEach(repoType -> Assertions.assertNotEquals(repoType, OnboardingRepoState.UNSET));
    }

    @ParameterizedTest
    @MethodSource("unshownRepoTypes")
    public void testGetSupportedRepoTypesMapJcrTypeWithoutTypes(RepoType repoType) {
        OssAddonsManager addonsManagerSpy = spy(new OssAddonsManager());
        CentralConfigService configService = mock(CentralConfigService.class);
        MutableCentralConfigDescriptor descriptor = mock(MutableCentralConfigDescriptor.class);
        when(descriptor.getUrlBase()).thenReturn(null);
        when(configService.getMutableDescriptor()).thenReturn(descriptor);
        GetUnsetReposService getUnsetReposService =
                new GetUnsetReposService(null, addonsManagerSpy, configService, null);
        doReturn(ArtifactoryRunningMode.JCR).when(addonsManagerSpy).getArtifactoryRunningMode();
        Map<RepoType, OnboardingRepoState> supportedRepoTypesMap = getUnsetReposService.getSupportedRepoTypesMap();
        Assertions.assertEquals(supportedRepoTypesMap.get(RepoType.Generic), OnboardingRepoState.UNSET);
        Assertions.assertEquals(supportedRepoTypesMap.get(RepoType.Docker), OnboardingRepoState.UNSET);
        Assertions.assertEquals(supportedRepoTypesMap.get(RepoType.Helm), OnboardingRepoState.UNSET);
        Assertions.assertNull(supportedRepoTypesMap.get(repoType));
        List<RepoType> repoTypesList = new ArrayList<>(Arrays.asList(RepoType.values()));
        repoTypesList.removeAll(Lists.newArrayList(RepoType.Helm, RepoType.Docker, RepoType.Generic));
        repoTypesList.forEach(currRepoType -> Assertions.assertNotEquals(currRepoType, OnboardingRepoState.UNSET));
    }

    @Test
    public void testGetSupportedRepoTypesMapJcrTypeWithConan() {
        OssAddonsManager addonsManagerSpy = spy(new OssAddonsManager());
        CentralConfigService configService = mock(CentralConfigService.class);
        MutableCentralConfigDescriptor descriptor = mock(MutableCentralConfigDescriptor.class);
        when(descriptor.getUrlBase()).thenReturn("http://localhost:8082");
        when(configService.getMutableDescriptor()).thenReturn(descriptor);
        GetUnsetReposService getUnsetReposService =
                new GetUnsetReposService(null, addonsManagerSpy, configService, null);
        doReturn(ArtifactoryRunningMode.JCR).when(addonsManagerSpy).getArtifactoryRunningMode();
        Map<RepoType, OnboardingRepoState> supportedRepoTypesMap = getUnsetReposService.getSupportedRepoTypesMap();
        Assertions.assertEquals(supportedRepoTypesMap.get(RepoType.Generic), OnboardingRepoState.UNSET);
        Assertions.assertEquals(supportedRepoTypesMap.get(RepoType.Docker), OnboardingRepoState.UNSET);
        Assertions.assertEquals(supportedRepoTypesMap.get(RepoType.Helm), OnboardingRepoState.UNSET);
        Assertions.assertTrue(supportedRepoTypesMap.containsKey(RepoType.Cargo));
        Assertions.assertFalse(supportedRepoTypesMap.containsKey(RepoType.TerraformBackend));
        List<RepoType> repoTypesList = new ArrayList<>(Arrays.asList(RepoType.values()));
        repoTypesList.removeAll(Lists.newArrayList(RepoType.Helm, RepoType.Docker, RepoType.Generic));
        repoTypesList.forEach(repoType -> Assertions.assertNotEquals(repoType, OnboardingRepoState.UNSET));
    }

    @Test
    public void testGetSupportedRepoTypesMapConanType() {
        OssAddonsManager addonsManagerSpy = spy(new OssAddonsManager());
        CentralConfigService configService = mock(CentralConfigService.class);
        MutableCentralConfigDescriptor descriptor = mock(MutableCentralConfigDescriptor.class);
        when(configService.getMutableDescriptor()).thenReturn(descriptor);
        GetUnsetReposService getUnsetReposService =
                new GetUnsetReposService(null, addonsManagerSpy, configService, null);
        doReturn(ArtifactoryRunningMode.CONAN).when(addonsManagerSpy).getArtifactoryRunningMode();
        Map<RepoType, OnboardingRepoState> supportedRepoTypesMap = getUnsetReposService.getSupportedRepoTypesMap();
        Assertions.assertEquals(supportedRepoTypesMap.get(RepoType.Conan), OnboardingRepoState.UNSET);
        List<RepoType> repoTypesList = new ArrayList<>(Arrays.asList(RepoType.values()));
        repoTypesList.removeAll(Lists.newArrayList(RepoType.Conan));
        repoTypesList.forEach(repoType -> Assertions.assertNotEquals(repoType, OnboardingRepoState.UNSET));
    }

    @Test
    public void testFilterRepoTypesMap() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos()).thenReturn(
                List.of(LocalRepoConfig.create("local", RepoType.Docker)));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED)).thenReturn(List.of(
                FederatedRepoConfig.create("fed", RepoType.Npm)));
        Map<RepoType, OnboardingRepoState> repoStateMap = new HashMap<>();
        repoStateMap.put(RepoType.Docker, OnboardingRepoState.UNAVAILABLE);
        repoStateMap.put(RepoType.Npm, OnboardingRepoState.UNAVAILABLE);
        GetUnsetReposService unsetReposService =
                new GetUnsetReposService(repositoryService, null, null, repositoryConfigService);
        unsetReposService.filterRepoTypesMap(repoStateMap);
        Assertions.assertEquals(repoStateMap.get(RepoType.Docker), ALREADY_SET);
        Assertions.assertEquals(repoStateMap.get(RepoType.Npm), ALREADY_SET);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testGetSupportedRepoTypesMapHexType(boolean isIdePluginsEnabled) {
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.forcePluginsRepositoryEnabled))
                .thenReturn(isIdePluginsEnabled);
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.aieditorExtensionsRepositoryEnabled))
                .thenReturn(isIdePluginsEnabled);
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.jetbrainsPluginsRepositoryEnabled))
                .thenReturn(isIdePluginsEnabled);
        OssAddonsManager addonsManagerSpy = spy(new OssAddonsManager());
        CentralConfigService configService = mock(CentralConfigService.class);
        MutableCentralConfigDescriptor descriptor = mock(MutableCentralConfigDescriptor.class);
        when(configService.getMutableDescriptor()).thenReturn(descriptor);
        doReturn(ArtifactoryRunningMode.CONAN).when(addonsManagerSpy).getArtifactoryRunningMode();
        GetUnsetReposService getUnsetReposService =
                new GetUnsetReposService(null, addonsManagerSpy, configService, null);
        Map<RepoType, OnboardingRepoState> supportedRepoTypesMap = getUnsetReposService.getSupportedRepoTypesMap();
        Assert.assertEquals(supportedRepoTypesMap.get(RepoType.Hex), null);
    }

    public static Object[][] unshownRepoTypes() {
        return new Object[][] {{RepoType.FrogML}, {RepoType.Cargo}};
    }
}