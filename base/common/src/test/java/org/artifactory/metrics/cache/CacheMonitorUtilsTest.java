package org.artifactory.metrics.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author oleksandrl
 */
class CacheMonitorUtilsTest {

    @Test
    void shouldIgnore_shouldReturnFalse_whenAllIgnoreListsAreEmpty() {
        // Act
        boolean result = CacheMonitorUtils.shouldIgnore("test-cache", getClass(), Set.of(), Set.of(), Set.of());
        // Assert
        assertFalse(result);
    }

    @Test
    void shouldIgnore_shouldReturnTrue_whenCacheNameIsInIgnoreList() {
        // Arrange
        Set<String> cachesToIgnore = Set.of("test-cache", "another-cache");
        // Act
        boolean result = CacheMonitorUtils.shouldIgnore("test-cache", getClass(), Set.of(), Set.of(), cachesToIgnore);
        // Assert
        assertTrue(result);
    }

    @Test
    void shouldIgnore_shouldReturnFalse_whenCacheNameIsNotInIgnoreList() {
        // Arrange
        Set<String> cachesToIgnore = Set.of("test-cache", "another-cache");
        // Act
        boolean result = CacheMonitorUtils.shouldIgnore("different-cache", getClass(), Set.of(), Set.of(),
                cachesToIgnore);
        // Assert
        assertFalse(result);
    }

    @Test
    void shouldIgnore_shouldReturnTrue_whenFullClassNameIsInIgnoreList() {
        // Arrange
        Set<String> classesToIgnore = Set.of("org.artifactory.metrics.cache.CacheMonitorUtilsTest");
        // Act
        boolean result = CacheMonitorUtils.shouldIgnore("test-cache", getClass(), Set.of(), classesToIgnore, Set.of());
        // Assert
        assertTrue(result);
    }

    @Test
    void shouldIgnore_shouldReturnTrue_whenSimpleClassNameIsInIgnoreList() {
        // Arrange
        Set<String> classesToIgnore = Set.of("CacheMonitorUtilsTest");
        // Act
        boolean result = CacheMonitorUtils.shouldIgnore("test-cache", getClass(), Set.of(), classesToIgnore, Set.of());
        // Assert
        assertTrue(result);
    }

    @Test
    void shouldIgnore_shouldReturnFalse_whenClassNameIsNotInIgnoreList() {
        // Arrange
        Set<String> classesToIgnore = Set.of("SomeOtherClass");
        // Act
        boolean result = CacheMonitorUtils.shouldIgnore("test-cache", getClass(), Set.of(), classesToIgnore, Set.of());
        // Assert
        assertFalse(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Exact package - tests that exact package match is recognized
            "org.artifactory.metrics.cache",
            // Parent package - tests that parent package match is recognized
            "org.artifactory",
            // Root package - tests that root package match is recognized
            "org"
    })
    void shouldIgnore_shouldReturnTrue_whenPackageIsInIgnoreList(String packageToIgnore) {
        // Arrange
        Set<String> packagesToIgnore = Set.of(packageToIgnore);
        // Act
        boolean result = CacheMonitorUtils.shouldIgnore("test-cache", getClass(), packagesToIgnore, Set.of(), Set.of());
        // Assert
        assertTrue(result);
    }

    @Test
    void shouldIgnore_shouldReturnFalse_whenPackageIsNotInIgnoreList() {
        // Arrange
        Set<String> packagesToIgnore = Set.of("com.example");
        // Act
        boolean result = CacheMonitorUtils.shouldIgnore("test-cache", getClass(), packagesToIgnore, Set.of(), Set.of());
        // Assert
        assertFalse(result);
    }

    @ParameterizedTest
    @MethodSource("multipleIgnoreRulesData")
    void shouldIgnore_shouldReturnExpectedResult_whenMultipleIgnoreRulesApply(
            String cacheName, Class<?> ownerClass, Set<String> packagesToIgnore, Set<String> classesToIgnore,
            Set<String> cachesToIgnore, boolean expectedResult) {
        // Act
        boolean result = CacheMonitorUtils.shouldIgnore(cacheName, ownerClass, packagesToIgnore, classesToIgnore,
                cachesToIgnore);
        // Assert
        if (expectedResult) {
            assertTrue(result);
        } else {
            assertFalse(result);
        }
    }

    private static Stream<Arguments> multipleIgnoreRulesData() {
        return Stream.of(
                // Cache name matches, class doesn't match, package doesn't match - should ignore
                Arguments.of("test-cache", CacheMonitorUtilsTest.class, Set.of("com.example"),
                        Set.of("SomeOtherClass"), Set.of("test-cache"), true),
                // Cache name doesn't match, class matches, package doesn't match - should ignore
                Arguments.of("different-cache", CacheMonitorUtilsTest.class, Set.of("com.example"),
                        Set.of("CacheMonitorUtilsTest"), Set.of("test-cache"), true),
                // Cache name doesn't match, class doesn't match, package matches - should ignore
                Arguments.of("different-cache", CacheMonitorUtilsTest.class,
                        Set.of("org.artifactory.metrics.cache"), Set.of("SomeOtherClass"), Set.of("test-cache"), true),
                // Nothing matches - should not ignore
                Arguments.of("different-cache", CacheMonitorUtilsTest.class, Set.of("com.example"),
                        Set.of("SomeOtherClass"), Set.of("test-cache"), false),
                // All lists are empty - should not ignore
                Arguments.of("test-cache", CacheMonitorUtilsTest.class, Set.of(), Set.of(), Set.of(), false),
                // Multiple cache names in ignore list, one matches
                Arguments.of("cache-two", String.class, Set.of(), Set.of(), Set.of("cache-one", "cache-two"), true),
                // Multiple class names in ignore list, simple name matches
                Arguments.of("test-cache", String.class, Set.of(), Set.of("Integer", "String"), Set.of(), true),
                // Multiple class names in ignore list, full name matches
                Arguments.of("test-cache", String.class, Set.of(), Set.of("java.lang.String"), Set.of(), true),
                // Multiple packages in ignore list, parent package matches
                Arguments.of("test-cache", CacheMonitorUtilsTest.class, Set.of("com.example", "org.artifactory"),
                        Set.of(), Set.of(), true)
        );
    }
}
