package org.artifactory.repo.cache;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.artifactory.repo.cache.RepoConfigCacheLoaderOptimizationUtils.shouldGetAllOptimizationBeApplied;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author oleksandrl
 */
class RepoConfigCacheLoaderOptimizationUtilsTest {

    @ParameterizedTest
    @MethodSource("getAllOptimizationRules")
    void shouldGetAllOptimizationBeApplied_shouldReturnExpectedResult_whenGivenVariousScenarios(
            int keysToLoad, int allRepoKeys, int batchSize, double optimizationRatio, boolean expectedResult) {
        // Act
        boolean result = shouldGetAllOptimizationBeApplied(keysToLoad, () -> allRepoKeys, batchSize,
                optimizationRatio);
        // Assert
        assertEquals(expectedResult, result);
    }

    private static Stream<Arguments> getAllOptimizationRules() {
        return Stream.of(
                // keys to fetch <= batch - optimization should not be applied
                Arguments.of(1, 100, 5, Double.MAX_VALUE, false),
                // Optimization ratio is 0 - always apply optimization
                Arguments.of(1, 100, 5, 0, true),
                // keys to fetch = batch - optimization should not be applied
                Arguments.of(5, 100, 5, Double.MAX_VALUE, false),
                // 2 batches useful, 18 batches unuseful, ratio = 0.11 < 1.0
                Arguments.of(10, 100, 5, 1.0, false),
                // 10 batches useful, 10 batches unuseful, ratio = 1.0 >= 1.0
                Arguments.of(50, 100, 5, 1.0, true),
                // 11 batches useful, 9 batches unuseful, ratio = 1.22 >= 1.0
                Arguments.of(51, 100, 5, 1.0, true),
                // 11 batches useful, 9 batches unuseful, ratio = 1.22 < 1.25
                Arguments.of(51, 100, 5, 1.25, false),
                // 12 batches useful, 8 batches unuseful, ratio = 1.5 >= 1.0
                Arguments.of(60, 100, 5, 1.0, true),
                // Negative optimization ratio - optimization should not be applied
                Arguments.of(60, 100, 5, -1, false),
                // 20 batches useful, 0 batches unuseful - useful >= all batches
                Arguments.of(99, 100, 5, Double.MAX_VALUE, true),
                // All keys requested - keys to load >= all repo keys
                Arguments.of(100, 100, 5, Double.MAX_VALUE, true),
                // More keys requested than exist - keys to load >= all repo keys
                Arguments.of(110, 100, 5, Double.MAX_VALUE, true),
                // Large scale: 20 batches useful, 80 batches unuseful, ratio = 0.25 < 0.5
                Arguments.of(10000, 50000, 500, 0.5, false),
                // Large scale: 40 batches useful, 60 batches unuseful, ratio = 0.66 >= 0.5
                Arguments.of(20000, 50000, 500, 0.5, true),
                // Large scale: 49 batches useful, 51 batches unuseful, ratio = 0.96 < 1.0
                Arguments.of(24500, 50000, 500, 1.0, false),
                // Large scale: 50 batches useful, 50 batches unuseful, ratio = 1.0 >= 1.0
                Arguments.of(25000, 50000, 500, 1.0, true),
                // 3 batches useful, 22 batches unuseful, ratio = 0.13 < 0.2
                Arguments.of(14, 121, 5, 0.2, false),
                // 14 batches useful, 11 batches unuseful, ratio = 1.27 >= 1.2
                Arguments.of(68, 121, 5, 1.2, true)
        );
    }
}
