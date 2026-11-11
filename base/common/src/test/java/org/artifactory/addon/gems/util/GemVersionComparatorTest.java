package org.artifactory.addon.gems.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author avivbr
 */
class GemVersionComparatorTest {

    private static Stream<Arguments> compareVersionsCases() {
        return Stream.of(
                // Null handling - null sorts before non-null
                Arguments.of(null, "1.0.0", -1),
                Arguments.of("1.0.0", null, 1),
                // Numeric comparison - 1.10 > 1.2
                Arguments.of("1.10", "1.2", 1),
                // Pre-release string segments sort lower than numeric segments
                Arguments.of("1.0.0.rc1", "1.0.0.1", -1),
                // Alpha segments compare lexicographically
                Arguments.of("1.0.a", "1.0.b", -1),
                Arguments.of("1.0.0.alpha", "1.0.0.beta", -1),
                // Platform suffix treated as pre-release string segment
                Arguments.of("1.0.0", "1.0.0-java", 1),
                Arguments.of("1.0.0-java", "1.0.0-x86_64", -1),
                // Equivalent numeric segments - 1.0 == 1.0.0
                Arguments.of("1.0", "1.0.0", 0),
                // Same version strings
                Arguments.of("2.0.0", "2.0.0", 0),
                // Empty version compares lower than numeric version
                Arguments.of("", "1", -1)
        );
    }

    private static Stream<Arguments> parseVersionCases() {
        return Stream.of(
                // Numeric and alpha segments
                Arguments.of("1.0.0-alpha.beta.1", List.of(1L, 0L, 0L, "alpha", "beta", 1L)),
                // Single numeric segment
                Arguments.of("1", List.of(1L)),
                // Empty string yields no segments
                Arguments.of("", List.of()),
                // Pre-release with separator or dot
                Arguments.of("1.0.0-rc1", List.of(1L, 0L, 0L, "rc", 1L)),
                Arguments.of("1.0.0.rc1", List.of(1L, 0L, 0L, "rc", 1L)),
                // Platform suffix
                Arguments.of("1.0.0-java", List.of(1L, 0L, 0L, "java")),
                // Alpha-only segments
                Arguments.of("alpha.beta.gamma", List.of("alpha", "beta", "gamma")),
                // Typical semver
                Arguments.of("1.2.3", List.of(1L, 2L, 3L)),
                // Large numeric segment
                Arguments.of("2.132.0.beta.20190930200026", List.of(2L, 132L, 0L, "beta", 20190930200026L))
        );
    }

    @ParameterizedTest
    @MethodSource("compareVersionsCases")
    void compareVersions_shouldFollowRubyGemsSemantics(String first, String second, int expectedSign) {
        int result = GemVersionComparator.compareVersions(first, second);
        assertEquals(expectedSign, Integer.signum(result));
    }

    @ParameterizedTest
    @MethodSource("parseVersionCases")
    void parseVersion_shouldSplitNumericAndAlphaSegments(String version, List<?> expectedSegments) {
        List<?> segments = GemVersionComparator.parseVersion(version);
        assertEquals(expectedSegments.size(), segments.size());
        for (int i = 0; i < expectedSegments.size(); i++) {
            if (expectedSegments.get(i) instanceof Long) {
                assertEquals(((Long) expectedSegments.get(i)).longValue(),
                        ((Number) segments.get(i)).longValue());
            } else if (expectedSegments.get(i) instanceof String) {
                assertEquals(expectedSegments.get(i), segments.get(i));
            }
        }
    }
}
