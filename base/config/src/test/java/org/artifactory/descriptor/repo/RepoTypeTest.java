package org.artifactory.descriptor.repo;


import org.artifactory.common.repo.PackageType;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Alexei Vainshtein
 */
class RepoTypeTest {

    @Test
    void testIsPipeInfo() {
        assertTrue(RepoType.PipeInfo.isPipeInfo());
        assertFalse(RepoType.Generic.isPipeInfo());
        assertFalse(RepoType.BuildInfo.isPipeInfo());
    }

    @Test
    void testIsDockerGroup() {
        assertTrue(RepoType.OCI.isDockerGroup());
        assertTrue(RepoType.Docker.isDockerGroup());
        assertTrue(RepoType.HelmOCI.isDockerGroup());
    }

    @Test
    void testIsBuildInfo() {
        assertTrue(RepoType.BuildInfo.isBuildInfo());
        assertFalse(RepoType.Generic.isBuildInfo());
        assertFalse(RepoType.PipeInfo.isBuildInfo());
    }

    @Test
    void testIsEnvironmentAgnostic() {
        assertTrue(RepoType.BuildInfo.isEnvironmentAgnostic());
        assertTrue(RepoType.ReleaseBundles.isEnvironmentAgnostic());
        assertFalse(RepoType.Generic.isEnvironmentAgnostic());
        assertFalse(RepoType.Maven.isEnvironmentAgnostic());
        assertFalse(RepoType.PipeInfo.isEnvironmentAgnostic());
    }

    // The following test is required so frogml repo type won't be accidentally deleted before its records in customer
    // DB are dealt with
    @Test
    void testFrogMLExists() {
        assertNotNull(RepoType.fromType("frogml"));
        assertNotNull(PackageType.valueOf("FROGML"));
    }

    @Test
    void testFromString() {
        for (RepoType repoType : RepoType.values()) {
            assertEquals(repoType, RepoType.fromString(repoType.getConfigName()));
        }
        assertEquals(RepoType.YUM, RepoType.fromString("RPM"));
        assertNull(RepoType.fromString("someInvalidInput"));
    }

    @Test
    void testFromTypeOrThrow() {
        for (RepoType repoType : RepoType.values()) {
            assertEquals(repoType, RepoType.fromTypeOrThrow(repoType.getConfigName()));
        }
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> RepoType.fromTypeOrThrow("invalid"));
        assertEquals("No matching RepoType found for type: invalid", exception.getMessage());
    }

    @Test
    void testIsMavenGroup() {
        assertTrue(RepoType.Maven.isMavenGroup());
        assertTrue(RepoType.Ivy.isMavenGroup());
        assertTrue(RepoType.Gradle.isMavenGroup());
        assertTrue(RepoType.P2.isMavenGroup());
        assertTrue(RepoType.SBT.isMavenGroup());
        assertFalse(RepoType.Generic.isMavenGroup());
    }

    @Test
    void testGetMavenGroupTypes() {
        Set<RepoType> mavenGroupTypes = RepoType.getMavenGroupTypes();
        assertNotNull(mavenGroupTypes);
        assertTrue(mavenGroupTypes.contains(RepoType.Maven));
        assertTrue(mavenGroupTypes.contains(RepoType.Ivy));
        assertTrue(mavenGroupTypes.contains(RepoType.Gradle));
        assertTrue(mavenGroupTypes.contains(RepoType.P2));
        assertTrue(mavenGroupTypes.contains(RepoType.SBT));
        assertFalse(mavenGroupTypes.contains(RepoType.Generic));
    }

    @Test
    void testGetMavenGroupExcludeP2Types() {
        Set<RepoType> mavenGroupExcludeP2Types = RepoType.getMavenGroupExcludeP2Types();
        assertNotNull(mavenGroupExcludeP2Types, "The returned set should not be null");
        assertTrue(mavenGroupExcludeP2Types.contains(RepoType.Maven), "Maven should be included");
        assertTrue(mavenGroupExcludeP2Types.contains(RepoType.Ivy), "Ivy should be included");
        assertTrue(mavenGroupExcludeP2Types.contains(RepoType.Gradle), "Gradle should be included");
        assertTrue(mavenGroupExcludeP2Types.contains(RepoType.SBT), "SBT should be included");
        assertFalse(mavenGroupExcludeP2Types.contains(RepoType.P2), "P2 should be excluded");
    }

    /**
     * The default production mapper ({@link JsonUtils}) must stay strict. An unknown package type
     * (for example, one written by a newer Artifactory version) must throw rather than be silently
     * coerced to Generic, so REST input and the config converters reject it. The lenient fallback
     * to Generic is applied only on the scoped, read-only blob mapper used at startup.
     */
    @Test
    void testUnknownValueViaJsonUtilsThrows() {
        assertThrows(Exception.class, () -> JsonUtils.getInstance().readValue("\"SkillsFuture\"", RepoType.class));
    }

    /**
     * Support serializes to its {@code @XmlEnumValue("supportbundles")} value. Verify the strict
     * default mapper resolves "supportbundles" back to {@link RepoType#Support} natively through the
     * JAXB module, so no extra alias mapping is needed for the deserialization path.
     */
    @Test
    void testSupportBundlesRoundTripViaJsonUtils() {
        String serialized = JsonUtils.getInstance().writeValueAsString(RepoType.Support);
        assertEquals("\"supportbundles\"", serialized);
        assertEquals(RepoType.Support, JsonUtils.getInstance().readValue(serialized, RepoType.class));
    }
}