/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.request;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.IllegalFormatException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for hardening of {@link RepoRequests#logToContext}. The method now has two safety
 * layers; both are exercised here:
 * <ol>
 *   <li>No {@code params} - {@code format} is treated as a literal string; {@code String.format} is bypassed
 *       entirely so any {@code %} sequences in user-controlled input never get interpreted as conversion
 *       specifiers. This is the original production bug class.</li>
 *   <li>With {@code params} - a defensive {@code try/catch (IllegalFormatException)} catches mismatched
 *       {@code format}/{@code params} pairs (e.g. {@code "%d"} with a {@code String} arg) so they cannot
 *       propagate as HTTP 500 either.</li>
 * </ol>
 * Before the fix every case here would propagate an {@link IllegalFormatException} out of
 * {@code logToContext} and ultimately surface as HTTP 500.
 */
@ExtendWith(MockitoExtension.class)
class RepoRequestsTest {

    @AfterEach
    void tearDown() {
        setContext(null);
    }

    /**
     * Safety layer #1 - empty {@code params}: replay the exact production payload seen in
     * Coralogix for the {@code gradle} namespace. {@code DownloadServiceImpl.respond} concatenates a constant
     * prefix with {@link java.nio.file.InvalidPathException#getMessage()}, which embeds the user-supplied
     * path containing literal {@code %2F}, and passes the result to {@code logToContext(message)} with no
     * args. Before the fix this surfaced as {@code UnknownFormatConversionException: 'F'}; now the
     * literal-string shortcut bypasses {@code String.format} entirely so the message is logged verbatim.
     */
    @Test
    void logToContext_emptyParamsWithLiteralPercentInFormat_logsRawWithoutThrowing() {
        RepoRequestContext contextMock = mock(RepoRequestContext.class);
        setContext(contextMock);

        String userPath = "github-downloads-proxy/login?return_to=%2F%2Fexample.com";
        String invalidPathMsg = "Invalid path. '?' is not a valid name character: " + userPath;
        String concatenatedAsInDownloadServiceImpl =
                "Invalid path exception occurred while sending request response: " + invalidPathMsg;

        // sanity-check: passing this string to String.format would throw (proves the input is malformed)
        assertThrows(IllegalFormatException.class, () -> String.format(concatenatedAsInDownloadServiceImpl));

        assertDoesNotThrow(() -> RepoRequests.logToContext(concatenatedAsInDownloadServiceImpl));
        verify(contextMock).log(eq(concatenatedAsInDownloadServiceImpl));
    }

    /**
     * Safety layer #2 - defensive {@code try/catch} around {@code String.format} for the case
     * where the caller supplies {@code params} but they don't match the {@code format} (e.g. {@code "%d"}
     * with a {@code String} arg). Each row is a (format, args) combination crafted so {@code String.format}
     * really throws a different concrete {@link IllegalFormatException} subclass - proving the catch covers
     * the whole family. The {@code assertThrows} on {@code String.format} is the sanity check that keeps
     * the test data from silently rotting.
     */
    @ParameterizedTest(name = "[{index}] {2} ({0})")
    @MethodSource("illegalFormatExceptionFamilyDataProvider")
    void logToContext_withParamsThatMismatchFormat_catchesIllegalFormatExceptionFamily(
            String format, Object[] args, String subclassName) {
        RepoRequestContext contextMock = mock(RepoRequestContext.class);
        setContext(contextMock);

        IllegalFormatException raised = assertThrows(IllegalFormatException.class,
                () -> String.format(format, args), "test data must trigger " + subclassName);
        assertEqualsClassName(subclassName, raised);

        assertDoesNotThrow(() -> RepoRequests.logToContext(format, args));
        verify(contextMock).log(eq(format));
    }

    private static Stream<Arguments> illegalFormatExceptionFamilyDataProvider() {
        return Stream.of(
                // 'F' is not a valid conversion - parsed before any arg is consumed
                Arguments.of("path=%2F", new Object[] {"unused"}, "UnknownFormatConversionException"),
                // first %s consumes the only arg, second %s has none
                Arguments.of("path=%s %s", new Object[] {"a"}, "MissingFormatArgumentException"),
                // arg type does not match conversion (%d expects int, got String)
                Arguments.of("count=%d", new Object[] {"abc"}, "IllegalFormatConversionException"),
                // %- requires an explicit width
                Arguments.of("[%-s]", new Object[] {"x"}, "MissingFormatWidthException")
        );
    }

    private static void assertEqualsClassName(String expected, Throwable actual) {
        if (!expected.equals(actual.getClass().getSimpleName())) {
            throw new AssertionError("expected " + expected + " but got " + actual.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    private static void setContext(RepoRequestContext contextValue) {
        try {
            Field contextField = RepoRequests.class.getDeclaredField("context");
            contextField.setAccessible(true);
            ThreadLocal<RepoRequestContext> threadLocal = (ThreadLocal<RepoRequestContext>) contextField.get(null);
            if (contextValue == null) {
                threadLocal.remove();
            } else {
                threadLocal.set(contextValue);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
