/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.storage.db.util;

import org.artifactory.activity.ActivityContext;
import org.artifactory.activity.ActivityScope;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.common.ConstantValues;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.test.ArtifactoryHomeStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test class for UniversalLimitHandlerUtility
 *
 * @author Eli Fishman
 */
class UniversalLimitHandlerUtilityTest {

    private ArtifactoryHomeStub homeStub;

    @BeforeEach
    void setUp() {
        homeStub = new ArtifactoryHomeStub(getClass().getName());
        homeStub.bind();
        UniversalLimitHandlerUtility.clearPatternCache();
        // Reset list-typed properties so each test is hermetic and does not
        // depend on whatever default values ConstantValues ships. Without
        // this, a non-empty default for either list bleeds in via
        // ArtifactorySystemProperties#getProperty -> ConstantValues#getDefValue
        // and silently affects precedence outcomes (e.g. a non-empty default
        // whitelist would exempt requests in tests that never set it).
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "");
        homeStub.setProperty(ConstantValues.limitAllQueriesRemediationWhitelist, "");
        homeStub.setProperty(ConstantValues.limitAllQueriesExemptUserAgents, "");
    }

    @AfterEach
    void tearDown() {
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of());
        ArtifactoryHomeStub.unbind();
    }

    @ParameterizedTest(name = "{4}")
    @MethodSource("zeroLimitScenarios")
    void testGetConfiguredLimitWithScenariosThatShouldReturnZero(
            int limitAllQueries,
            String limitAllQueriesWithBlock,
            String requestPath,
            String jobName,
            String description) {
        homeStub.setProperty(ConstantValues.limitAllQueries, String.valueOf(limitAllQueries));
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, limitAllQueriesWithBlock);

        try (MockedStatic<RequestThreadLocal> requestMock = Mockito.mockStatic(RequestThreadLocal.class);
                MockedStatic<CurrentActivity> currentActivityMock = Mockito.mockStatic(CurrentActivity.class)) {

            requestMock.when(RequestThreadLocal::getRequestPath).thenReturn(requestPath);
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            // Always stub context() — getContextInfo() calls CurrentActivity.NAME.get() which
            // delegates to context().get(key). When requestPath is present we let all gets
            // return null so the method falls through to the request-path branch.
            ActivityContext mockContext = Mockito.mock(ActivityContext.class);
            currentActivityMock.when(CurrentActivity::context).thenReturn(mockContext);
            if (requestPath == null) {
                when(mockContext.get(anyString())).thenReturn(jobName);
            }

            int result = UniversalLimitHandlerUtility.getConfiguredLimit();
            assertEquals(0, result, description);
        }
    }

    static Stream<Arguments> zeroLimitScenarios() {
        return Stream.of(
                Arguments.of(0, "search/dates", "search/dates", "",
                        "Zero limit with matching pattern should return zero"),
                Arguments.of(1000, "", "search/dates", "",
                        "Non-zero limit with empty block list should return zero"),
                Arguments.of(1000, "search/*", null, "",
                        "Non-zero limit with empty context info should return zero"),
                Arguments.of(1000, "dates/users", "/search/dates", "",
                        "Non-zero limit with non-matching block pattern should return zero")
        );
    }

    @ParameterizedTest(name = "{6}")
    @MethodSource("patternMatchingScenarios")
    void testGetConfiguredLimitWithMatchedPatterns(
            int limitAllQueries,
            String limitAllQueriesWithBlock,
            String requestPath,
            String jobName,
            int expectedLimit,
            boolean expectedShouldBlock,
            String description) {
        homeStub.setProperty(ConstantValues.limitAllQueries, String.valueOf(limitAllQueries));
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, limitAllQueriesWithBlock);

        try (MockedStatic<RequestThreadLocal> requestMock = Mockito.mockStatic(RequestThreadLocal.class);
                MockedStatic<CurrentActivity> currentActivityMock = Mockito.mockStatic(CurrentActivity.class)) {

            requestMock.when(RequestThreadLocal::getRequestPath).thenReturn(requestPath);
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            // Always stub context() — getContextInfo() calls CurrentActivity.NAME.get() which
            // delegates to context().get(key). When requestPath is present we let all gets
            // return null so the method falls through to the request-path branch.
            ActivityContext mockContext = Mockito.mock(ActivityContext.class);
            currentActivityMock.when(CurrentActivity::context).thenReturn(mockContext);
            if (requestPath == null) {
                when(mockContext.get(anyString())).thenReturn(jobName);
            }

            int configuredLimit = UniversalLimitHandlerUtility.getConfiguredLimit();
            boolean shouldBlock = UniversalLimitHandlerUtility.isToBlock();

            assertEquals(expectedLimit, configuredLimit, description + " - limit");
            assertEquals(expectedShouldBlock, shouldBlock, description + " - block status");
        }
    }

    static Stream<Arguments> patternMatchingScenarios() {
        return Stream.of(
                Arguments.of(6666, "search/.*", "search/dates", "",
                        6666, true,
                        "Block pattern with regex should return limit and block"),
                Arguments.of(1000, "search/dates", "/search/dates", "",
                        1000, true,
                        "Block pattern match should return limit and block"),
                Arguments.of(1000, "search/.+?/foo", "search/dates/foo", "",
                        1000, true,
                        "Block pattern with .+? as regex match should return limit and block"),
                Arguments.of(1000, "/search/[^/]+/foo", "search/dates/foo", "",
                        1000, true,
                        "Block pattern with [^/]+ as regex match should return limit and block")
        );
    }

    @Test
    void getConfiguredLimit_shouldReturnZero_whenUserAgentIsInExclusionList() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "1000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "search/dates");

        try (MockedStatic<RequestThreadLocal> requestMock = Mockito.mockStatic(RequestThreadLocal.class)) {

            requestMock.when(RequestThreadLocal::getRequestPath).thenReturn("search/dates");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(true);

            int result = UniversalLimitHandlerUtility.getConfiguredLimit();

            assertEquals(0, result);
        }
    }

    @Test
    void getConfiguredLimit_shouldApplyLimit_whenInternalUserIsNotInExclusionList() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "1000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "search/dates");

        try (MockedStatic<RequestThreadLocal> requestMock = Mockito.mockStatic(RequestThreadLocal.class)) {

            requestMock.when(RequestThreadLocal::getRequestPath).thenReturn("search/dates");
            // The request comes from an internal agent, but its User-Agent is NOT in
            // limitAllQueriesExemptUserAgents, so the universal limit must still be applied (DoorDash-style).
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            int result = UniversalLimitHandlerUtility.getConfiguredLimit();

            assertEquals(1000, result,
                    "Internal users not in the exclusion list must still be subject to the universal limit");
        }
    }

    @Test
    void isToBlock_shouldReturnFalse_whenUserAgentIsInExclusionList() {
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "search/dates");

        try (MockedStatic<RequestThreadLocal> requestMock = Mockito.mockStatic(RequestThreadLocal.class)) {

            requestMock.when(RequestThreadLocal::getRequestPath).thenReturn("search/dates");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(true);

            assertFalse(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    @Test
    void testGetConfiguredLimitWithInvalidRegexPatternShouldFallbackToContains() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "1000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "api/search/[abc");

        try (MockedStatic<RequestThreadLocal> requestMock = Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath).thenReturn("search/[abc");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            int configuredLimit = UniversalLimitHandlerUtility.getConfiguredLimit();
            boolean shouldBlock = UniversalLimitHandlerUtility.isToBlock();

            assertEquals(1000, configuredLimit);
            assertTrue(shouldBlock);
        }
    }

    // Note on contextInfo form: production calls reach getContextInfo() via
    // RequestThreadLocal#getRequestPath, which returns request.getPathInfo()
    // for the Jersey /api/* servlet mapping — the leading /api segment is
    // stripped server-side. The mocks below therefore use the bare form
    // (e.g. "search/aql", not "api/search/aql"). Operator-written list
    // entries may still be written either with or without the api/ prefix —
    // UniversalLimitHandlerUtility#stripApiPrefix normalizes them.

    @Test
    void getConfiguredLimit_shouldReturnLimit_whenContextMatchesOomBlacklist() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "500000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "");
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of(".*search.*"));

        try (MockedStatic<RequestThreadLocal> requestMock =
                     Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath)
                    .thenReturn("search/aql");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit)
                    .thenReturn(false);

            assertEquals(500000,
                    UniversalLimitHandlerUtility.getConfiguredLimit());
            assertTrue(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    @Test
    void getConfiguredLimit_shouldReturnZero_whenContextDoesNotMatchOomBlacklist() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "500000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "");
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of(".*search.*"));

        try (MockedStatic<RequestThreadLocal> requestMock =
                     Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath)
                    .thenReturn("download/artifact");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit)
                    .thenReturn(false);

            assertEquals(0,
                    UniversalLimitHandlerUtility.getConfiguredLimit());
            assertFalse(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    @Test
    void whitelist_exemptsSubUrls_whenViolationsMatch() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "500000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "");
        homeStub.setProperty(ConstantValues.limitAllQueriesRemediationWhitelist,
                "api/liko/something");
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of(".*liko.*"));

        try (MockedStatic<RequestThreadLocal> requestMock =
                     Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath)
                    .thenReturn("liko/something/sub-url");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertEquals(0, UniversalLimitHandlerUtility.getConfiguredLimit());
            assertFalse(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    @Test
    void whitelist_doesNotExemptSiblings() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "500000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "");
        homeStub.setProperty(ConstantValues.limitAllQueriesRemediationWhitelist,
                "api/liko/something");
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of(".*liko.*"));

        try (MockedStatic<RequestThreadLocal> requestMock =
                     Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath)
                    .thenReturn("liko/other");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertEquals(500000, UniversalLimitHandlerUtility.getConfiguredLimit());
            assertTrue(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    @Test
    void blocklist_overridesWhitelist() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "500000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock,
                "api/liko/something");
        homeStub.setProperty(ConstantValues.limitAllQueriesRemediationWhitelist,
                "api/liko/something");

        try (MockedStatic<RequestThreadLocal> requestMock =
                     Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath)
                    .thenReturn("liko/something");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertEquals(500000, UniversalLimitHandlerUtility.getConfiguredLimit());
            assertTrue(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    @Test
    void whitelist_overridesViolations() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "500000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "");
        homeStub.setProperty(ConstantValues.limitAllQueriesRemediationWhitelist,
                "api/liko/something");
        UniversalLimitHandlerUtility.setOomBlacklist(
                Set.of(".*liko/something.*"));

        try (MockedStatic<RequestThreadLocal> requestMock =
                     Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath)
                    .thenReturn("liko/something/details");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertEquals(0, UniversalLimitHandlerUtility.getConfiguredLimit());
            assertFalse(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    @Test
    void emptyWhitelist_preservesLegacyBehaviour() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "1000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "search/.*");
        homeStub.setProperty(ConstantValues.limitAllQueriesRemediationWhitelist, "");

        try (MockedStatic<RequestThreadLocal> requestMock =
                     Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath)
                    .thenReturn("search/dates");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertEquals(1000, UniversalLimitHandlerUtility.getConfiguredLimit());
            assertTrue(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    /**
     * Operators may write whitelist entries with the user-visible URL prefix
     * {@code api/...} even though the runtime contextInfo
     * (i.e. {@code request.getPathInfo()}) returned by Jersey for {@code /api/*}
     * mappings strips that segment.
     * {@code UniversalLimitHandlerUtility#stripApiPrefix} drops a leading
     * {@code api/} segment from each entry once at match time, so
     * {@code api/search/aql} matches a request whose contextInfo is
     * {@code search/aql}.
     */
    @Test
    void whitelist_acceptsApiPrefixedEntries() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "500000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "");
        homeStub.setProperty(ConstantValues.limitAllQueriesRemediationWhitelist,
                "api/search/aql");
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of(".*search/aql.*"));

        try (MockedStatic<RequestThreadLocal> requestMock =
                     Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath)
                    .thenReturn("search/aql");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertEquals(0, UniversalLimitHandlerUtility.getConfiguredLimit());
            assertFalse(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    /**
     * Symmetric counterpart of {@link #whitelist_acceptsApiPrefixedEntries()}
     * for the regex blocklist: the same {@code stripApiPrefix} normalization
     * is applied to each {@code limitAllQueriesWithBlock} entry, so an
     * operator-written entry of {@code api/search/aql} blocks a request
     * whose contextInfo is {@code search/aql}.
     */
    @Test
    void blocklist_acceptsApiPrefixedRegex() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "1000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock,
                "api/search/aql");

        try (MockedStatic<RequestThreadLocal> requestMock =
                     Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath)
                    .thenReturn("search/aql");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertEquals(1000, UniversalLimitHandlerUtility.getConfiguredLimit());
            assertTrue(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    // ---- /artifactory/api prefix normalisation tests ----

    /**
     * An operator who writes {@code "/artifactory/api/search/aql"} in the blocklist must get
     * the same blocking behaviour as the bare form {@code "search/aql"}.
     */
    @Test
    void blocklist_acceptsArtifactoryApiPrefixedRegex() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "1000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock,
                "/artifactory/api/search/aql");

        try (MockedStatic<RequestThreadLocal> requestMock =
                     Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath).thenReturn("search/aql");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertEquals(1000, UniversalLimitHandlerUtility.getConfiguredLimit());
            assertTrue(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    /**
     * {@code "artifactory/api/search/aql"} (no leading slash) must also be treated as the bare
     * form by {@code stripApiPrefix}.
     */
    @Test
    void blocklist_acceptsArtifactoryApiPrefixedRegex_withoutLeadingSlash() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "1000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock,
                "artifactory/api/search/aql");

        try (MockedStatic<RequestThreadLocal> requestMock =
                     Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath).thenReturn("search/aql");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertEquals(1000, UniversalLimitHandlerUtility.getConfiguredLimit());
            assertTrue(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    /**
     * Whitelist entries written with the {@code /artifactory/api/} prefix must be normalised the
     * same way as bare entries.
     */
    @Test
    void whitelist_acceptsArtifactoryApiPrefixedEntries() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "500000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "");
        homeStub.setProperty(ConstantValues.limitAllQueriesRemediationWhitelist,
                "/artifactory/api/search/aql");
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of(".*search/aql.*"));

        try (MockedStatic<RequestThreadLocal> requestMock =
                     Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath).thenReturn("search/aql");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertEquals(0, UniversalLimitHandlerUtility.getConfiguredLimit());
            assertFalse(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    /**
     * When the context info itself carries a full {@code /artifactory/api/} prefix (e.g. from a
     * URI-template filter that assembles the complete path), it must be stripped before matching
     * so that a bare operator pattern still blocks the request correctly.
     */
    @Test
    void blocklist_matchesWhenContextInfoCarriesArtifactoryApiPrefix() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "1000");
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "search/aql");

        try (MockedStatic<RequestThreadLocal> requestMock =
                     Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMock.when(RequestThreadLocal::getRequestPath)
                    .thenReturn("/artifactory/api/search/aql");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertEquals(1000, UniversalLimitHandlerUtility.getConfiguredLimit());
            assertTrue(UniversalLimitHandlerUtility.isToBlock());
        }
    }

    @Test
    void addToOomBlacklist_mergesIntoExistingPatterns_withoutDiscardingThem() {
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of(".*search.*"));

        UniversalLimitHandlerUtility.addToOomBlacklist(Set.of(".*download.*"));

        Collection<String> effective = UniversalLimitHandlerUtility.getEffectiveBlocklist();
        assertTrue(effective.contains(".*search.*"), "pre-existing pattern must be preserved");
        assertTrue(effective.contains(".*download.*"), "propagated pattern must be added");
    }

    @Test
    void addToOomBlacklist_seedsFromConstantValues_whenNoBlacklistSetYet() {
        homeStub.setProperty(ConstantValues.limitAllQueriesWithBlock, "search/.*");

        UniversalLimitHandlerUtility.addToOomBlacklist(Set.of(".*download.*"));

        Collection<String> effective = UniversalLimitHandlerUtility.getEffectiveBlocklist();
        assertTrue(effective.contains("search/.*"), "ConstantValues baseline must be preserved");
        assertTrue(effective.contains(".*download.*"), "propagated pattern must be added");
    }

    @Test
    void addToOomBlacklist_isNoOp_whenPatternsNullOrEmpty() {
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of(".*search.*"));

        UniversalLimitHandlerUtility.addToOomBlacklist(null);
        UniversalLimitHandlerUtility.addToOomBlacklist(Set.of());

        Collection<String> effective = UniversalLimitHandlerUtility.getEffectiveBlocklist();
        assertEquals(1, effective.size());
        assertTrue(effective.contains(".*search.*"));
    }

    // ---- Route-key-aware getContextInfo() tests ----

    /**
     * When {@link CurrentActivity#NAME} is populated (Jersey URI-template context filter active),
     * it takes priority over the request path and becomes the context info used for blocklist matching.
     */
    @Test
    void getContextInfo_prioritisesActivityName_overRequestPath() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "500000");
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of("search/aql"));

        try (ActivityScope scope = CurrentActivity.newScope();
                MockedStatic<RequestThreadLocal> requestMock =
                        Mockito.mockStatic(RequestThreadLocal.class)) {
            CurrentActivity.NAME.put("search/aql");
            requestMock.when(RequestThreadLocal::getRequestPath).thenReturn("other/path");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertTrue(UniversalLimitHandlerUtility.isToBlock(),
                    "Blocklist pattern 'search/aql' must match context info 'search/aql' from NAME");
        }
    }

    /**
     * When {@link CurrentActivity#NAME} is absent but both GROUP and PACKAGE are set, the combined
     * {@code "group package"} key is used — e.g. {@code "download npm"} for npm artifact downloads.
     */
    @Test
    void getContextInfo_usesGroupAndPackage_whenNameIsAbsent() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "500000");
        // OOM recovery set "download npm" as the exact route-key pattern
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of("download npm"));

        try (ActivityScope scope = CurrentActivity.newScope();
                MockedStatic<RequestThreadLocal> requestMock =
                        Mockito.mockStatic(RequestThreadLocal.class)) {
            CurrentActivity.GROUP.put("download");
            CurrentActivity.PACKAGE.put("npm");
            // requestPath is the concrete artifact URL — it must NOT be used
            requestMock.when(RequestThreadLocal::getRequestPath)
                    .thenReturn("npm/-/lodash/-/lodash-4.17.21.tgz");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertTrue(UniversalLimitHandlerUtility.isToBlock(),
                    "Blocklist pattern 'download npm' must match context info 'download npm' "
                            + "derived from GROUP+PACKAGE");
        }
    }

    /**
     * A concrete artifact download for a different package type must NOT be blocked by a
     * {@code "download npm"} pattern.
     */
    @Test
    void getContextInfo_groupAndPackageKey_doesNotMatchDifferentPackage() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "500000");
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of("download npm"));

        try (ActivityScope scope = CurrentActivity.newScope();
                MockedStatic<RequestThreadLocal> requestMock =
                        Mockito.mockStatic(RequestThreadLocal.class)) {
            CurrentActivity.GROUP.put("download");
            CurrentActivity.PACKAGE.put("docker");
            requestMock.when(RequestThreadLocal::getRequestPath)
                    .thenReturn("docker/myrepo/v2/myimage/blobs/sha256:abc");
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertFalse(UniversalLimitHandlerUtility.isToBlock(),
                    "'download npm' pattern must not block a 'download docker' request");
        }
    }

    /**
     * When only GROUP is set (no NAME, no PACKAGE), the group alone forms the context info.
     * This preserves blocking for background jobs and simple download activities.
     */
    @Test
    void getContextInfo_usesGroupAlone_whenPackageIsAbsent() {
        homeStub.setProperty(ConstantValues.limitAllQueries, "500000");
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of(".*gc.*"));

        try (ActivityScope scope = CurrentActivity.newScope();
                MockedStatic<RequestThreadLocal> requestMock =
                        Mockito.mockStatic(RequestThreadLocal.class)) {
            CurrentActivity.GROUP.put("gc");
            requestMock.when(RequestThreadLocal::getRequestPath).thenReturn(null);
            requestMock.when(RequestThreadLocal::isExcludedFromUniversalLimit).thenReturn(false);

            assertTrue(UniversalLimitHandlerUtility.isToBlock(),
                    "Pattern '.*gc.*' must match context info 'gc' derived from GROUP alone");
        }
    }
}