package org.artifactory.remote.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StatusPatternMatcher}.
 *
 * @author Hussein Alshqerat
 */
class StatusPatternMatcherTest {

    @Test
    void compile_shouldMatchExactCode_whenSingleCodeProvided() {
        StatusPatternMatcher matcher = StatusPatternMatcher.compile("404");
        assertTrue(matcher.matches(404));
        assertFalse(matcher.matches(200));
        assertFalse(matcher.matches(403));
    }

    @Test
    void compile_shouldMatchMultipleCodes_whenCommaSeparated() {
        StatusPatternMatcher matcher = StatusPatternMatcher.compile("404,503");
        assertTrue(matcher.matches(404));
        assertTrue(matcher.matches(503));
        assertFalse(matcher.matches(200));
        assertFalse(matcher.matches(500));
    }

    @Test
    void compile_shouldMatchGroupShorthand_when4xxProvided() {
        StatusPatternMatcher matcher = StatusPatternMatcher.compile("4xx");
        assertTrue(matcher.matches(400));
        assertTrue(matcher.matches(404));
        assertTrue(matcher.matches(499));
        assertFalse(matcher.matches(200));
        assertFalse(matcher.matches(500));
    }

    @Test
    void compile_shouldMatchMultipleGroups_whenCommaSeparatedGroups() {
        StatusPatternMatcher matcher = StatusPatternMatcher.compile("4xx,5xx");
        assertTrue(matcher.matches(400));
        assertTrue(matcher.matches(404));
        assertTrue(matcher.matches(500));
        assertTrue(matcher.matches(503));
        assertFalse(matcher.matches(200));
        assertFalse(matcher.matches(301));
    }

    @Test
    void compile_shouldMatchRegex_whenRawRegexProvided() {
        StatusPatternMatcher matcher = StatusPatternMatcher.compile("5.*");
        assertTrue(matcher.matches(500));
        assertTrue(matcher.matches(503));
        assertTrue(matcher.matches(599));
        assertFalse(matcher.matches(200));
        assertFalse(matcher.matches(404));
    }

    @Test
    void compile_shouldMatchMixed_whenGroupAndExactCombined() {
        StatusPatternMatcher matcher = StatusPatternMatcher.compile("2xx,404");
        assertTrue(matcher.matches(200));
        assertTrue(matcher.matches(206));
        assertTrue(matcher.matches(404));
        assertFalse(matcher.matches(500));
        assertFalse(matcher.matches(403));
    }

    @Test
    void compile_shouldHandleUppercaseX_whenGroupProvided() {
        StatusPatternMatcher matcher = StatusPatternMatcher.compile("4XX");
        assertTrue(matcher.matches(400));
        assertTrue(matcher.matches(404));
        assertFalse(matcher.matches(500));
    }

    @Test
    void compile_shouldTrimWhitespace_whenTokensHaveSpaces() {
        StatusPatternMatcher matcher = StatusPatternMatcher.compile("404 , 503");
        assertTrue(matcher.matches(404));
        assertTrue(matcher.matches(503));
    }

    @Test
    void compile_shouldThrow_whenBlankPattern() {
        assertThrows(IllegalArgumentException.class,
                () -> StatusPatternMatcher.compile(""));
        assertThrows(IllegalArgumentException.class,
                () -> StatusPatternMatcher.compile("   "));
    }

    @Test
    void compile_shouldThrow_whenNullPattern() {
        assertThrows(IllegalArgumentException.class,
                () -> StatusPatternMatcher.compile(null));
    }

    @Test
    void compile_shouldThrow_whenInvalidRegex() {
        assertThrows(IllegalArgumentException.class,
                () -> StatusPatternMatcher.compile("[invalid"));
    }
}
