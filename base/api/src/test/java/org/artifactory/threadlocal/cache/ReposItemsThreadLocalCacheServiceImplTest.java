package org.artifactory.threadlocal.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.apache.commons.lang3.RandomStringUtils;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.fscLocalReposMinimum;
import static org.artifactory.common.ConstantValues.fscRemoteReposMinimum;
import static org.artifactory.descriptor.repo.RepoType.Ansible;
import static org.artifactory.descriptor.repo.RepoType.Composer;
import static org.artifactory.descriptor.repo.RepoType.Conda;
import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.Gems;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.Go;
import static org.artifactory.descriptor.repo.RepoType.Helm;
import static org.artifactory.descriptor.repo.RepoType.HelmOCI;
import static org.artifactory.descriptor.repo.RepoType.LuaRocks;
import static org.artifactory.descriptor.repo.RepoType.Maven;
import static org.artifactory.descriptor.repo.RepoType.Npm;
import static org.artifactory.descriptor.repo.RepoType.NuGet;
import static org.artifactory.descriptor.repo.RepoType.OCI;
import static org.artifactory.descriptor.repo.RepoType.Pypi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReposItemsThreadLocalCacheServiceImplTest extends ArtifactoryHomeBoundJUnitTest {
    
    @Mock
    private RepositoryService repositoryService;
    private AutoCloseable autoCloseable;
    private static final int MIN_LOCAL_REPOS = 5;
    private static final int MIN_REMOTES_REPOS = 5;

    @BeforeEach
    void beforeEach() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        getBound().setProperty(fscLocalReposMinimum, String.valueOf(MIN_LOCAL_REPOS));
        getBound().setProperty(fscRemoteReposMinimum, String.valueOf(MIN_REMOTES_REPOS));
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(MeterRegistry.class)).thenReturn(Metrics.globalRegistry);
        LocalRepoConfig localRepoConfig = mock(LocalRepoConfig.class);
        when(repositoryService.getLocalRepoConfigByKey("cool-repo")).thenReturn(localRepoConfig);
        when(repositoryService.getLocalOrFederatedRepoConfigByKey("cool-repo")).thenReturn(localRepoConfig);
        when(localRepoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(localRepoConfig.getPackageType()).thenReturn(Maven);
        ArtifactoryContextThreadBinder.bind(context);
        ReposItemsThreadLocalCacheService.startUseCacheForSupplierFlow(Maven);
    }

    @AfterEach
    void afterEach() throws Exception {
        ReposItemsThreadLocalCacheService.endUseCacheForCurrentFlow();
        ReposItemsThreadLocalCacheService.getVirtualRepoNumberOfLocalRepos().invalidateAll();
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void testWrap() {
        String s = "cool string";
        String repoKey = "my-repo";
        LocalConfig localConfig = mock(LocalConfig.class);
        when(localConfig.getPackageType()).thenReturn(Maven);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(localConfig);
        String wrapped = ReposItemsThreadLocalCacheService.wrap(repoKey, () -> s);
        assertEquals(s, wrapped);
    }

    @Test
    void wrap_shouldCallSupplierWithoutThreadLocalCache_whenRepoConfigIsMissing() {
        String repoKey = "missing-repo";
        String expectedValue = "not found flow result";

        String result = ReposItemsThreadLocalCacheService.wrap(repoKey, () -> expectedValue);

        assertEquals(expectedValue, result);
        verify(repositoryService, never()).virtualRepoDescriptorByKey(repoKey);
    }

    @Test
    void wrap_shouldCallSupplierWithoutThreadLocalCache_whenRepoPackageTypeIsMissing() {
        String repoKey = "missing-package-type-repo";
        String expectedValue = "not found flow result";
        LocalConfig localConfig = mock(LocalConfig.class);
        when(localConfig.getPackageType()).thenReturn(null);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(localConfig);

        String result = ReposItemsThreadLocalCacheService.wrap(repoKey, () -> expectedValue);

        assertEquals(expectedValue, result);
        verify(repositoryService, never()).virtualRepoDescriptorByKey(repoKey);
    }

    @Test
    void testNestedWrap() {
        String repoKey1 = "my-repo-1";
        String expectedValue1 = "Expected Value 1";
        String expectedValue2 = "Expected Value 2";
        String expectedValue3 = "Expected Value 3";

        LocalConfig localConfig = mock(LocalConfig.class);
        when(localConfig.getPackageType()).thenReturn(Maven);
        when(repositoryService.getRepoConfigByKey(repoKey1)).thenReturn(localConfig);
        setupRepositoryServiceMock(repoKey1, true, 10, 10);

        String actualValue1 = ReposItemsThreadLocalCacheService.wrap(repoKey1, () -> {
            String actualValue2 = ReposItemsThreadLocalCacheService.wrap(repoKey1, () -> {
                String actualValue3 = ReposItemsThreadLocalCacheService.wrap(repoKey1, () -> expectedValue3);
                assertEquals(expectedValue3, actualValue3);
                return expectedValue2;
            });
            assertEquals(expectedValue2, actualValue2);
            return expectedValue1;
        });

        assertEquals(expectedValue1, actualValue1);
    }

    @Test
    void testGetWithThreadLocal() {
        AqlItem node1 = mock(AqlItem.class);
        doReturn(1L).when(node1).getNodeId();
        doReturn("cool-repo").when(node1).getRepo();
        AqlItem node2 = mock(AqlItem.class);
        doReturn(2L).when(node2).getNodeId();
        doReturn("not-cool-repo").when(node2).getRepo();
        AqlItem node3 = mock(AqlItem.class);
        List<AqlItem> nodes = List.of(node1, node2);
        LocalConfig localConfig = mock(LocalConfig.class);
        when(localConfig.getPackageType()).thenReturn(Maven);
        when(repositoryService.getRepoConfigByKey("cool-repo")).thenReturn(localConfig);

        VirtualRepoDescriptor virtualRepo = mock(VirtualRepoDescriptor.class);
        when(repositoryService.virtualRepoDescriptorByKey("cool-repo")).thenReturn(virtualRepo);

        List<RepoDescriptor> repoDescriptors = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
            when(repoDescriptor.getKey()).thenReturn("cool-repo-" + i);
            repoDescriptors.add(repoDescriptor);
        }

        when(repositoryService.getVirtualResolvedLocalFederatedAndCacheDescriptors("cool-repo"))
                .thenReturn(repoDescriptors);

        ReposItemsThreadLocalCacheService.wrap("cool-repo", () -> {
            AqlItem node = ReposItemsThreadLocalCacheService
                    .get("cool-key", "cool-repo", limit -> nodes, 10, () -> List.of(node3));
            assertEquals(node1.getNodeId(), node.getNodeId());
            return null;
        });
    }

    @Test
    void testGetWithoutThreadLocal() {
        ReposItemsThreadLocalCacheService.endUseCacheForCurrentFlow();
        AqlItem node1 = mock(AqlItem.class);
        AqlItem node2 = mock(AqlItem.class);
        AqlItem node3 = mock(AqlItem.class);
        doReturn(3L).when(node3).getNodeId();
        List<AqlItem> nodes = List.of(node1, node2);
        AqlItem node = ReposItemsThreadLocalCacheService
                .get("cool-key", "cool-repo", limit -> nodes, 10, () -> List.of(node3));
        assertEquals(node3.getNodeId(), node.getNodeId());
    }

    @Test
    void testExistsWithThreadLocal() {
        AqlItem node1 = mock(AqlItem.class);
        doReturn(1L).when(node1).getNodeId();
        doReturn("cool-repo").when(node1).getRepo();
        AqlItem node2 = mock(AqlItem.class);
        doReturn(2L).when(node2).getNodeId();
        doReturn("not-cool-repo").when(node2).getRepo();
        List<AqlItem> nodes = List.of(node1, node2);
        LocalConfig localConfig = mock(LocalConfig.class);
        when(localConfig.getPackageType()).thenReturn(Maven);
        when(repositoryService.getRepoConfigByKey("cool-repo")).thenReturn(localConfig);
        boolean exists = ReposItemsThreadLocalCacheService
                .exists("cool-key", "cool-repo", limit -> nodes, 10, () -> false);
        assertTrue(exists);
    }

    @Test
    void testExistsAndInvalidate() {
        AqlItem node1 = mock(AqlItem.class);
        doReturn(1L).when(node1).getNodeId();
        doReturn("cool-repo").when(node1).getRepo();
        AqlItem node2 = mock(AqlItem.class);
        doReturn(2L).when(node2).getNodeId();
        doReturn("not-cool-repo").when(node2).getRepo();
        List<AqlItem> nodes = List.of(node1, node2);
        LocalConfig localConfig = mock(LocalConfig.class);
        when(localConfig.getPackageType()).thenReturn(Maven);
        when(repositoryService.getRepoConfigByKey("cool-repo")).thenReturn(localConfig);

        ReposItemsThreadLocalCacheService.invalidateCache("cool-repo");
        boolean exists = ReposItemsThreadLocalCacheService
                .exists("cool-key", "cool-repo", limit -> nodes, 10, () -> false);
        assertFalse(exists);
    }

    @Test
    void testExistsWithoutThreadLocal() {
        ReposItemsThreadLocalCacheService.endUseCacheForCurrentFlow();
        AqlItem node1 = mock(AqlItem.class);
        AqlItem node2 = mock(AqlItem.class);
        AqlItem node3 = mock(AqlItem.class);
        doReturn(3L).when(node3).getNodeId();
        List<AqlItem> nodes = List.of(node1, node2);
        boolean exists =
                ReposItemsThreadLocalCacheService.exists("cool-key", "cool-repo", limit -> nodes, 10, () -> false);
        assertFalse(exists);
    }

    @ParameterizedTest
    @MethodSource("packageTypeSource")
    void testIsPackageTypeSupported_UnsupportedPackage(RepoType packageType, boolean outcome) {
        String repoKey = "my-repo";
        LocalConfig localConfig = mock(LocalConfig.class);
        when(localConfig.getPackageType()).thenReturn(packageType);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(localConfig);
        assertEquals(outcome, ReposItemsThreadLocalCacheService.isPackageTypeSupported(packageType));
    }

    @ParameterizedTest
    @MethodSource("shouldUseThreadLocalCache")
    void testShouldUseThreadLocalCache(boolean result, boolean isPackageSupported, boolean isVirtualRepo,
            int numberOfLocals, int numberOfRemotes) {
        String repoKey = RandomStringUtils.randomAlphanumeric(5);
        setupRepositoryServiceMock(repoKey, isVirtualRepo, numberOfLocals, numberOfRemotes);
        RepoType repoType = isPackageSupported ? Pypi : Gems;
        LocalConfig localConfig = mock(LocalConfig.class);
        when(localConfig.getPackageType()).thenReturn(repoType);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(localConfig);
        assertEquals(result, ReposItemsThreadLocalCacheService.shouldUseThreadLocalCache(
                repoKey, isPackageSupported ? Pypi : Gems, () -> true));
    }

    private void setupRepositoryServiceMock(String repoName, boolean isVirtualRepo, int numberOfLocals
            , int numberOfRemotes) {
        VirtualRepoDescriptor virtualRepo = mock(VirtualRepoDescriptor.class);
        var localRepoDescriptors = Collections.nCopies(numberOfLocals, mock(LocalRepoDescriptor.class));
        var remoteRepoDescriptors = Collections.nCopies(numberOfRemotes, mock(RemoteRepoDescriptor.class));
        List<RepoDescriptor> combined = Stream.concat(localRepoDescriptors.stream(), remoteRepoDescriptors.stream())
                .collect(Collectors.toList());
        when(virtualRepo.getRepositories()).thenReturn(combined);
        if (isVirtualRepo) {
            when(repositoryService.virtualRepoDescriptorByKey(repoName)).thenReturn(virtualRepo);
        } else {
            when(repositoryService.virtualRepoDescriptorByKey(repoName)).thenReturn(null);
        }

        List<RepoDescriptor> repoDescriptors = new ArrayList<>();
        for (int i = 0; i < numberOfLocals; i++) {
            RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
            when(repoDescriptor.getKey()).thenReturn("local-repo-" + i);
            repoDescriptors.add(repoDescriptor);
        }

        for (int i = 0; i < numberOfRemotes; i++) {
            RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
            when(repoDescriptor.getKey()).thenReturn("remote-repo-" + i + "-cache");
            repoDescriptors.add(repoDescriptor);
        }

        when(repositoryService.getVirtualResolvedLocalFederatedAndCacheDescriptors(repoName))
                .thenReturn(repoDescriptors);
    }

    private static Stream<Arguments> packageTypeSource() {
        Set<RepoType> supported = Set.of(Docker, Maven, Pypi, Npm, Go, OCI, HelmOCI, Conda, Ansible, Helm, Generic,
                NuGet, Composer, LuaRocks);
        return Arrays.stream(RepoType.values())
                .map(repoType -> Arguments.of(repoType, supported.contains(repoType)));
    }

    private static Stream<Arguments> shouldUseThreadLocalCache() {
        return Stream.of(
                Arguments.of(true, true, true, MIN_LOCAL_REPOS, 0),
                Arguments.of(true, true, true, MIN_LOCAL_REPOS + 1, 0),
                Arguments.of(false, true, true, MIN_LOCAL_REPOS - 1, 0),
                Arguments.of(false, false, true, 10, 0),
                Arguments.of(false, true, false, 10, 0),

                Arguments.of(true, true, true, 0, MIN_LOCAL_REPOS),
                Arguments.of(true, true, true, 0, MIN_LOCAL_REPOS + 1),
                Arguments.of(false, true, true, 0, MIN_LOCAL_REPOS - 1),
                Arguments.of(false, false, true, 0, 10),
                Arguments.of(false, true, false, 0, 10)
        );
    }
}