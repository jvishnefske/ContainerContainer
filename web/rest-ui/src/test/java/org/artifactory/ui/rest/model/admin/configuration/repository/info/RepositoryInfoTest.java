package org.artifactory.ui.rest.model.admin.configuration.repository.info;

import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoBaseDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.ph.PackageSpecificationImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Yevdo Abramov
 * Created on 01/11/2020
 */
public class RepositoryInfoTest extends ArtifactoryHomeBoundJUnitTest {
    private AutoCloseable mocks;
    @Mock
    private PackageSpecificationAdapter packageSpecificationAdapter;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void unbind() throws Exception {
        mocks.close();
    }
    
    @Test
    void testTestToStringWhenProjectRelatedFieldsAreEmpty() throws IOException {
        RepositoryInfo repositoryInfo = new LocalRepositoryInfo();
        repositoryInfo.setRepoKey("myrepo");
        LocalRepositoryInfo info = JsonUtil.mapDataToModel(repositoryInfo.toString(), LocalRepositoryInfo.class);
        assertEquals(info, repositoryInfo);
        repositoryInfo.setProjectKey("my_proj");
        repositoryInfo.setEnvironments(Set.of("dev", "test"));
        RepositoryInfo repositoryInfoAfterConversion =
                JsonUtil.mapDataToModel(repositoryInfo.toString(), LocalRepositoryInfo.class);
        assertEquals("my_proj", repositoryInfoAfterConversion.getProjectKey());
        assertTrue(repositoryInfoAfterConversion.getEnvironments().containsAll(List.of("dev", "test")));
    }

    @ParameterizedTest
    @MethodSource("provideMigrationActionTestCases")
    void testLHuggingFaceLocalRepoHasMigrationAction(String isHuggingFaceLayoutEnabled, String isMlRepoLayout,
            Boolean expected) {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.huggingFaceMLEnableFrogMLLayout.getPropertyName(),
                isHuggingFaceLayoutEnabled);
        LocalRepositoryInfo localRepositoryInfo =
                createLocalRepositoryInfo(RepoType.HuggingFaceML, "testRepo", isMlRepoLayout);
        assertEquals(expected, localRepositoryInfo.hasMigrationAction);
    }

    @ParameterizedTest
    @MethodSource("provideMigrationActionTestCases")
    void testHuggingFaceFederatedRepoHasMigrationAction(String isHuggingFaceLayoutEnabled, String isMlRepoLayout,
            Boolean expected) {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.huggingFaceMLEnableFrogMLLayout.getPropertyName(),
                isHuggingFaceLayoutEnabled);
        FederatedRepositoryInfo remoteRepositoryInfo =
                createFederatedRepositoryInfo(RepoType.HuggingFaceML, "federatedTestRepo", isMlRepoLayout);
        assertEquals(expected, remoteRepositoryInfo.hasMigrationAction);
    }

    @ParameterizedTest
    @MethodSource("provideMigrationActionTestCases")
    void testHuggingFaceRemoteRepoHasMigrationAction(String isHuggingFaceLayoutEnabled, String isMlRepoLayout,
            Boolean expected) {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.huggingFaceMLEnableFrogMLLayout.getPropertyName(),
                isHuggingFaceLayoutEnabled);
        RemoteRepositoryInfo remoteRepositoryInfo =
                createRemoteRepositoryInfo(RepoType.HuggingFaceML, "remoteTestRepo", isMlRepoLayout);
        assertEquals(expected, remoteRepositoryInfo.hasMigrationAction);
    }

    @Test
    void testHuggingFaceRepositoryInfoWhenIsMlRepoLayoutNull() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.huggingFaceMLEnableFrogMLLayout.getPropertyName(),
                "true");
        RemoteRepositoryInfo remoteRepositoryInfo =
                createRemoteRepositoryInfo(RepoType.HuggingFaceML, "remoteTestRepo", null);
        assertTrue(remoteRepositoryInfo.hasMigrationAction);

        LocalRepositoryInfo localRepositoryInfo =
                createLocalRepositoryInfo(RepoType.HuggingFaceML, "localTestRepo", null);
        assertTrue(localRepositoryInfo.hasMigrationAction);

        FederatedRepositoryInfo federatedRepositoryInfo =
                createFederatedRepositoryInfo(RepoType.HuggingFaceML, "federatedTestRepo", null);
        assertTrue(federatedRepositoryInfo.hasMigrationAction);
    }

    @Test
    void testNonHuggingFaceRepoHasNoMigrationAction() {
        LocalRepositoryInfo localRepositoryInfo = createLocalRepositoryInfo(RepoType.Generic, "testRepo", null);
        Assertions.assertNull(localRepositoryInfo.hasMigrationAction);

        RemoteRepositoryInfo remoteRepositoryInfo = createRemoteRepositoryInfo(RepoType.Generic, "testRepo", null);
        Assertions.assertNull(remoteRepositoryInfo.hasMigrationAction);
    }

    private LocalRepositoryInfo createLocalRepositoryInfo(RepoType repoType, String repoKey, String isMlRepoLayout) {
        RepoConfig repoConfig = mock(RepoConfig.class);
        CentralConfigService configService = mock(CentralConfigService.class);
        PackageSpecification packageSpecification = new PackageSpecificationImpl(packageSpecificationAdapter);
        Map<String, String> packageTypeConfig = mock(Map.class);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);

        when(repoConfig.getPackageType()).thenReturn(repoType);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(repoConfig.getPackageTypeConfig()).thenReturn(packageTypeConfig);
        when(packageTypeConfig.get(IS_ML_REPO_LAYOUT)).thenReturn(isMlRepoLayout);
        when(configService.getDescriptor()).thenReturn(centralConfigDescriptor);

        return new LocalRepositoryInfo(repoConfig, configService, packageSpecification);
    }

    private RemoteRepositoryInfo createRemoteRepositoryInfo(RepoType repoType, String repoKey, String isMlRepoLayout) {
        RemoteRepoConfig repoConfig = mock(RemoteRepoConfig.class);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        PackageSpecification packageSpecification = new PackageSpecificationImpl(packageSpecificationAdapter);
        Map<String, String> packageTypeConfig = mock(Map.class);

        when(repoConfig.getPackageType()).thenReturn(repoType);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(repoConfig.getPackageTypeConfig()).thenReturn(packageTypeConfig);
        when(repoConfig.getRepoTypeConfig()).thenReturn(mock(RemoteRepositoryRepoTypeConfig.class));
        when(repoConfig.getRepoTypeConfig().getUrl()).thenReturn("http://test.com");
        when(repoConfig.getRepoTypeConfig().isBlackedOut()).thenReturn(false);
        when(repoConfig.getRepoTypeConfig().isOffline()).thenReturn(false);
        when(packageTypeConfig.get(IS_ML_REPO_LAYOUT)).thenReturn(isMlRepoLayout);
        when(centralConfigDescriptor.getRemoteReplication(repoKey)).thenReturn(null);

        return new RemoteRepositoryInfo(repoConfig, centralConfigDescriptor, packageSpecification);
    }

    private FederatedRepositoryInfo createFederatedRepositoryInfo(RepoType repoType, String repoKey,
            String isMlRepoLayout) {
        FederatedRepoConfig repoConfig = mock(FederatedRepoConfig.class);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        PackageSpecification packageSpecification = new PackageSpecificationImpl(packageSpecificationAdapter);
        Map<String, String> packageTypeConfig = mock(Map.class);
        when(repoConfig.getPackageType()).thenReturn(repoType);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(repoConfig.getPackageTypeConfig()).thenReturn(packageTypeConfig);
        when(packageTypeConfig.get(IS_ML_REPO_LAYOUT)).thenReturn(isMlRepoLayout);
        when(centralConfigDescriptor.getMultiLocalReplications(repoKey)).thenReturn(List.of());
        return new FederatedRepositoryInfo(repoConfig, centralConfigDescriptor, packageSpecification);
    }

    private static Stream<Arguments> provideMigrationActionTestCases() {
        return Stream.of(
                Arguments.of("true", "false", true),
                Arguments.of("true", "true", false),
                Arguments.of("false", "true", null),
                Arguments.of("false", "false", null),
                Arguments.of("true", null, true)
        );
    }

    @ParameterizedTest
    @MethodSource("provideNormalizedTestCases")
    void testNormalizedBehavior(RepoType repoType, boolean enableNormalized, Boolean expected) {
        RepoBaseDescriptor descriptor = mock(RepoBaseDescriptor.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("test-repo");
        when(descriptor.getType()).thenReturn(repoType);
        when(descriptor.isEnableNormalizedVersion()).thenReturn(enableNormalized);

        var info = new org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info.RepositoryInfo(
                descriptor, repoPath);
        assertEquals(expected, info.getNormalized());
    }

    @Test
    void testNormalizedIsNullWhenDescriptorTypeIsNull() {
        RepoBaseDescriptor descriptor = mock(RepoBaseDescriptor.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("test-repo");
        when(descriptor.getType()).thenReturn(null);

        var info = new org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info.RepositoryInfo(
                descriptor, repoPath);
        assertNull(info.getNormalized());
    }

    private static Stream<Arguments> provideNormalizedTestCases() {
        return Stream.of(
                Arguments.of(RepoType.NuGet, true, true),
                Arguments.of(RepoType.NuGet, false, null),
                Arguments.of(RepoType.Maven, true, null),
                Arguments.of(RepoType.Generic, false, null)
        );
    }
}