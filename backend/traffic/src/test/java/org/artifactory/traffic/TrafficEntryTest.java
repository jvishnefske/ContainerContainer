package org.artifactory.traffic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficEntryTest {

    private static final int EXPECTED_PIPE_COUNT = 18; // 19 fields = 18 separators

    @Test
    void testToString() {
        TrafficEntry trafficEntry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.REDIRECT)
                .actor(TrafficEntry.TrafficActor.USER)
                .redirectTarget(TrafficEntry.TrafficTarget.S3)
                .userAddress("1.1.1.1")
                .repoPath("path/to/file")
                .traceId("1234")
                .size(345)
                .requestedSize(45)
                .transferredSize(23)
                .repoKey("jfrt-npm-local")
                .projectKey("jfrt")
                .username("cool-user")
                .repoType("local")
                .packageType("npm")
                .originRepoKey("cool-npm-virtual")
                .originProjectKey("cool")
                .originRepoType("virtual")
                .originPackageType("npm")
                .build();
        String expected = "|1234|REDIRECT|USER|S3|1.1.1.1|path/to/file|345|45|23|jfrt-npm-local|jfrt|cool-user|local" +
                "|npm|cool-npm-virtual|cool|virtual|npm";
        assertTrue(trafficEntry.toString().endsWith(expected));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("repoPathInjectionCases")
    void testRepoPathCRLFStripped(String description, String rawPath, String escapedPath) {
        TrafficEntry entry = baseBuilder().repoPath(rawPath).build();
        String result = entry.toString();

        assertNoCRLF(result);
        assertEquals(EXPECTED_PIPE_COUNT, pipeCount(result), "Field count must remain 19");
        assertTrue(result.contains("|" + escapedPath + "|"),
                "Escaped path must appear between pipe delimiters");
    }

    static Stream<Arguments> repoPathInjectionCases() {
        return Stream.of(
                Arguments.of(
                        "LF in middle (production incident payload)",
                        "ssrf-headers:test\nX-Injected: yes",
                        "ssrf-headers:testX-Injected: yes"),
                Arguments.of(
                        "CR in middle",
                        "ssrf-headers:test\rX-Injected: yes",
                        "ssrf-headers:testX-Injected: yes"),
                Arguments.of(
                        "CRLF in middle",
                        "ssrf-headers:test\r\nX-Injected: yes",
                        "ssrf-headers:testX-Injected: yes"),
                Arguments.of(
                        "LF at start",
                        "\nrepo:artifact",
                        "repo:artifact"),
                Arguments.of(
                        "LF at end",
                        "repo:artifact\n",
                        "repo:artifact"),
                Arguments.of(
                        "CR at start",
                        "\rrepo:artifact",
                        "repo:artifact"),
                Arguments.of(
                        "CR at end",
                        "repo:artifact\r",
                        "repo:artifact"),
                Arguments.of(
                        "Multiple LFs",
                        "repo:a\nb\nc",
                        "repo:abc"),
                Arguments.of(
                        "Multiple CRLFs",
                        "repo:a\r\nb\r\nc",
                        "repo:abc"),
                Arguments.of(
                        "Clean path - fast path, no modification",
                        "repo:path/to/artifact.jar",
                        "repo:path/to/artifact.jar")
        );
    }

    @ParameterizedTest(name = "CRLF in field [{0}] produces single-line 19-field record")
    @MethodSource("fieldInjectionCases")
    void testAllStringFieldsCRLFStripped(String fieldName, TrafficEntry entry, String expectedEscaped) {
        String result = entry.toString();
        assertNoCRLF(result);
        assertEquals(EXPECTED_PIPE_COUNT, pipeCount(result),
                fieldName + ": injection must not change field count");
        assertTrue(result.contains(expectedEscaped),
                fieldName + ": escaped value must appear in output");
    }

    static Stream<Arguments> fieldInjectionCases() {
        String injectLF = "val\ninjected";
        String injectCR = "val\rinjected";
        String injectCRLF = "val\r\ninjected";
        String escaped = "valinjected";
        return Stream.of(
                Arguments.of("traceId - LF", baseBuilder().traceId(injectLF).build(), escaped),
                Arguments.of("traceId - CR", baseBuilder().traceId(injectCR).build(), escaped),
                Arguments.of("traceId - CRLF", baseBuilder().traceId(injectCRLF).build(), escaped),
                Arguments.of("userAddress - LF", baseBuilder().userAddress(injectLF).build(), escaped),
                Arguments.of("userAddress - CRLF", baseBuilder().userAddress(injectCRLF).build(), escaped),
                Arguments.of("repoKey - LF", baseBuilder().repoKey(injectLF).build(), escaped),
                Arguments.of("repoKey - CRLF", baseBuilder().repoKey(injectCRLF).build(), escaped),
                Arguments.of("projectKey - LF", baseBuilder().projectKey(injectLF).build(), escaped),
                Arguments.of("projectKey - CRLF", baseBuilder().projectKey(injectCRLF).build(), escaped),
                Arguments.of("username - LF", baseBuilder().username(injectLF).build(), escaped),
                Arguments.of("username - CR", baseBuilder().username(injectCR).build(), escaped),
                Arguments.of("username - CRLF", baseBuilder().username(injectCRLF).build(), escaped),
                Arguments.of("repoType - CRLF", baseBuilder().repoType(injectCRLF).build(), escaped),
                Arguments.of("packageType - CRLF", baseBuilder().packageType(injectCRLF).build(), escaped),
                Arguments.of("originRepoKey - CRLF", baseBuilder().originRepoKey(injectCRLF).build(), escaped),
                Arguments.of("originProjectKey - CRLF", baseBuilder().originProjectKey(injectCRLF).build(), escaped),
                Arguments.of("originRepoType - CRLF", baseBuilder().originRepoType(injectCRLF).build(), escaped),
                Arguments.of("originPackageType - CRLF", baseBuilder().originPackageType(injectCRLF).build(), escaped)
        );
    }

    @Test
    void testNullStringFieldsProduceEmptyString() {
        TrafficEntry entry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.DOWNLOAD)
                .actor(TrafficEntry.TrafficActor.USER)
                .repoPath("repo:path")
                .size(0)
                .requestedSize(0)
                .transferredSize(0)
                .build();
        String result = entry.toString();
        assertFalse(result.contains("null"), "Null fields must produce empty string, not the literal 'null'");
        assertEquals(EXPECTED_PIPE_COUNT, pipeCount(result), "Null fields must not change field count");
    }

    @ParameterizedTest
    @EnumSource(TrafficEntry.TrafficTarget.class)
    void testDisplayName(TrafficEntry.TrafficTarget trafficTarget) {
        if (trafficTarget == TrafficEntry.TrafficTarget.CONTROLLED_CACHE) {
            assertEquals("cc", trafficTarget.displayName());
        } else {
            assertEquals(trafficTarget.name().toLowerCase(), trafficTarget.displayName());
        }
    }

    private static TrafficEntry.TrafficEntryBuilder baseBuilder() {
        return TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.UPLOAD)
                .actor(TrafficEntry.TrafficActor.USER)
                .redirectTarget(TrafficEntry.TrafficTarget.DIRECT)
                .userAddress("3.126.224.214")
                .repoPath("ssrf-headers:path")
                .traceId("abc123")
                .size(2)
                .requestedSize(2)
                .transferredSize(2)
                .repoKey("ssrf-headers")
                .projectKey("default")
                .username("user@example.com")
                .repoType("remote")
                .packageType("generic")
                .originRepoKey("ssrf-headers")
                .originProjectKey("default")
                .originRepoType("remote")
                .originPackageType("generic");
    }

    private static void assertNoCRLF(String result) {
        assertFalse(result.contains("\n"), "Log line must not contain LF");
        assertFalse(result.contains("\r"), "Log line must not contain CR");
    }

    private static long pipeCount(String s) {
        return s.chars().filter(c -> c == '|').count();
    }
}
