package org.artifactory.repo.interceptor.modle;

import org.artifactory.descriptor.repo.RepoType;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllowedRemoteUrlsConfigTest {

    private static final String SAMPLE_JSON = """
            {
                "globallyAllowedUrls": {
                    "https://registry.npmjs.org": ["Npm"],
                    "https://repo1.maven.org": ["Maven", "Gradle"],
                    "https://registry-1.docker.io": ["Docker"]
                },
                "explicitlyDeniedUrlRoute": {
                    "https://nodejs.org/dist": ["Npm"],
                    "https://registry.npmjs.org/-/npm/v1/security/audits": ["Npm"]
                }
            }
            """;

    @Test
    void testParseJson() {
        AllowedRemoteUrlsConfig config = buildConfig();
        assertNotNull(config);
        assertNotNull(config.getGloballyAllowedUrls());
        assertFalse(config.getGloballyAllowedUrls().isEmpty());
        assertNotNull(config.getExplicitlyDeniedUrlRoute());
        assertFalse(config.getExplicitlyDeniedUrlRoute().isEmpty());
    }

    @Test
    void testResolvedMappings() {
        Map<String, Set<RepoType>> resolved = buildConfig().getResolvedMappings();
        assertNotNull(resolved);
        assertTrue(resolved.get("https://registry.npmjs.org").contains(RepoType.Npm));
        assertTrue(resolved.get("https://repo1.maven.org").contains(RepoType.Maven));
        assertTrue(resolved.get("https://repo1.maven.org").contains(RepoType.Gradle));
    }

    @Test
    void testResolvedDeniedRoutes() {
        Map<String, Set<RepoType>> denied = buildConfig().getResolvedDeniedRoutes();
        assertNotNull(denied);
        assertTrue(denied.get("https://nodejs.org/dist").contains(RepoType.Npm));
        assertTrue(denied.get("https://registry.npmjs.org/-/npm/v1/security/audits").contains(RepoType.Npm));
        assertFalse(denied.get("https://registry.npmjs.org/-/npm/v1/security/audits").contains(RepoType.Generic));
    }

    @ParameterizedTest(name = "[{index}] url={0}, type={1}, expected={2}")
    @MethodSource("isUrlAllowedCases")
    void testIsUrlAllowed(String url, RepoType packageType, boolean expected) {
        assertEquals(expected, buildConfig().isUrlAllowed(url, packageType));
    }

    static Stream<Arguments> isUrlAllowedCases() {
        return Stream.of(
                // matching prefix, correct type -> allowed
                Arguments.of("https://registry.npmjs.org", RepoType.Npm, true),
                Arguments.of("https://registry.npmjs.org/some-pkg", RepoType.Npm, true),
                // matching prefix, wrong type -> blocked
                Arguments.of("https://registry.npmjs.org", RepoType.Docker, false),
                Arguments.of("https://repo1.maven.org/repo", RepoType.Npm, false),
                // unknown URL -> always allowed
                Arguments.of("https://custom-registry.example.com", RepoType.Docker, true),
                Arguments.of("https://my-private-npm.local", RepoType.Npm, true),
                // Generic type is always allowed regardless of URL
                Arguments.of("https://registry.npmjs.org", RepoType.Generic, true),
                Arguments.of("https://registry-1.docker.io", RepoType.Generic, true),
                Arguments.of("https://repo1.maven.org/maven2/", RepoType.Generic, true),
                // null url or null type -> allowed
                Arguments.of(null, RepoType.Npm, true),
                Arguments.of("https://registry.npmjs.org", null, true),
                // case insensitive
                Arguments.of("HTTPS://REGISTRY.NPMJS.ORG/foo", RepoType.Npm, true),
                Arguments.of("Https://Repo1.Maven.Org", RepoType.Maven, true),
                // explicitly denied route, exact match + denied type -> blocked
                Arguments.of("https://nodejs.org/dist", RepoType.Npm, false),
                // explicitly denied route, exact match + Generic -> always allowed
                Arguments.of("https://nodejs.org/dist", RepoType.Generic, true),
                // denied route with extra path (not exact) -> falls through to other rules
                Arguments.of("https://nodejs.org/dist/v18", RepoType.Npm, true),
                // denied route case insensitive
                Arguments.of("HTTPS://NODEJS.ORG/DIST", RepoType.Npm, false),
                // denied route that is a sub-path of an allowed prefix
                Arguments.of("https://registry.npmjs.org/-/npm/v1/security/audits", RepoType.Npm, false),
                // denied route with Generic -> always allowed (Generic bypasses all checks)
                Arguments.of("https://registry.npmjs.org/-/npm/v1/security/audits", RepoType.Generic, true)
        );
    }

    private static AllowedRemoteUrlsConfig buildConfig() {
        return JsonUtils.getInstance().readValue(SAMPLE_JSON, AllowedRemoteUrlsConfig.class);
    }
}
