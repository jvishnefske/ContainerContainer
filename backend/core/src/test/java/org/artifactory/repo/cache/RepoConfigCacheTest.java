package org.artifactory.repo.cache;

import ch.qos.logback.classic.Level;
import com.google.common.cache.LoadingCache;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.metrics.cache.GlobalCacheMetrics;
import org.artifactory.model.BasicRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.RepoConfigPropagationModel;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoConfigReload;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author vovam
 */
@SuppressWarnings("unchecked")
class RepoConfigCacheTest extends ArtifactoryHomeBoundJUnitTest {

    @BeforeEach
    void setUp() {
        getBound().setProperty(ConstantValues.enableCaffeineCache.getPropertyName(), "false");
    }

    @Test
    void constructor_shouldUseCaffeineCache_whenPropertyIsTrue() throws NoSuchFieldException, IllegalAccessException {
        // Arrange
        getBound().setProperty(ConstantValues.enableCaffeineCache.getPropertyName(), "true");
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", LocalRepoConfig.create("repo1", Generic)
        );
        // Act
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, null, null, null, null);
        // Assert
        Field caffeineCacheField = RepoConfigCache.class.getDeclaredField("caffeineCache");
        caffeineCacheField.setAccessible(true);
        assertNotNull(caffeineCacheField.get(repoConfigCache));
        Field guavaCacheField = RepoConfigCache.class.getDeclaredField("guavaCache");
        guavaCacheField.setAccessible(true);
        assertNull(guavaCacheField.get(repoConfigCache));
    }

    @Test
    void constructor_shouldUseGuavaCache_whenPropertyIsFalse() throws NoSuchFieldException, IllegalAccessException {
        // Arrange
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", LocalRepoConfig.create("repo1", Generic)
        );
        // Act
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, null, null, null, null);
        // Assert
        Field guavaCacheField = RepoConfigCache.class.getDeclaredField("guavaCache");
        guavaCacheField.setAccessible(true);
        assertNotNull(guavaCacheField.get(repoConfigCache));
        Field caffeineCacheField = RepoConfigCache.class.getDeclaredField("caffeineCache");
        caffeineCacheField.setAccessible(true);
        assertNull(caffeineCacheField.get(repoConfigCache));
    }

    @Test
    void callGetAllReposFunctionOnlyOnce() {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        RepoConfig repoConfig2 = LocalRepoConfig.create("repo2", Generic);
        when(getAllReposFunction.get())
                .thenReturn(Map.of("repo1", repoConfig1))
                .thenReturn(Map.of("repo2", repoConfig2));

        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, null, null, null, null);
        assertTrue(repoConfigCache.existByKey("repo1"));
        assertFalse(repoConfigCache.existByKey("repo2"));
        verify(getAllReposFunction, times(1)).get();
    }

    @Test
    void existByKey() {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", LocalRepoConfig.create("repo1", Generic)
        );
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, mock(Function.class), null, null, null);
        assertTrue(repoConfigCache.existByKey("repo1"));
        assertFalse(repoConfigCache.existByKey("repo2"));
    }

    @Test
    void getByKey() {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", LocalRepoConfig.create("repo1", Generic),
                "repo2", LocalRepoConfig.create("repo2", Generic)
        );
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, mock(Function.class), null, null, null);
        assertNotNull(repoConfigCache.getByKey("repo1"));
        assertEquals("repo1", repoConfigCache.getByKey("repo1").getKey());
        assertNull(repoConfigCache.getByKey("repo3"));
    }

    @Test
    void getRepoDetailsTypeByKey() {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", LocalRepoConfig.create("repo1", Generic),
                "repo2", LocalRepoConfig.create("repo2", Generic)
        );
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, mock(Function.class), null, null, null);
        assertNotNull(repoConfigCache.getRepoDetailsTypeByKey("repo1"));
        assertEquals(LOCAL, repoConfigCache.getRepoDetailsTypeByKey("repo1"));
        assertNull(repoConfigCache.getRepoDetailsTypeByKey("repo3"));
    }

    @Test
    void getRepoPackageTypeByKey() {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", LocalRepoConfig.create("repo1", Generic),
                "repo2", LocalRepoConfig.create("repo2", Generic)
        );
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, mock(Function.class), null, null, null);
        assertNotNull(repoConfigCache.getRepoPackageTypeByKey("repo1"));
        assertEquals(Generic, repoConfigCache.getRepoPackageTypeByKey("repo1"));
        assertNull(repoConfigCache.getRepoPackageTypeByKey("repo3"));
    }

    @Test
    void getByKeyWhenRepoPresentInKeysSetAndDoesNotExistInDb() {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = Map::of;
        Supplier<Map<String, BasicRepoConfig>> getAllRepoKeys =
                () -> Map.of("repo1", new BasicRepoConfig("repo1", 0L, LOCAL, Generic));
        RepoConfigCache repoConfigCache =
                new RepoConfigCache(Integer.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                        Double.MAX_VALUE, getAllReposFunction, mock(Function.class), null, null, getAllRepoKeys);
        BasicRepoConfig basicRepoConfigUpdated = new BasicRepoConfig("repo1", 1L, LOCAL, Generic);
        repoConfigCache.addAndUpdateRepoConfigKeys(Map.of("repo1", basicRepoConfigUpdated));
        // exist because repo keys set contains repo key
        assertTrue(repoConfigCache.existByKey("repo1"));
        // null because DB does not contain it
        assertNull(repoConfigCache.getByKey("repo1"));
    }

    @Test
    void findRepositoryConfigsByKeys() {
        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        RepoConfig repoConfig2 = LocalRepoConfig.create("repo2", Generic);
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", repoConfig1, "repo2", repoConfig2
        );
        RepoConfigCache repoConfigCache =
                new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                        Double.MAX_VALUE, getAllReposFunction, mock(Function.class), null, null, null);
        Collection<RepoConfig> repoConfigs =
                repoConfigCache.getRepositoryConfigsByKeys(Set.of("repo1", "repo2"));
        assertEquals(2, repoConfigs.size());
        assertEquals(repoConfig1, findRepoConfigByKey("repo1", repoConfigs));
        assertEquals(repoConfig2, findRepoConfigByKey("repo2", repoConfigs));
    }

    @Test
    void findReposByKeysWhenSomeReposDoNotExistInDb() {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        when(getAllReposFunction.get()).thenReturn(Map.of());
        Supplier<Map<String, BasicRepoConfig>> getAllRepoKeysFunction = mock(Supplier.class);
        BasicRepoConfig basicRepoConfig = new BasicRepoConfig("repo1", 1L, LOCAL, Generic);
        when(getAllRepoKeysFunction.get()).thenReturn(Map.of("repo1", basicRepoConfig));
        Function<Set<String>, Map<String, RepoConfig>> finReposByKeysFunction = mock(Function.class);
        when(finReposByKeysFunction.apply(Set.of("repo1"))).thenReturn(Map.of());
        RepoConfigCache repoConfigCache =
                new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                        Double.MAX_VALUE, getAllReposFunction, mock(Function.class), finReposByKeysFunction, null,
                        getAllRepoKeysFunction);

        assertTrue(repoConfigCache.getRepositoryConfigsByKeys(Set.of("repo1")).isEmpty());

        // 2 times - first when cache tries to load because item is not cached, second - db query fallback because of
        // the exception
        verify(finReposByKeysFunction, times(2)).apply(Set.of("repo1"));
    }

    @Test
    void findReposByKeysWhenMissedInKeySet() {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        when(getAllReposFunction.get()).thenReturn(Map.of());
        Supplier<Map<String, BasicRepoConfig>> getAllRepoKeysFunction = mock(Supplier.class);
        BasicRepoConfig basicRepoConfig1 = new BasicRepoConfig("repo1", 1L, LOCAL, Generic);
        BasicRepoConfig basicRepoConfig2 = new BasicRepoConfig("repo2", 1L, LOCAL, Generic);
        BasicRepoConfig basicRepoConfig3 = new BasicRepoConfig("repo3", 1L, LOCAL, Generic);
        when(getAllRepoKeysFunction.get()).thenReturn(
                Map.of("repo1", basicRepoConfig1, "repo2", basicRepoConfig2, "repo3", basicRepoConfig3));
        Function<Set<String>, Map<String, RepoConfig>> finReposByKeysFunction = mock(Function.class);
        when(finReposByKeysFunction.apply(Set.of("repo1", "repo4"))).thenReturn(
                Map.of("repo4", LocalRepoConfig.create("repo4", Generic)));
        RepoConfigCache repoConfigCache =
                new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                        Double.MAX_VALUE, getAllReposFunction, mock(Function.class), finReposByKeysFunction, null,
                        getAllRepoKeysFunction);

        List<RepoConfig> repoConfigs =
                new ArrayList<>(repoConfigCache.getRepositoryConfigsByKeys(Set.of("repo1", "repo4")));
        assertEquals(repoConfigs.size(), 1);
        assertEquals(repoConfigs.get(0).getKey(), "repo4");

        // 2 times - first when cache tries to load because items are not cached, second - db query fallback because of
        // the exception
        verify(finReposByKeysFunction, times(2)).apply(Set.of("repo1", "repo4"));
    }

    @Test
    void getAll() {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        RepoConfig repoConfig2 = LocalRepoConfig.create("repo2", Generic);
        when(getAllReposFunction.get()).thenReturn(Map.of("repo1", repoConfig1, "repo2", repoConfig2));

        RepoConfigCache repoConfigCache =
                new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                        Double.MAX_VALUE, getAllReposFunction, null, null, null, null);
        assertEquals(2, repoConfigCache.getAll().size());
        assertEquals(repoConfig1, findRepoConfigByKey("repo1", repoConfigCache.getAll()));
        assertEquals(repoConfig2, findRepoConfigByKey("repo2", repoConfigCache.getAll()));
    }

    @Test
    void getAllWhenRepoKeysDoNotExistInCache() {
        // given - cache (and db) contains repo1
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        when(getAllReposFunction.get()).thenReturn(Map.of("repo1", repoConfig1));
        RepoConfigCache repoConfigCache = new RepoConfigCache(Integer.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, null, mock(Function.class),
                null, null);

        // given - key set cache contains repo1, repo2
        BasicRepoConfig basicRepoConfig1 = new BasicRepoConfig("repo1", 0L, LOCAL, Generic);
        BasicRepoConfig basicRepoConfig2 = new BasicRepoConfig("repo2", 2L, LOCAL, Generic);
        repoConfigCache.addRepoConfigKeys(Map.of("repo1", basicRepoConfig1, "repo2", basicRepoConfig2));

        assertEquals(2, repoConfigCache.countAll());
        assertTrue(CollectionUtils.isEqualCollection(repoConfigCache.getAllRepoKeys(), Set.of("repo1", "repo2")));

        // then - getAll returns only repo1
        assertEquals(1, repoConfigCache.getAll().size());
        RepoConfig retrievedRepoConfig = repoConfigCache.getAll().iterator().next();
        assertEquals(repoConfig1, retrievedRepoConfig);
    }

    @Test
    void countAll() {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        RepoConfig repoConfig2 = LocalRepoConfig.create("repo2", Generic);
        when(getAllReposFunction.get()).thenReturn(Map.of("repo1", repoConfig1, "repo2", repoConfig2));
        RepoConfigCache repoConfigCache =
                new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                        Double.MAX_VALUE, getAllReposFunction, null, null, null, null);
        assertEquals(2, repoConfigCache.getAll().size());
    }

    @Test
    void addRepoConfigKeys() {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        when(getAllReposFunction.get()).thenReturn(Map.of("repo1", repoConfig1));
        RepoConfigCache repoConfigCache =
                new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                        Double.MAX_VALUE, getAllReposFunction, mock(Function.class), null,
                        null, null);

        assertTrue(repoConfigCache.existByKey("repo1"));
        assertFalse(repoConfigCache.existByKey("repo2"));
        BasicRepoConfig basicRepoConfig = new BasicRepoConfig("repo1", 1L, LOCAL, Generic);
        repoConfigCache.addRepoConfigKeys(Map.of("repo2", basicRepoConfig));
        assertTrue(repoConfigCache.existByKey("repo1"));
        assertTrue(repoConfigCache.existByKey("repo2"));
    }

    @Test
    void updateRepoConfigKeys() {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        when(getAllReposFunction.get()).thenReturn(Map.of("repo1", repoConfig1));
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("repo1")).thenReturn(repoConfig1);
        RepoConfigCache repoConfigCache =
                new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                        Double.MAX_VALUE, getAllReposFunction, findByKeyFunction, null, null, null);
        assertNotNull(repoConfigCache.getByKey("repo1"));
        verify(findByKeyFunction, times(0)).apply("repo1");
        repoConfigCache.updateRepoConfigKeys(Map.of("repo1", repoConfig1.toBasic()));
        verify(findByKeyFunction, times(0)).apply("repo1");
        assertNotNull(repoConfigCache.getByKey("repo1"));
        verify(findByKeyFunction, times(1)).apply("repo1");
    }

    @Test
    void deleteRepoConfigsByKeys() {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        RepoConfig repoConfig2 = LocalRepoConfig.create("repo2", Generic);
        Map<String, RepoConfig> map = Map.of("repo1", repoConfig1, "repo2", repoConfig2);
        when(getAllReposFunction.get()).thenReturn(map);
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction,
                mock(Function.class), mock(Function.class), null, mock(Supplier.class));
        assertTrue(repoConfigCache.existByKey("repo1"));
        assertTrue(repoConfigCache.existByKey("repo2"));
        repoConfigCache.deleteRepoConfigsByKeys(List.of(repoConfig2.getKey()));
        assertTrue(repoConfigCache.existByKey("repo1"));
        assertFalse(repoConfigCache.existByKey("repo2"));
    }

    @Test
    void concurrentAddRepoConfigsToCache() throws InterruptedException, BrokenBarrierException {
        int threadCount = 5;
        CyclicBarrier cyclicBarrier = new CyclicBarrier(threadCount + 1);
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        when(getAllReposFunction.get()).thenReturn(Map.of());

        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction,
                mock(Function.class), mock(Function.class), null, mock(Supplier.class));

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future> futures = IntStream.range(0, threadCount)
                .mapToObj(value -> executorService.submit(() -> {
                    awaitCyclicBarrier(cyclicBarrier);
                    BasicRepoConfig basicRepoConfig = new BasicRepoConfig("repoKey" + value, 0L, LOCAL, Generic);
                    repoConfigCache.addRepoConfigKeys(Map.of("repoKey" + value, basicRepoConfig));
                }))
                .collect(Collectors.toList());
        cyclicBarrier.await();
        try {
            futures.forEach(this::getFuture);
        } finally {
            executorService.shutdown();
        }
        IntStream.range(0, threadCount)
                .forEach(value -> assertTrue(repoConfigCache.existByKey("repoKey" + value)));
    }

    @Test
    void concurrentDeleteRepoConfigsFromCache() throws InterruptedException, BrokenBarrierException {
        int threadCount = 5;
        CyclicBarrier cyclicBarrier = new CyclicBarrier(threadCount + 1);
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        Map<String, RepoConfig> allRepoConfigs = new HashMap<>();
        IntStream.range(0, 5).forEach((value) -> {
            RepoConfig repoConfig = LocalRepoConfig.create("repo", Generic);
            allRepoConfigs.put("repo" + value, repoConfig);
        });
        when(getAllReposFunction.get()).thenReturn(allRepoConfigs);

        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, mock(Function.class), mock(Function.class), null,
                mock(Supplier.class));

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future> futures = IntStream
                .range(0, threadCount)
                .mapToObj(value -> executorService.submit(() -> {
                    awaitCyclicBarrier(cyclicBarrier);
                    repoConfigCache.deleteRepoConfigsByKeys(Set.of("repoKey" + value));
                }))
                .collect(Collectors.toList());
        cyclicBarrier.await();
        try {
            futures.forEach(this::getFuture);
        } finally {
            executorService.shutdown();
        }
        IntStream.range(0, threadCount)
                .forEach(value -> assertFalse(repoConfigCache.existByKey("repoKey" + value)));
    }

    @Test
    void concurrentUpdateRepoConfigsInCache() throws InterruptedException, BrokenBarrierException {
        int threadCount = 5;
        CyclicBarrier cyclicBarrier = new CyclicBarrier(threadCount + 1);
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);

        Map<String, RepoConfig> allRepoConfigs = new HashMap<>();
        IntStream.range(0, 5).forEach((value) -> {
            RepoConfig repoConfig = LocalRepoConfig.create("repoKey" + value, Generic);
            repoConfig.getBaseConfig().setDescription("original");
            allRepoConfigs.put("repoKey" + value, repoConfig);
        });
        when(getAllReposFunction.get()).thenReturn(allRepoConfigs);

        Function<String, RepoConfig> findByKeyFunction = repoKey -> {
            RepoConfig repoConfig = LocalRepoConfig.create(repoKey, Generic);
            repoConfig.getBaseConfig().setDescription("updated");
            return repoConfig;
        };

        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, findByKeyFunction, mock(Function.class), null,
                mock(Supplier.class));

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future> futures = IntStream
                .range(0, threadCount)
                .mapToObj(value -> executorService.submit(() -> {
                    awaitCyclicBarrier(cyclicBarrier);
                    BasicRepoConfig basicRepoConfig = new BasicRepoConfig("repoKey" + value, 1L, LOCAL, Docker);
                    repoConfigCache.updateRepoConfigKeys(Map.of("repoKey" + value, basicRepoConfig));
                }))
                .collect(Collectors.toList());

        cyclicBarrier.await();
        try {
            futures.forEach(this::getFuture);
        } finally {
            executorService.shutdown();
        }
        IntStream.range(0, threadCount)
                .forEach(value -> assertEquals(
                        repoConfigCache.getByKey("repoKey" + value).getBaseConfig().getDescription(), "updated"));
    }

    @RepeatedTest(5)
    void concurrentReadWriteSyncRepoKeysOperationsInCache() throws InterruptedException, BrokenBarrierException {
        Map<String, BasicRepoConfig> allRepoConfigKeys = new ConcurrentHashMap<>();
        Supplier<Map<String, BasicRepoConfig>> getAllRepoKeysFunction = mock(Supplier.class);
        when(getAllRepoKeysFunction.get()).thenReturn(allRepoConfigKeys);

        Function<String, RepoConfig> findByKeyFunction = repoKey -> {
            RepoConfig repoConfig = LocalRepoConfig.create(repoKey, Generic);
            return repoConfig;
        };

        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        when(getAllReposFunction.get()).thenReturn(Map.of());

        RepoConfigCache repoConfigCache =
                new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                        Double.MAX_VALUE, getAllReposFunction, findByKeyFunction, mock(Function.class), null,
                        getAllRepoKeysFunction);

        int threadCountPerAction = 5;
        // to sync threads that run add/update/delete operations
        CyclicBarrier cyclicBarrier = new CyclicBarrier(threadCountPerAction * 3 + 1);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCountPerAction * 3);

        // add a repo key to a cache
        List<Future> futures = IntStream
                .range(0, threadCountPerAction)
                .mapToObj(value -> executorService.submit(() -> {
                    // wait until all threads will be ready
                    awaitCyclicBarrier(cyclicBarrier);
                    // add to the set - data source for getAllRepoKeys method
                    BasicRepoConfig basicRepoConfig = new BasicRepoConfig("repoKey" + value, 1L, LOCAL, Generic);
                    allRepoConfigKeys.put("repoKey" + value, basicRepoConfig);
                    // add repo key to cache
                    repoConfigCache.addRepoConfigKeys(Map.of("repoKey" + value, basicRepoConfig));
                    // assert that repo exists
                    assertTrue(repoConfigCache.existByKey("repoKey" + value));
                    assertNotNull(repoConfigCache.getByKey("repoKey" + value));
                }))
                .collect(Collectors.toList());

        // update repo key in a cache
        futures.addAll(IntStream
                .range(threadCountPerAction * 3, threadCountPerAction * 4)
                .mapToObj(value -> executorService.submit(() -> {
                    // add a repo to execute an update operation
                    BasicRepoConfig basicRepoConfig = new BasicRepoConfig("repoKey" + value, 1L, LOCAL, Generic);
                    allRepoConfigKeys.put("repoKey" + value, basicRepoConfig);
                    repoConfigCache.addRepoConfigKeys(Map.of("repoKey" + value, basicRepoConfig));
                    // wait until all threads will be ready
                    awaitCyclicBarrier(cyclicBarrier);
                    repoConfigCache.updateRepoConfigKeys(Map.of("repoKey" + value, basicRepoConfig));
                    assertNotNull(repoConfigCache.getByKey("repoKey" + value));
                }))
                .toList());

        futures.addAll(IntStream
                .range(threadCountPerAction, threadCountPerAction * 2)
                .mapToObj(value -> executorService.submit(() -> {
                    // add a repo to execute a remove operation
                    BasicRepoConfig basicRepoConfig = new BasicRepoConfig("repoKey" + value, 1L, LOCAL, Generic);
                    allRepoConfigKeys.put("repoKey" + value, basicRepoConfig);
                    repoConfigCache.addRepoConfigKeys(Map.of("repoKey" + value, basicRepoConfig));
                    // wait until all threads will be ready
                    awaitCyclicBarrier(cyclicBarrier);
                    allRepoConfigKeys.remove("repoKey" + value);
                    repoConfigCache.deleteRepoConfigsByKeys(Set.of("repoKey" + value));
                    assertFalse(repoConfigCache.existByKey("repoKey" + value));
                }))
                .toList());

        cyclicBarrier.await();

        try {
            futures.forEach(this::getFuture);
        } finally {
            executorService.shutdown();
        }
        IntStream
                .range(0, threadCountPerAction * 2)
                .forEach(value -> repoConfigCache.existByKey("repoKey" + value));
    }

    @Test
    void syncRepoCacheByModificationDateWhenModificationIsNotChanged() {
        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(RepoConfigCache.class, Level.DEBUG);
        // given - cache contains repo without modification date
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        RepoConfig repoConfigWithoutModificationDate = LocalRepoConfig.create("repo", Generic);
        when(getAllReposFunction.get()).thenReturn(Map.of("repo", repoConfigWithoutModificationDate));

        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);

        // given - db contains repo without modification date
        Supplier<Map<String, BasicRepoConfig>> getAllRepoKeys = mock(Supplier.class);
        BasicRepoConfig basicRepoConfig = new BasicRepoConfig("repo", 0L, LOCAL, Generic);
        when(getAllRepoKeys.get()).thenReturn(Map.of("repo", basicRepoConfig));

        Consumer<RepoConfigPropagationModel> repoConfigurationsFunction = mock(Consumer.class);

        RepoConfigCache repoConfigCache = new RepoConfigCache(Integer.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, findByKeyFunction, null, repoConfigurationsFunction,
                getAllRepoKeys);

        RepoConfig repoConfigOriginal = repoConfigCache.getByKey("repo");
        assertNotNull(repoConfigOriginal);
        assertEquals(LOCAL, repoConfigCache.getRepoDetailsTypeByKey("repo"));
        assertEquals(0L, repoConfigOriginal.getModification());
        verify(findByKeyFunction, times(0)).apply("repo");

        // when
        repoConfigCache.syncRepoConfigKeys();

        // then
        verify(getAllRepoKeys, atLeastOnce()).get();
        verify(repoConfigurationsFunction, times(0)).accept(any());
        verify(findByKeyFunction, times(0)).apply("repo");
        assertEquals(0, appender.list.stream().filter(e -> e.getMessage()
                .contains("Modification date for repo {} is changed.")).count());
    }

    @Test
    void syncRepoCacheWhenModificationIsChanged() {
        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(RepoConfigCache.class, Level.DEBUG);
        // given - cache contains repo without modification date
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        RepoConfig repoConfigWithoutModificationDate = LocalRepoConfig.create("repo", Generic);
        when(getAllReposFunction.get()).thenReturn(Map.of("repo", repoConfigWithoutModificationDate));

        // given - db contains repo with modification date
        RepoConfig repoConfigWithModificationDate = LocalRepoConfig.create("repo", Generic);
        repoConfigWithModificationDate.setModification(1L);
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("repo")).thenReturn(repoConfigWithModificationDate);

        Supplier<Map<String, BasicRepoConfig>> getAllRepoKeys = mock(Supplier.class);
        BasicRepoConfig basicRepoConfig = new BasicRepoConfig("repo", 1L, LOCAL, Generic);
        when(getAllRepoKeys.get()).thenReturn(Map.of("repo", basicRepoConfig));

        Consumer<RepoConfigPropagationModel> repoConfigurationsFunction = mock(Consumer.class);
        RepoConfigCache repoConfigCache = new RepoConfigCache(Integer.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, findByKeyFunction, null, repoConfigurationsFunction,
                getAllRepoKeys);

        RepoConfig repoConfigOriginal = repoConfigCache.getByKey("repo");
        assertNotNull(repoConfigOriginal);
        assertEquals(0L, repoConfigOriginal.getModification());
        verify(findByKeyFunction, times(0)).apply("repo");

        // when
        repoConfigCache.syncRepoConfigKeys();

        // then - call reload with REPO_UPDATE action
        verify(getAllRepoKeys, atLeastOnce()).get();
        ArgumentCaptor<RepoConfigPropagationModel> captor = ArgumentCaptor.forClass(RepoConfigPropagationModel.class);
        verify(repoConfigurationsFunction, atLeastOnce()).accept(captor.capture());
        RepoConfigPropagationModel propagationModel = captor.getValue();
        Set<RepoConfigReload> repoConfigCreateOrUpdate = propagationModel.getRepoConfigCreateOrUpdate();
        assertEquals(1, propagationModel.getRepoConfigCreateOrUpdate().size());
        assertEquals(0, propagationModel.getRepoConfigDelete().size());
        RepoConfigReload configReload = repoConfigCreateOrUpdate.iterator().next();
        assertEquals("repo", configReload.getRepoKey());
        assertEquals(LOCAL, configReload.getRepoType());
        assertEquals(Generic, configReload.getPackageType());
        assertEquals(REPO_UPDATE, configReload.getConfigAction());
        assertEquals(1L, configReload.getModification());
        assertEquals(1, appender.list.stream().filter(e -> e.toString()
                .contains("Modification date for repo repo is changed.")).count());
        assertEquals(1, appender.list.stream().filter(e -> e.getMessage()
                .contains("Modification date for repo {} is changed.")).count());
    }

    @Test
    void syncRepoCacheWhenItemsAddedDeleted() {
        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(RepoConfigCache.class, Level.DEBUG);
        // given
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        RepoConfig repoConfig2 = LocalRepoConfig.create("repo2", Generic);
        RepoConfig repoConfig3 = LocalRepoConfig.create("repo3", Generic);
        when(getAllReposFunction.get()).thenReturn(
                Map.of("repo1", repoConfig1, "repo2", repoConfig2, "repo3", repoConfig3));

        // given
        RepoConfig repoConfig4 = LocalRepoConfig.create("repo4", Generic);
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("repo2")).thenReturn(repoConfig2);
        when(findByKeyFunction.apply("repo4")).thenReturn(repoConfig4);

        Supplier<Map<String, BasicRepoConfig>> getAllBasicRepos = mock(Supplier.class);
        BasicRepoConfig basicRepoConfig1 = new BasicRepoConfig("repo1", 0L, LOCAL, Generic);
        BasicRepoConfig basicRepoConfig2 = new BasicRepoConfig("repo2", 1L, LOCAL, Generic);
        BasicRepoConfig basicRepoConfig4 = new BasicRepoConfig("repo4", 0L, LOCAL, Generic);
        when(getAllBasicRepos.get()).thenReturn(
                Map.of("repo1", basicRepoConfig1, "repo2", basicRepoConfig2, "repo4", basicRepoConfig4));

        Consumer<RepoConfigPropagationModel> repoConfigurationsFunction = mock(Consumer.class);
        RepoConfigCache repoConfigCache = new RepoConfigCache(Integer.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, findByKeyFunction, null, repoConfigurationsFunction,
                getAllBasicRepos);

        assertNull(repoConfigCache.getByKey("repo4"));
        assertNull(repoConfigCache.getRepoDetailsTypeByKey("repo4"));
        verify(findByKeyFunction, times(0)).apply("repo4");
        assertEquals(3, repoConfigCache.getAllRepoKeys().size());
        assertTrue(repoConfigCache.existByKey("repo3"));

        // when
        repoConfigCache.syncRepoConfigKeys();

        // then
        verify(getAllBasicRepos, atLeastOnce()).get();
        assertEquals(3, repoConfigCache.getAllRepoKeys().size());
        assertTrue(repoConfigCache.existByKey("repo1"));
        assertTrue(repoConfigCache.existByKey("repo2"));
        assertTrue(repoConfigCache.existByKey("repo4"));
        assertFalse(repoConfigCache.existByKey("repo3"));
        verify(findByKeyFunction, times(0)).apply("repo1");
        verify(findByKeyFunction, times(0)).apply("repo2");
        verify(findByKeyFunction, times(0)).apply("repo3");
        verify(findByKeyFunction, times(0)).apply("repo4");

        assertNotNull(repoConfigCache.getByKey("repo4"));
        assertNotNull(repoConfigCache.getRepoDetailsTypeByKey("repo4"));
        verify(findByKeyFunction, times(1)).apply("repo4");
        assertEquals(1, appender.list.stream().filter(e -> e.toString()
                .contains("Modification date for repo repo2 is changed.")).count());
        assertEquals(1, appender.list.stream().filter(e -> e.getMessage()
                .contains("Modification date for repo {} is changed.")).count());
    }

    @Test
    void syncRepoCacheWhenRepoDoesNotExistInDB() {
        // given - cache contains repo
        RepoConfig repoConfig = LocalRepoConfig.create("repo", Generic);
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        when(getAllReposFunction.get()).thenReturn(Map.of("repo", repoConfig));

        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("repo")).thenReturn(repoConfig);

        // given - db does not contain repo
        Supplier<Map<String, BasicRepoConfig>> getAllRepoKeys = mock(Supplier.class);
        when(getAllRepoKeys.get()).thenReturn(Map.of());

        Consumer<RepoConfigPropagationModel> repoConfigurationsFunction = mock(Consumer.class);

        RepoConfigCache repoConfigCache = new RepoConfigCache(Integer.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, findByKeyFunction, null, repoConfigurationsFunction,
                getAllRepoKeys);

        RepoConfig repoConfigOriginal = repoConfigCache.getByKey("repo");
        assertNotNull(repoConfigOriginal);
        verify(findByKeyFunction, times(0)).apply("repo");

        // when
        repoConfigCache.syncRepoConfigKeys();

        // then - call reload with REPO_DELETE action
        verify(getAllRepoKeys, atLeastOnce()).get();
        ArgumentCaptor<RepoConfigPropagationModel> captor = ArgumentCaptor.forClass(RepoConfigPropagationModel.class);
        verify(repoConfigurationsFunction, atLeastOnce()).accept(captor.capture());
        RepoConfigPropagationModel propagationModel = captor.getValue();
        assertEquals(0, propagationModel.getRepoConfigCreateOrUpdate().size());
        assertEquals(1, propagationModel.getRepoConfigDelete().size());
        RepoConfigReload repoConfigDeletion = propagationModel.getRepoConfigDelete().iterator().next();
        assertEquals("repo", repoConfigDeletion.getRepoKey());
        assertEquals(LOCAL, repoConfigDeletion.getRepoType());
        assertEquals(Generic, repoConfigDeletion.getPackageType());
        assertEquals(RepoConfigChange.ConfigAction.REPO_DELETE, repoConfigDeletion.getConfigAction());
    }

    @Test
    void syncRepoCacheWhenRepoExistInDbButNotInCache() {
        // given - cache does not contain repo
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        when(getAllReposFunction.get()).thenReturn(Map.of());
        Supplier<Map<String, BasicRepoConfig>> getAllRepoKeys = mock(Supplier.class);
        BasicRepoConfig basicRepoConfig = new BasicRepoConfig("repo", 1L, LOCAL, Generic);
        when(getAllRepoKeys.get()).thenReturn(Map.of("repo", basicRepoConfig));

        // given - db contains repo
        Function<String, RepoConfig> repoConfigByKeyFunction = mock(Function.class);
        RepoConfig repoConfig = LocalRepoConfig.create("repo", Generic);
        when(repoConfigByKeyFunction.apply("repo")).thenReturn(repoConfig);

        Consumer<RepoConfigPropagationModel> repoConfigurationsFunction = mock(Consumer.class);

        RepoConfigCache repoConfigCache = spy(new RepoConfigCache(Integer.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, repoConfigByKeyFunction, null, repoConfigurationsFunction,
                getAllRepoKeys));

        // when
        repoConfigCache.syncRepoConfigKeys();

        // then - call reload with REPO_CREATE action
        verify(getAllRepoKeys, atLeastOnce()).get();
        ArgumentCaptor<RepoConfigPropagationModel> captor = ArgumentCaptor.forClass(RepoConfigPropagationModel.class);
        verify(repoConfigurationsFunction, atLeastOnce()).accept(captor.capture());
        RepoConfigPropagationModel propagationModel = captor.getValue();
        assertEquals(1, propagationModel.getRepoConfigCreateOrUpdate().size());
        assertEquals(0, propagationModel.getRepoConfigDelete().size());
        RepoConfigReload configReload = propagationModel.getRepoConfigCreateOrUpdate().iterator().next();
        assertEquals("repo", configReload.getRepoKey());
        assertEquals(LOCAL, configReload.getRepoType());
        assertEquals(Generic, configReload.getPackageType());
        assertEquals(RepoConfigChange.ConfigAction.REPO_CREATE, configReload.getConfigAction());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testRepoConfigCacheRecordStatsEnabled(boolean recordStatsEnabled)
            throws NoSuchFieldException, IllegalAccessException, ExecutionException {
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        RepoConfig repoConfig = LocalRepoConfig.create("repo1", Generic);
        when(getAllReposFunction.get()).thenReturn(Map.of("repo1", repoConfig));
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoConfigCacheRecordStatsEnabled.getPropertyName(),
                Boolean.toString(recordStatsEnabled));
        try (MockedStatic<GlobalCacheMetrics> globalCacheMetrics = mockStatic(GlobalCacheMetrics.class)) {
            RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                    Double.MAX_VALUE, getAllReposFunction, null, null, null, null);
            Field cacheField = RepoConfigCache.class.getDeclaredField("guavaCache");
            cacheField.setAccessible(true);
            LoadingCache<String, RepoConfig> cache = (LoadingCache<String, RepoConfig>) cacheField.get(repoConfigCache);

            cache.get("repo1");

            assertEquals(cache.stats().hitCount(), recordStatsEnabled ? 1 : 0);
            globalCacheMetrics.verify(() -> GlobalCacheMetrics.monitorGuava(any(), any(), any()),
                    times(recordStatsEnabled ? 1 : 0));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testRepoConfigCacheRecordStatsEnabled_withCaffeine(boolean recordStatsEnabled)
            throws NoSuchFieldException, IllegalAccessException {
        // Arrange
        getBound().setProperty(ConstantValues.enableCaffeineCache.getPropertyName(), "true");
        Supplier<Map<String, RepoConfig>> getAllReposFunction = mock(Supplier.class);
        RepoConfig repoConfig = LocalRepoConfig.create("repo1", Generic);
        when(getAllReposFunction.get()).thenReturn(Map.of("repo1", repoConfig));
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoConfigCacheRecordStatsEnabled.getPropertyName(),
                Boolean.toString(recordStatsEnabled));
        // Act & Assert
        try (MockedStatic<GlobalCacheMetrics> globalCacheMetrics = mockStatic(GlobalCacheMetrics.class)) {
            RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                    Double.MAX_VALUE, getAllReposFunction, null, null, null, null);
            Field cacheField = RepoConfigCache.class.getDeclaredField("caffeineCache");
            cacheField.setAccessible(true);
            com.github.benmanes.caffeine.cache.LoadingCache<String, RepoConfig> cache =
                    (com.github.benmanes.caffeine.cache.LoadingCache<String, RepoConfig>) cacheField
                            .get(repoConfigCache);
            cache.get("repo1");
            assertEquals(cache.stats().hitCount(), recordStatsEnabled ? 1 : 0);
            globalCacheMetrics.verify(() -> GlobalCacheMetrics.monitorCaffeine(any(), any(), any()),
                    times(recordStatsEnabled ? 1 : 0));
        }
    }

    @Test
    void getByKey_withCaffeine_shouldReturnRepoConfig_whenCached() {
        // Arrange
        getBound().setProperty(ConstantValues.enableCaffeineCache.getPropertyName(), "true");
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", LocalRepoConfig.create("repo1", Generic)
        );
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, null, null, null, null);
        // Act
        RepoConfig result = repoConfigCache.getByKey("repo1");
        // Assert
        assertNotNull(result);
        assertEquals("repo1", result.getKey());
    }

    @Test
    void getByKey_withCaffeine_shouldReturnNull_whenNotInKeySet() {
        // Arrange
        getBound().setProperty(ConstantValues.enableCaffeineCache.getPropertyName(), "true");
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", LocalRepoConfig.create("repo1", Generic)
        );
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, null, null, null, null);
        // Act
        RepoConfig result = repoConfigCache.getByKey("repo-nonexistent");
        // Assert
        assertNull(result);
    }

    @Test
    void getByKey_withCaffeine_shouldFallbackToDb_whenCacheLoaderReturnsNull() {
        // Arrange
        getBound().setProperty(ConstantValues.enableCaffeineCache.getPropertyName(), "true");
        Supplier<Map<String, RepoConfig>> getAllRepoConfigsFunction = Map::of;
        var allBasicRepoConfigsMap = Map.of("repo1", new BasicRepoConfig("repo1", 0L, LOCAL, Generic));
        Supplier<Map<String, BasicRepoConfig>> getAllBasicRepoConfigsFunction =
                () -> allBasicRepoConfigsMap;
        Function<String, RepoConfig> getRepoConfigByKeyFunctionMock = mock(Function.class);
        when(getRepoConfigByKeyFunctionMock.apply("repo1")).thenReturn(null);
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllRepoConfigsFunction, getRepoConfigByKeyFunctionMock, null,
                null, getAllBasicRepoConfigsFunction);
        ReflectionTestUtils.setField(repoConfigCache, "cachedBasicRepoConfigs",
                new ConcurrentHashMap<>(allBasicRepoConfigsMap));
        // Act
        RepoConfig result = repoConfigCache.getByKey("repo1");
        // Assert
        assertNull(result);
        verify(getRepoConfigByKeyFunctionMock, times(2)).apply("repo1");
    }

    @Test
    void getRepositoryConfigsByKeys_withCaffeine_shouldReturnConfigs() {
        // Arrange
        getBound().setProperty(ConstantValues.enableCaffeineCache.getPropertyName(), "true");
        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        RepoConfig repoConfig2 = LocalRepoConfig.create("repo2", Generic);
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", repoConfig1, "repo2", repoConfig2
        );
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, null, null, null, null);
        // Act
        Collection<RepoConfig> result = repoConfigCache.getRepositoryConfigsByKeys(Set.of("repo1", "repo2"));
        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void getAll_withCaffeine_shouldReturnAllConfigs() {
        // Arrange
        getBound().setProperty(ConstantValues.enableCaffeineCache.getPropertyName(), "true");
        RepoConfig repoConfig1 = LocalRepoConfig.create("repo1", Generic);
        RepoConfig repoConfig2 = LocalRepoConfig.create("repo2", Generic);
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", repoConfig1, "repo2", repoConfig2
        );
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, null, null, null, null);
        // Act
        Collection<RepoConfig> result = repoConfigCache.getAll();
        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void updateRepoConfigKeys_withCaffeine_shouldInvalidateCache() {
        // Arrange
        getBound().setProperty(ConstantValues.enableCaffeineCache.getPropertyName(), "true");
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", LocalRepoConfig.create("repo1", Generic)
        );
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        RepoConfig updatedConfig = LocalRepoConfig.create("repo1", Docker);
        when(findByKeyFunction.apply("repo1")).thenReturn(updatedConfig);
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, findByKeyFunction, null, null, null);
        // Act
        assertNotNull(repoConfigCache.getByKey("repo1"));
        verify(findByKeyFunction, times(0)).apply("repo1");
        repoConfigCache.updateRepoConfigKeys(Map.of("repo1", new BasicRepoConfig("repo1", 1L, LOCAL, Docker)));
        RepoConfig result = repoConfigCache.getByKey("repo1");
        // Assert
        assertNotNull(result);
        assertEquals(Docker, result.getPackageType());
        verify(findByKeyFunction, times(1)).apply("repo1");
    }

    @Test
    void deleteRepoConfigsByKeys_withCaffeine_shouldRemoveFromCache() {
        // Arrange
        getBound().setProperty(ConstantValues.enableCaffeineCache.getPropertyName(), "true");
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", LocalRepoConfig.create("repo1", Generic),
                "repo2", LocalRepoConfig.create("repo2", Generic)
        );
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, null, null, null, null);
        // Act
        assertTrue(repoConfigCache.existByKey("repo1"));
        assertTrue(repoConfigCache.existByKey("repo2"));
        repoConfigCache.deleteRepoConfigsByKeys(Set.of("repo2"));
        // Assert
        assertTrue(repoConfigCache.existByKey("repo1"));
        assertFalse(repoConfigCache.existByKey("repo2"));
    }

    @Test
    void getMappedBasicRepoConfigByKey_shouldReturnNull_whenRepoKeyIsNull() {
        // Arrange
        Supplier<Map<String, RepoConfig>> getAllReposFunction = () -> Map.of(
                "repo1", LocalRepoConfig.create("repo1", Generic)
        );
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, null, null, null, null);
        // Act
        assertNull(repoConfigCache.getRepoDetailsTypeByKey(null));
        assertNull(repoConfigCache.getRepoPackageTypeByKey(null));
    }

    @Test
    void createCacheLoadErrorMessage_shouldTruncate_whenMoreThan20Keys() {
        // Arrange
        getBound().setProperty(ConstantValues.enableCaffeineCache.getPropertyName(), "true");
        Supplier<Map<String, RepoConfig>> getAllReposFunction = Map::of;
        Supplier<Map<String, BasicRepoConfig>> getAllRepoKeysFunction = () -> {
            Map<String, BasicRepoConfig> keys = new HashMap<>();
            for (int i = 1; i <= 25; i++) {
                keys.put("repo" + i, new BasicRepoConfig("repo" + i, 0L, LOCAL, Generic));
            }
            return keys;
        };
        Function<Set<String>, Map<String, RepoConfig>> getByKeysFunction = mock(Function.class);
        when(getByKeysFunction.apply(any())).thenThrow(new RuntimeException("DB error"));
        RepoConfigCache repoConfigCache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
                Double.MAX_VALUE, getAllReposFunction, null, getByKeysFunction, null, getAllRepoKeysFunction);
        // Act & Assert
        Set<String> allKeys = getAllRepoKeysFunction.get().keySet();
        try {
            repoConfigCache.getRepositoryConfigsByKeys(allKeys);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("..."));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getByKey_withFallbackEnabled_shouldQueryDb_whenNotInCache(boolean useCaffeine) {
        getBound().setProperty(
                ConstantValues.enableCaffeineCache.getPropertyName(),
                Boolean.toString(useCaffeine));
        getBound().setProperty(
                ConstantValues.repoConfigCacheDbFallbackOnMissEnabled.getPropertyName(),
                "true");
        RepoConfig repoInDb = LocalRepoConfig.create("repo1", Generic);
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("repo1")).thenReturn(repoInDb);

        RepoConfigCache cache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE,
                Integer.MAX_VALUE, Double.MAX_VALUE, Map::of, findByKeyFunction,
                null, null, null);

        RepoConfig result = cache.getByKey("repo1");
        assertNotNull(result);
        assertEquals("repo1", result.getKey());
        verify(findByKeyFunction, times(1)).apply("repo1");

        RepoConfig result2 = cache.getByKey("repo1");
        assertNotNull(result2);
        verify(findByKeyFunction, times(1)).apply("repo1");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getByKey_withFallbackEnabled_shouldReturnNull_whenNotInDb(
            boolean useCaffeine) {
        getBound().setProperty(
                ConstantValues.enableCaffeineCache.getPropertyName(),
                Boolean.toString(useCaffeine));
        getBound().setProperty(
                ConstantValues.repoConfigCacheDbFallbackOnMissEnabled.getPropertyName(),
                "true");
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("missing")).thenReturn(null);

        RepoConfigCache cache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE,
                Integer.MAX_VALUE, Double.MAX_VALUE, Map::of, findByKeyFunction,
                null, null, null);

        assertNull(cache.getByKey("missing"));
        verify(findByKeyFunction, times(1)).apply("missing");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getByKey_negativeCachePreventsRepeatedDbQueries(boolean useCaffeine) {
        getBound().setProperty(
                ConstantValues.enableCaffeineCache.getPropertyName(),
                Boolean.toString(useCaffeine));
        getBound().setProperty(
                ConstantValues.repoConfigCacheDbFallbackOnMissEnabled.getPropertyName(),
                "true");
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("missing")).thenReturn(null);

        RepoConfigCache cache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE,
                Integer.MAX_VALUE, Double.MAX_VALUE, Map::of, findByKeyFunction,
                null, null, null);

        assertNull(cache.getByKey("missing"));
        assertNull(cache.getByKey("missing"));
        assertNull(cache.getByKey("missing"));
        verify(findByKeyFunction, times(1)).apply("missing");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void existByKey_withFallbackEnabled_shouldQueryDb_whenNotInCache(
            boolean useCaffeine) {
        getBound().setProperty(
                ConstantValues.enableCaffeineCache.getPropertyName(),
                Boolean.toString(useCaffeine));
        getBound().setProperty(
                ConstantValues.repoConfigCacheDbFallbackOnMissEnabled.getPropertyName(),
                "true");
        RepoConfig repoInDb = LocalRepoConfig.create("repo1", Generic);
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("repo1")).thenReturn(repoInDb);

        RepoConfigCache cache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE,
                Integer.MAX_VALUE, Double.MAX_VALUE, Map::of, findByKeyFunction,
                null, null, null);

        assertTrue(cache.existByKey("repo1"));
        verify(findByKeyFunction, times(1)).apply("repo1");

        assertTrue(cache.existByKey("repo1"));
        verify(findByKeyFunction, times(1)).apply("repo1");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void existByKey_negativeCachePreventsRepeatedDbQueries(boolean useCaffeine) {
        getBound().setProperty(
                ConstantValues.enableCaffeineCache.getPropertyName(),
                Boolean.toString(useCaffeine));
        getBound().setProperty(
                ConstantValues.repoConfigCacheDbFallbackOnMissEnabled.getPropertyName(),
                "true");
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("missing")).thenReturn(null);

        RepoConfigCache cache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE,
                Integer.MAX_VALUE, Double.MAX_VALUE, Map::of, findByKeyFunction,
                null, null, null);

        assertFalse(cache.existByKey("missing"));
        assertFalse(cache.existByKey("missing"));
        verify(findByKeyFunction, times(1)).apply("missing");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getMappedBasicRepoConfig_withFallbackEnabled_shouldQueryDb(
            boolean useCaffeine) {
        getBound().setProperty(
                ConstantValues.enableCaffeineCache.getPropertyName(),
                Boolean.toString(useCaffeine));
        getBound().setProperty(
                ConstantValues.repoConfigCacheDbFallbackOnMissEnabled.getPropertyName(),
                "true");
        RepoConfig repoInDb = LocalRepoConfig.create("repo1", Generic);
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("repo1")).thenReturn(repoInDb);

        RepoConfigCache cache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE,
                Integer.MAX_VALUE, Double.MAX_VALUE, Map::of, findByKeyFunction,
                null, null, null);

        assertEquals(LOCAL, cache.getRepoDetailsTypeByKey("repo1"));
        assertEquals(Generic, cache.getRepoPackageTypeByKey("repo1"));
        verify(findByKeyFunction, times(1)).apply("repo1");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deleteRepoConfigsByKeys_withFallbackEnabled_negativeCachePreventsResurrection(
            boolean useCaffeine) {
        getBound().setProperty(
                ConstantValues.enableCaffeineCache.getPropertyName(),
                Boolean.toString(useCaffeine));
        getBound().setProperty(
                ConstantValues.repoConfigCacheDbFallbackOnMissEnabled.getPropertyName(),
                "true");
        RepoConfig repo1 = LocalRepoConfig.create("repo1", Generic);
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("repo1")).thenReturn(repo1);

        RepoConfigCache cache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE,
                Integer.MAX_VALUE, Double.MAX_VALUE,
                () -> Map.of("repo1", repo1), findByKeyFunction,
                mock(Function.class), null, mock(Supplier.class));

        assertTrue(cache.existByKey("repo1"));
        cache.deleteRepoConfigsByKeys(Set.of("repo1"));

        assertFalse(cache.existByKey("repo1"));
        assertNull(cache.getByKey("repo1"));
        verify(findByKeyFunction, times(0)).apply("repo1");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void addRepoConfigKeys_withFallbackEnabled_clearsNegativeCache(
            boolean useCaffeine) {
        getBound().setProperty(
                ConstantValues.enableCaffeineCache.getPropertyName(),
                Boolean.toString(useCaffeine));
        getBound().setProperty(
                ConstantValues.repoConfigCacheDbFallbackOnMissEnabled.getPropertyName(),
                "true");
        RepoConfig repoInDb = LocalRepoConfig.create("repo1", Generic);
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("repo1")).thenReturn(null)
                .thenReturn(repoInDb);

        RepoConfigCache cache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE,
                Integer.MAX_VALUE, Double.MAX_VALUE, Map::of, findByKeyFunction,
                null, null, null);

        assertFalse(cache.existByKey("repo1"));
        verify(findByKeyFunction, times(1)).apply("repo1");

        assertFalse(cache.existByKey("repo1"));
        verify(findByKeyFunction, times(1)).apply("repo1");

        BasicRepoConfig basic =
                new BasicRepoConfig("repo1", 0L, LOCAL, Generic);
        cache.addRepoConfigKeys(Map.of("repo1", basic));

        assertTrue(cache.existByKey("repo1"));
        verify(findByKeyFunction, times(1)).apply("repo1");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void addAndUpdateRepoConfigKeys_withFallbackEnabled_clearsNegativeCache(
            boolean useCaffeine) {
        getBound().setProperty(
                ConstantValues.enableCaffeineCache.getPropertyName(),
                Boolean.toString(useCaffeine));
        getBound().setProperty(
                ConstantValues.repoConfigCacheDbFallbackOnMissEnabled.getPropertyName(),
                "true");
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("repo1")).thenReturn(null);

        RepoConfigCache cache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE,
                Integer.MAX_VALUE, Double.MAX_VALUE, Map::of, findByKeyFunction,
                null, null, null);

        assertFalse(cache.existByKey("repo1"));
        verify(findByKeyFunction, times(1)).apply("repo1");

        BasicRepoConfig basic =
                new BasicRepoConfig("repo1", 0L, LOCAL, Generic);
        cache.addAndUpdateRepoConfigKeys(Map.of("repo1", basic));

        assertTrue(cache.existByKey("repo1"));
        verify(findByKeyFunction, times(1)).apply("repo1");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void syncRepoConfigKeys_clearsNegativeCache(boolean useCaffeine) {
        getBound().setProperty(
                ConstantValues.enableCaffeineCache.getPropertyName(),
                Boolean.toString(useCaffeine));
        getBound().setProperty(
                ConstantValues.repoConfigCacheDbFallbackOnMissEnabled.getPropertyName(),
                "true");
        RepoConfig repoInDb = LocalRepoConfig.create("repo1", Generic);
        Function<String, RepoConfig> findByKeyFunction = mock(Function.class);
        when(findByKeyFunction.apply("repo1"))
                .thenReturn(null)
                .thenReturn(repoInDb);

        Supplier<Map<String, BasicRepoConfig>> getAllBasicRepos =
                mock(Supplier.class);
        BasicRepoConfig basic =
                new BasicRepoConfig("repo1", 0L, LOCAL, Generic);
        when(getAllBasicRepos.get()).thenReturn(Map.of("repo1", basic));

        RepoConfigCache cache = new RepoConfigCache(Long.MAX_VALUE, Long.MAX_VALUE,
                Integer.MAX_VALUE, Double.MAX_VALUE, Map::of, findByKeyFunction,
                null, mock(Consumer.class), getAllBasicRepos);

        assertNull(cache.getByKey("repo1"));
        verify(findByKeyFunction, times(1)).apply("repo1");

        assertNull(cache.getByKey("repo1"));
        verify(findByKeyFunction, times(1)).apply("repo1");

        cache.syncRepoConfigKeys();

        RepoConfig result = cache.getByKey("repo1");
        assertNotNull(result);
        verify(findByKeyFunction, times(2)).apply("repo1");
    }

    private RepoConfig findRepoConfigByKey(String key, Collection<RepoConfig> repoConfigs) {
        return repoConfigs.stream()
                .filter(config -> config.getKey().equals(key))
                .findFirst()
                .orElse(null);
    }

    private void awaitCyclicBarrier(CyclicBarrier cyclicBarrier) {
        try {
            cyclicBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            throw new RuntimeException(e);
        }
    }

    private void getFuture(Future future) {
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}