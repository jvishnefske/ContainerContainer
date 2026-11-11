package org.artifactory.repo.cache;

import org.artifactory.model.RepoConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author oleksandrl
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepoConfigCaffeineCacheLoaderTest {

    @Mock
    private Supplier<Integer> allReposCountFunction;
    @Mock
    private Supplier<Map<String, RepoConfig>> getAllRepoConfigsFunction;
    @Mock
    private Function<String, RepoConfig> getRepoConfigByKeyFunction;
    @Mock
    private Function<Set<String>, Map<String, RepoConfig>> getRepoConfigsByKeysFunction;

    @Test
    void load_shouldReturnRepoConfig_whenGivenKey() {
        // Arrange
        RepoConfig expectedConfig = mock(RepoConfig.class);
        when(getRepoConfigByKeyFunction.apply("repo1")).thenReturn(expectedConfig);
        RepoConfigCaffeineCacheLoader cacheLoader = new RepoConfigCaffeineCacheLoader(
                5, 1.0, allReposCountFunction, getAllRepoConfigsFunction,
                getRepoConfigByKeyFunction, getRepoConfigsByKeysFunction);
        // Act
        RepoConfig result = cacheLoader.load("repo1");
        // Assert
        assertEquals(expectedConfig, result);
        verify(getRepoConfigByKeyFunction).apply("repo1");
    }

    @Test
    void loadAll_shouldUseGetAllFunction_whenOptimizationIsApplied() {
        // Arrange
        Map<String, RepoConfig> allConfigs = new HashMap<>();
        RepoConfig config1 = mock(RepoConfig.class);
        RepoConfig config2 = mock(RepoConfig.class);
        allConfigs.put("repo1", config1);
        allConfigs.put("repo2", config2);
        when(allReposCountFunction.get()).thenReturn(100);
        when(getAllRepoConfigsFunction.get()).thenReturn(allConfigs);
        RepoConfigCaffeineCacheLoader cacheLoader = new RepoConfigCaffeineCacheLoader(
                5, 1.0, allReposCountFunction, getAllRepoConfigsFunction,
                getRepoConfigByKeyFunction, getRepoConfigsByKeysFunction);
        // Act
        Map<String, RepoConfig> result = cacheLoader.loadAll(Set.of("repo1", "repo2", "repo3", "repo4", "repo5",
                "repo6", "repo7", "repo8", "repo9", "repo10", "repo11", "repo12", "repo13", "repo14", "repo15",
                "repo16", "repo17", "repo18", "repo19", "repo20", "repo21", "repo22", "repo23", "repo24", "repo25",
                "repo26", "repo27", "repo28", "repo29", "repo30", "repo31", "repo32", "repo33", "repo34", "repo35",
                "repo36", "repo37", "repo38", "repo39", "repo40", "repo41", "repo42", "repo43", "repo44", "repo45",
                "repo46", "repo47", "repo48", "repo49", "repo50", "repo51"));
        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(getAllRepoConfigsFunction).get();
    }

    @Test
    void loadAll_shouldUseGetByKeysFunction_whenOptimizationIsNotApplied() {
        // Arrange
        Map<String, RepoConfig> configsByKeys = new HashMap<>();
        RepoConfig config1 = mock(RepoConfig.class);
        RepoConfig config2 = mock(RepoConfig.class);
        configsByKeys.put("repo1", config1);
        configsByKeys.put("repo2", config2);
        when(allReposCountFunction.get()).thenReturn(100);
        when(getRepoConfigsByKeysFunction.apply(any())).thenReturn(configsByKeys);
        RepoConfigCaffeineCacheLoader cacheLoader = new RepoConfigCaffeineCacheLoader(
                5, 1.0, allReposCountFunction, getAllRepoConfigsFunction,
                getRepoConfigByKeyFunction, getRepoConfigsByKeysFunction);
        Set<String> keys = Set.of("repo1", "repo2");
        // Act
        Map<String, RepoConfig> result = cacheLoader.loadAll(keys);
        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(getRepoConfigsByKeysFunction).apply(keys);
    }

    @ParameterizedTest
    @MethodSource("ensureNoNullsTestData")
    void ensureNoNulls_shouldReturnExpectedResult_whenGivenVariousInputs(
            Map<String, RepoConfig> input, int expectedSize) {
        // Arrange
        RepoConfigCaffeineCacheLoader cacheLoader = new RepoConfigCaffeineCacheLoader(
                5, 1.0, allReposCountFunction, getAllRepoConfigsFunction,
                getRepoConfigByKeyFunction, getRepoConfigsByKeysFunction);
        // Act
        Map<String, RepoConfig> result = cacheLoader.ensureNoNulls(input);
        // Assert
        assertNotNull(result);
        assertEquals(expectedSize, result.size());
        assertTrue(result.keySet().stream().noneMatch(Objects::isNull));
        assertTrue(result.values().stream().noneMatch(Objects::isNull));
    }

    @Test
    void ensureNoNulls_shouldReturnSameInstance_whenNoNullsPresent() {
        // Arrange
        Map<String, RepoConfig> validMap = new HashMap<>();
        RepoConfig config1 = mock(RepoConfig.class);
        RepoConfig config2 = mock(RepoConfig.class);
        validMap.put("repo1", config1);
        validMap.put("repo2", config2);
        RepoConfigCaffeineCacheLoader cacheLoader = new RepoConfigCaffeineCacheLoader(
                5, 1.0, allReposCountFunction, getAllRepoConfigsFunction,
                getRepoConfigByKeyFunction, getRepoConfigsByKeysFunction);
        // Act
        Map<String, RepoConfig> result = cacheLoader.ensureNoNulls(validMap);
        // Assert
        assertSame(validMap, result);
    }

    private static Stream<Arguments> ensureNoNullsTestData() {
        // Valid map with two entries
        Map<String, RepoConfig> validMap = new HashMap<>();
        validMap.put("repo1", mock(RepoConfig.class));
        validMap.put("repo2", mock(RepoConfig.class));
        // Map with null key
        Map<String, RepoConfig> mapWithNullKey = new HashMap<>();
        mapWithNullKey.put(null, mock(RepoConfig.class));
        mapWithNullKey.put("repo1", mock(RepoConfig.class));
        // Map with null value
        Map<String, RepoConfig> mapWithNullValue = new HashMap<>();
        mapWithNullValue.put("repo1", null);
        mapWithNullValue.put("repo2", mock(RepoConfig.class));
        // Map with both null key and value
        Map<String, RepoConfig> mapWithBothNull = new HashMap<>();
        mapWithBothNull.put(null, null);
        mapWithBothNull.put("repo1", mock(RepoConfig.class));
        // Empty map
        Map<String, RepoConfig> emptyMap = new HashMap<>();
        return Stream.of(
                // Null input should return empty map
                Arguments.of(null, 0),
                // Valid map should return all entries
                Arguments.of(validMap, 2),
                // Map with null key should filter out null key entry
                Arguments.of(mapWithNullKey, 1),
                // Map with null value should filter out null value entry
                Arguments.of(mapWithNullValue, 1),
                // Map with both null key and value should filter out null entries
                Arguments.of(mapWithBothNull, 1),
                // Empty map should return empty map
                Arguments.of(emptyMap, 0)
        );
    }
}
