package org.artifactory.threadlocal.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.utils.PathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.common.ConstantValues.reposItemsLimitReachedCacheExpMS;
import static org.artifactory.common.ConstantValues.reposItemsLimitReachedCacheSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Andrey Tashlitsky
 */
class ReposItemsCacheTest extends ArtifactoryHomeBoundJUnitTest {

    public static final String REMOTE_REPO_CACHE = "cool-repo-cache";
    public static final String NOT_COOL_REPO = "not-cool-repo";
    public static final String COOL_REPO = "cool-repo";
    private ReposItemsCache<AqlItem, SQLException> itemsCacheService;
    private static AqlItem node1;
    private static AqlItem node2;
    private static AqlItem node3;
    private static AqlItem node4;
    private static List<AqlItem> nodes;
    private static Map<String, List<AqlItem>> fullCacheMap;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    ArtifactoryHome artifactoryHome;
    @Mock
    ArtifactorySystemProperties artifactorySystemProperties;
    private AutoCloseable autoCloseable;
    ArtifactoryContext context;
    MeterRegistry meterRegistry;

    @BeforeEach
    public void beforeEach() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        context = mock(ArtifactoryContext.class);
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        ArtifactoryContextThreadBinder.bind(context);

        meterRegistry = new SimpleMeterRegistry();
        when(context.beanForType(MeterRegistry.class)).thenReturn(meterRegistry);

        ArtifactoryHome.bind(artifactoryHome);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getLongProperty(reposItemsLimitReachedCacheExpMS)).thenReturn(100L);
        when(artifactorySystemProperties.getLongProperty(reposItemsLimitReachedCacheSize)).thenReturn(100L);
        when(artifactorySystemProperties.getLongProperty(reposItemsLimitReachedCacheSize)).thenReturn(100L);
        ReposItemsCache.itemsLimitReachedKeys.invalidateAll();

        LocalRepoConfig localRepoConfig = mock(LocalRepoConfig.class);
        when(repositoryService.getLocalOrFederatedRepoConfigByKey("cool-repo"))
                .thenReturn(localRepoConfig);
        when(repositoryService.getLocalOrFederatedRepoConfigByKey("other-cool-repo"))
                .thenReturn(localRepoConfig);
        when(localRepoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(localRepoConfig.getPackageType()).thenReturn(RepoType.Maven);

        itemsCacheService = new ReposItemsCache<>(Collections.singleton(RepoType.Maven),
                Collections.singleton(RepoType.Maven), meterRegistry, RepoType.Maven);

        node1 = mock(AqlItem.class);
        doReturn(1L).when(node1).getNodeId();
        doReturn(COOL_REPO).when(node1).getRepo();
        node2 = mock(AqlItem.class);
        doReturn(2L).when(node2).getNodeId();
        doReturn(NOT_COOL_REPO).when(node2).getRepo();
        node3 = mock(AqlItem.class);
        doReturn(3L).when(node3).getNodeId();
        doReturn(COOL_REPO).when(node3).getRepo();

        node4 = mock(AqlItem.class);
        doReturn(4L).when(node4).getNodeId();
        doReturn(REMOTE_REPO_CACHE).when(node4).getRepo();
        nodes = List.of(node1, node2, node3, node4);


        fullCacheMap = new HashMap<>() {{
                put(COOL_REPO, List.of(node1, node3));
                put(NOT_COOL_REPO, List.of(node2));
                put(REMOTE_REPO_CACHE, List.of(node4));
            }};
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void testGetCacheEmpty_RepoFound_Caching() throws Exception {
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        String repoType = RepoType.Maven.getType();
        itemsCacheService.cache = spy(cache);
        setuprepositoryServiceMock(COOL_REPO);

        List<AqlItem> actualNode = itemsCacheService.get(
                "cool-key", COOL_REPO, getNodes(nodes), 10, getNode(node3));
        assertNotNull(actualNode);
        assertEquals(node1.getNodeId(), actualNode.get(0).getNodeId());
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        nodesEqual(fullCacheMap, cache.get("cool-key"));
        verify(cache, times(1)).put(eq("cool-key"), any());
        assertEquals(0, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
    }

    @Test
    void testGetCacheEmpty_RepoNotFound_Caching() throws Exception {
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        String repoType = RepoType.Maven.getType();
        itemsCacheService.cache = spy(cache);

        setuprepositoryServiceMock("other-cool-repo");
        List<AqlItem> actualNode = itemsCacheService.get(
                "cool-key", "other-cool-repo", getNodes(nodes), 10, getNode(node3));
        assertNull(actualNode);
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        nodesEqual(fullCacheMap, cache.get("cool-key"));
        verify(cache, times(1)).put(eq("cool-key"), any());

        assertEquals(0, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
    }

    @Test
    void testGetCacheEmpty_ReachedLimit_Caching() throws Exception {
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        String repoType = RepoType.Maven.getType();
        itemsCacheService.cache = spy(cache);
        setuprepositoryServiceMock(COOL_REPO);

        List<AqlItem> actualNode = itemsCacheService.get(
                "cool-key4", COOL_REPO, getNodes(nodes), 3, getNode(node3));
        assertNotNull(actualNode);
        assertEquals(node3.getNodeId(), actualNode.get(0).getNodeId());
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        assertNull(cache.get("cool-key4"));
        verify(cache, times(0)).put(eq("cool-key4"), any());

        assertEquals(0, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
        assertEquals(1, getMetricLimitFallbackCount(repoType));
    }

    @Test
    void testGetCacheEmpty_RemoteRepo_Caching() throws Exception {
        setupRemoterepositoryServiceMock(REMOTE_REPO_CACHE);
        String remoteRepoName = PathUtils.removeDashCache(REMOTE_REPO_CACHE);
        when(repositoryService.existsByRepoKey(remoteRepoName)).thenReturn(true);
        String repoType = RepoType.Maven.getType();
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        itemsCacheService.cache = spy(cache);

        List<AqlItem> actualNode = itemsCacheService.get(
                "remote-key", REMOTE_REPO_CACHE, getNodes(nodes), 10, getNode(node3));
        assertNotNull(actualNode);
        assertEquals(node4.getNodeId(), actualNode.get(0).getNodeId());
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        assertNotNull(cache.get("remote-key"));
        verify(cache, times(1)).put(eq("remote-key"), any());

        assertEquals(0, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
    }

    @Test
    void testGetCacheNotEmpty_RepoFound_NotCaching() throws Exception {
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        Map<String, Map<String, List<AqlItem>>> cacheSpy = spy(cache);
        String repoType = RepoType.Maven.getType();
        cache.put("cool-key", fullCacheMap);
        itemsCacheService.cache = cacheSpy;
        setuprepositoryServiceMock(COOL_REPO);

        List<AqlItem> actualNode = itemsCacheService.get(
                "cool-key", COOL_REPO, getNodes(nodes), 10, getNode(node3));
        assertNotNull(actualNode);
        assertEquals(node1.getNodeId(), actualNode.get(0).getNodeId());
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        nodesEqual(fullCacheMap, cache.get("cool-key"));
        verify(cache, times(1)).put(eq("cool-key"), any());

        assertEquals(0, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
    }

    @Test
    void testGetCacheNotEmpty_RepoNotFound_NotCaching() throws Exception {
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        String repoType = RepoType.Maven.getType();
        itemsCacheService.cache = spy(cache);
        cache.put("cool-key", fullCacheMap);
        setuprepositoryServiceMock("other-cool-repo");

        List<AqlItem> actualNode = get10Times(() -> itemsCacheService.get(
                "cool-key", "other-cool-repo", getNodes(nodes), 10, getNode(node3)));

        assertNull(actualNode);
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        nodesEqual(fullCacheMap, cache.get("cool-key"));
        verify(cache, times(1)).put(eq("cool-key"), any());

        assertEquals(9, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
    }

    @Test
    void testGetCacheNotEmpty_ReachedLimit_NotCaching() throws Exception {
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        Map<String, Map<String, List<AqlItem>>> cacheSpy = spy(cache);
        String repoType = RepoType.Maven.getType();
        cache.put("cool-key3", fullCacheMap);
        itemsCacheService.cache = cacheSpy;
        setuprepositoryServiceMock(COOL_REPO);

        List<AqlItem> actualNode = get10Times(() -> itemsCacheService.get(
                "cool-key3", COOL_REPO, getNodes(nodes), 3, getNode(node3)));
        assertNotNull(actualNode);
        assertEquals(node3.getNodeId(), actualNode.get(0).getNodeId());
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        assertNull(cache.get("cool-key3"));
        verify(cache, times(0)).put(eq("cool-key3"), any());

        assertEquals(0, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
        assertEquals(10, getMetricLimitFallbackCount(repoType));
    }

    @Test
    void testGetCacheNullRemoteRepo() {
        when(repositoryService.existsByRepoKey("not")).thenReturn(true);
        assertDoesNotThrow(() -> itemsCacheService.get("remote-key", "not-cache", getNodes(nodes), 10, getNode(node3)));
    }

    @Test
    void testGetCacheNotEmpty_RemoteRepo() throws Exception {
        setupRemoterepositoryServiceMock(REMOTE_REPO_CACHE);
        String remoteRepoName = PathUtils.removeDashCache(REMOTE_REPO_CACHE);
        when(repositoryService.existsByRepoKey(remoteRepoName)).thenReturn(true);
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        String repoType = RepoType.Maven.getType();
        itemsCacheService.cache = spy(cache);
        itemsCacheService.cache.put("remote-key", fullCacheMap);

        List<AqlItem> actualNode = get10Times(() -> itemsCacheService.get(
                "remote-key", REMOTE_REPO_CACHE, getNodes(nodes), 10, getNode(node3)));
        assertNotNull(actualNode);
        assertEquals(node4.getNodeId(), actualNode.get(0).getNodeId());
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        nodesEqual(fullCacheMap, cache.get("remote-key"));
        verify(cache, times(1)).put(eq("remote-key"), any());

        assertEquals(10, getMetricHitCount(repoType));
        assertEquals(0, getMetricMissCount(repoType));
    }

    @Test
    void testExistsCacheEmpty_RepoFound_Caching() throws Exception {
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        String repoType = RepoType.Maven.getType();
        itemsCacheService.cache = spy(cache);
        setuprepositoryServiceMock(COOL_REPO);

        boolean exists = run10Times(() -> itemsCacheService.exists(
                "cool-key", COOL_REPO, getNodes(nodes), 10, nodeExists(true)));
        assertTrue(exists);
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        nodesEqual(fullCacheMap, cache.get("cool-key"));
        verify(cache, times(1)).put(eq("cool-key"), any());

        assertEquals(9, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
    }

    @Test
    void testExistsCacheEmpty_RepoNotFound_Caching() throws Exception {
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        String repoType = RepoType.Maven.getType();
        itemsCacheService.cache = spy(cache);
        setuprepositoryServiceMock("other-cool-repo");

        boolean exists = run10Times(() -> itemsCacheService.exists(
                "cool-key", "other-cool-repo", getNodes(nodes), 10, nodeExists(false)));
        assertFalse(exists);
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        nodesEqual(fullCacheMap, cache.get("cool-key"));
        verify(cache, times(1)).put(eq("cool-key"), any());

        assertEquals(9, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
    }

    @Test
    void testExistsCacheEmpty_ReachedLimit_Caching() throws Exception {
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        String repoType = RepoType.Maven.getType();
        itemsCacheService.cache = spy(cache);
        setuprepositoryServiceMock(COOL_REPO);

        boolean exists = run10Times(() -> itemsCacheService.exists(
                "cool-key5", COOL_REPO, getNodes(nodes), 3, nodeExists(false)));
        assertFalse(exists);
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        assertNull(cache.get("cool-key5"));
        verify(cache, times(0)).put(eq("cool-key"), any());

        assertEquals(0, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
        assertEquals(10, getMetricLimitFallbackCount(repoType));
    }

    @Test
    void testExistsCacheEmpty_nonSupportedRepo_metrics() throws Exception {
        String repoType = RepoType.Pypi.getType();
        ReposItemsCache<AqlItem, SQLException> itemsCacheService = new ReposItemsCache<>(
                Collections.singleton(RepoType.Pypi), Collections.singleton(RepoType.Pypi), meterRegistry,
                RepoType.Pypi);

        setuprepositoryServiceMock(COOL_REPO);

        run10Times(() -> itemsCacheService.exists("cool-key5", COOL_REPO, getNodes(nodes), 20, nodeExists(false)));

        assertEquals(0, getMetricHitCount(repoType));
        assertEquals(0, getMetricMissCount(repoType));
        assertEquals(0, getMetricInvalidatedFallbackCount(repoType));
        assertEquals(10, getMetricNotSupportedFallbackCount(repoType));
    }

    @Test
    void testExistsCacheEmpty_virtualRepo_metrics() throws Exception {
        String repoType = RepoType.Maven.getType();
        VirtualRepoConfig virtualRepoConfig = mock(VirtualRepoConfig.class);
        when(repositoryService.getRepoConfigByKey(COOL_REPO)).thenReturn(virtualRepoConfig);
        when(virtualRepoConfig.getRepoType()).thenReturn(RepoDetailsType.VIRTUAL);

        run10Times(() -> itemsCacheService.exists("cool-key5", COOL_REPO, getNodes(nodes), 20, nodeExists(false)));

        assertEquals(0, getMetricHitCount(repoType));
        assertEquals(0, getMetricMissCount(repoType));
        assertEquals(10, getMetricVirtualFallbackCount(repoType));
    }

    @Test
    void testExistsCacheEmpty_Invalidate_Caching() throws Exception {
        ReposItemsCache.itemsLimitReachedKeys.invalidateAll();
        String repoType = RepoType.Maven.getType();

        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        itemsCacheService.cache = spy(cache);
        setuprepositoryServiceMock(COOL_REPO);

        itemsCacheService.invalidate(COOL_REPO);
        boolean exists = run10Times(() -> itemsCacheService.exists(
                "cool-key5", COOL_REPO, getNodes(nodes), 20, nodeExists(false)));
        assertFalse(exists);
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        assertNull(cache.get("cool-key5"));
        verify(cache, times(0)).put(eq("cool-key"), any());

        assertEquals(0, getMetricHitCount(repoType));
        assertEquals(0, getMetricMissCount(repoType));
        assertEquals(10, getMetricInvalidatedFallbackCount(repoType));
    }

    @Test
    void testExistsCacheEmpty_RemoteRepo_Caching() throws Exception {
        setupRemoterepositoryServiceMock(REMOTE_REPO_CACHE);
        String remoteRepoName = PathUtils.removeDashCache(REMOTE_REPO_CACHE);
        when(repositoryService.existsByRepoKey(remoteRepoName)).thenReturn(true);
        String repoType = RepoType.Maven.getType();
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        itemsCacheService.cache = spy(cache);

        boolean exists = run10Times(() -> itemsCacheService.exists(
                "remote-key", REMOTE_REPO_CACHE, getNodes(nodes), 10, nodeExists(false)));
        assertTrue(exists);
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        assertNotNull(cache.get("remote-key"));
        verify(cache, times(1)).put(eq("remote-key"), any());

        assertEquals(9, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
    }

    @Test
    void testExistsCacheNotEmpty_RepoFound_NotCaching() throws Exception {
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        String repoType = RepoType.Maven.getType();
        itemsCacheService.cache = spy(cache);
        cache.put("cool-key", fullCacheMap);
        setuprepositoryServiceMock(COOL_REPO);

        boolean exists = run10Times(() -> itemsCacheService.exists(
                "cool-key", COOL_REPO, getNodes(nodes), 10, nodeExists(false)));
        assertTrue(exists);
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        nodesEqual(fullCacheMap, cache.get("cool-key"));
        verify(cache, times(1)).put(eq("cool-key"), any());

        assertEquals(9, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
    }

    @Test
    void testExistsCacheNotEmpty_RepoNotFound_NotCaching() throws Exception {
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        String repoType = RepoType.Maven.getType();
        Map<String, Map<String, List<AqlItem>>> cacheSpy = spy(cache);
        cache.put("cool-key", fullCacheMap);
        itemsCacheService.cache = cacheSpy;
        setuprepositoryServiceMock("other-cool-repo");

        boolean exists = run10Times(() -> itemsCacheService.exists(
                "cool-key", "other-cool-repo", getNodes(nodes), 10, nodeExists(false)));
        assertFalse(exists);
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        nodesEqual(fullCacheMap, cache.get("cool-key"));
        verify(cache, times(1)).put(eq("cool-key"), any());

        assertEquals(9, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
    }

    @Test
    void testExistsCacheNotEmpty_ReachedLimit_NotCaching() throws Exception {
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        String repoType = RepoType.Maven.getType();
        itemsCacheService.cache = spy(cache);
        itemsCacheService.cache.put("cool-key2", fullCacheMap);
        setuprepositoryServiceMock(COOL_REPO);
        ReposItemsCache.itemsLimitReachedKeys.put("cool-key2", "LIMIT_REACHED");

        boolean exists = run10Times(() -> itemsCacheService.exists(
                "cool-key2", COOL_REPO, getNodes(nodes), 3, nodeExists(false)));
        assertFalse(exists);
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        nodesEqual(fullCacheMap, cache.get("cool-key2"));
        verify(cache, times(1)).put(eq("cool-key2"), any());

        assertEquals(0, getMetricHitCount(repoType));
        assertEquals(0, getMetricMissCount(repoType));
        assertEquals(10, getMetricLimitFallbackCount(repoType));
    }

    @Test
    void testExistsCacheNotEmpty_RemoteRepo_getFromCache() throws Exception {
        setupRemoterepositoryServiceMock(REMOTE_REPO_CACHE);
        String remoteRepoName = PathUtils.removeDashCache(REMOTE_REPO_CACHE);
        when(repositoryService.existsByRepoKey(remoteRepoName)).thenReturn(true);
        String repoType = RepoType.Maven.getType();
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        itemsCacheService.cache = spy(cache);
        itemsCacheService.cache.put("cool-key", fullCacheMap);

        boolean exists = run10Times(() -> itemsCacheService.exists(
                "remote-key", REMOTE_REPO_CACHE, getNodes(nodes), 10, nodeExists(false)));
        assertTrue(exists);
        cache = itemsCacheService.cache;
        assertNotNull(cache);
        nodesEqual(fullCacheMap, cache.get("remote-key"));
        verify(cache, times(1)).put(eq("remote-key"), any());

        assertEquals(9, getMetricHitCount(repoType));
        assertEquals(1, getMetricMissCount(repoType));
    }

    @Test
    void testGetFromCacheOrFillCacheIfNeeded_CacheContainsNonEmptyMapForKey_ReturnsMap() throws SQLException {
        itemsCacheService.cache = Map.of("key", Map.of("repo", List.of(node1, node2)));
        Map<String, List<AqlItem>> map = itemsCacheService.getFromCacheOrFillCacheIfNeeded("key", null, -1);
        assertFalse(map.isEmpty());
    }

    @Test
    void testGetFromCacheOrFillCacheIfNeeded_CacheContainsEmptyMapForKey_NoSupplierCall() throws Exception {
        long limit = 10L;
        itemsCacheService.cache = Map.of("key", Map.of());
        SupplierWithLimit supplierWithLimit = mock(SupplierWithLimit.class);
        when(supplierWithLimit.apply(limit)).thenReturn(List.of());
        Map<String, List<AqlItem>> map =
                itemsCacheService.getFromCacheOrFillCacheIfNeeded("key", supplierWithLimit, limit);
        assertNotNull(map);
        assertTrue(map.isEmpty());
        verify(supplierWithLimit, never()).apply(anyLong());
    }

    @Test
    void testGetFromCacheOrFillCacheIfNeeded_SupplierReturnsNonEmptyList_CachedUpdated() throws Exception {
        long limit = 10L;
        itemsCacheService.cache = new HashMap<>();
        SupplierWithLimit supplierWithLimit = mock(SupplierWithLimit.class);
        when(supplierWithLimit.apply(limit)).thenReturn(List.of(node1));
        Map<String, List<AqlItem>> map =
                itemsCacheService.getFromCacheOrFillCacheIfNeeded("key", supplierWithLimit, limit);
        assertNotNull(map);
        assertEquals(1, map.size());
        assertEquals(node1, map.get(COOL_REPO).get(0));
        assertEquals(itemsCacheService.cache.get("key"), map);
    }

    @Test
    void testGetFromCacheOrFillCacheIfNeeded_SupplierReturnsEmptyList_CachesEmptyResult() throws Exception {
        long limit = 10L;
        Map<String, Map<String, List<AqlItem>>> cache = new HashMap<>();
        itemsCacheService.cache = spy(cache);
        SupplierWithLimit supplierWithLimit = mock(SupplierWithLimit.class);
        when(supplierWithLimit.apply(limit)).thenReturn(List.of());

        Map<String, List<AqlItem>> first =
                itemsCacheService.getFromCacheOrFillCacheIfNeeded("key", supplierWithLimit, limit);
        assertNotNull(first);
        assertTrue(first.isEmpty());
        verify(itemsCacheService.cache, times(1)).put(eq("key"), any());

        Map<String, List<AqlItem>> second =
                itemsCacheService.getFromCacheOrFillCacheIfNeeded("key", supplierWithLimit, limit);
        assertNotNull(second);
        assertTrue(second.isEmpty());
        verify(supplierWithLimit, times(1)).apply(limit);
    }

    private void setupRemoterepositoryServiceMock(String repoName) {
        RemoteRepoConfig remoteConfig = mock(RemoteRepoConfig.class);
        when(remoteConfig.getPackageType()).thenReturn(RepoType.Maven);
        String remoteRepoName = PathUtils.removeDashCache(repoName);
        when(repositoryService.getRemoteRepoConfigByKey(remoteRepoName)).thenReturn(remoteConfig);
    }

    private static SupplierWithLimit<AqlItem, SQLException> getNodes(List<AqlItem> nodes) {
        return limit -> nodes;
    }

    private static FallbackSupplier<AqlItem, SQLException> getNode(AqlItem node) {
        return () -> List.of(node);
    }

    private static NodeExistsSupplier nodeExists(boolean exists) {
        return () -> exists;
    }

    private void nodesEqual(Map<String, List<AqlItem>> map1, Map<String, List<AqlItem>> map2) {
        assertNotNull(map1);
        assertNotNull(map2);
        assertEquals(map1.entrySet(), map2.entrySet());
    }

    private void setuprepositoryServiceMock(String repoName) {
        LocalConfig localConfig = mock(LocalConfig.class);
        when(localConfig.getPackageType()).thenReturn(RepoType.Maven);
        when(repositoryService.getRepoConfigByKey(repoName)).thenReturn(localConfig);
    }

    private List<AqlItem> get10Times(FallbackSupplier<AqlItem, SQLException> function) throws Exception {
        for (int i = 1; i < 10; i++) {
            function.get();
        }
        return function.get();
    }

    private boolean run10Times(NodeExistsSupplier function) throws Exception {
        for (int i = 1; i < 10; i++) {
            function.exists();
        }
        return function.exists();
    }

    private double getMetricHitCount(String repoType) {
        return meterRegistry.counter("thread.local.cache.event", "result", "hit", "repoType", repoType).count();
    }

    private double getMetricMissCount(String repoType) {
        return meterRegistry.counter("thread.local.cache.event", "result", "miss", "repoType", repoType).count();
    }

    private double getMetricLimitFallbackCount(String repoType) {
        return meterRegistry.counter("thread.local.cache.fallback", "reason", "limit", "repoType", repoType).count();
    }

    private double getMetricInvalidatedFallbackCount(String repoType) {
        return meterRegistry.counter("thread.local.cache.fallback", "reason", "invalidated", "repoType", repoType)
                .count();
    }

    private double getMetricNotSupportedFallbackCount(String repoType) {
        return meterRegistry.counter("thread.local.cache.fallback", "reason", "notSupportedLocalOrRemote", "repoType",
                repoType).count();
    }

    private double getMetricVirtualFallbackCount(String repoType) {
        return meterRegistry.counter("thread.local.cache.fallback", "reason", "virtual", "repoType", repoType).count();
    }
}
