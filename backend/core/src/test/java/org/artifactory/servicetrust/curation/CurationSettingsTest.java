package org.artifactory.servicetrust.curation;

import org.artifactory.common.repo.PackageType;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurationSettingsTest {

    @Test
    void compactConstructor_nullForceCVSPackages_defaultsToEmptySet() {
        CurationSettings settings = CurationSettings.builder()
                .enabled(true)
                .forceCVSPackages(null)
                .build();

        assertTrue(settings.forceCVSPackages().isEmpty());
    }

    @Test
    void canonicalConstructor_nullForceCVSPackages_defaultsToEmptySet() {
        CurationSettings settings = new CurationSettings(
                null, null, true, false, false, false, null);

        assertTrue(settings.forceCVSPackages().isEmpty());
    }

    @Test
    void canonicalConstructor_nonNullForceCVSPackages_returnsImmutableCopy() {
        Set<PackageType> input = new HashSet<>(Set.of(PackageType.NPM, PackageType.MAVEN));
        CurationSettings settings = new CurationSettings(
                null, null, true, false, false, false, input);

        assertEquals(input, settings.forceCVSPackages());
        assertNotSame(input, settings.forceCVSPackages());
    }

    @Test
    void deserialize_validForceCVSPackages_parsesPackageTypes() throws Exception {
        CurationSettings settings = readSettings("""
                {"enabled":true,"blockDownloadEnabled":false,"indexVersionsCompliantEnabled":true,
                "allowDownloadWhenUnavailable":false,"forceCVSPackages":["npm","maven"]}
                """);

        assertEquals(Set.of(PackageType.NPM, PackageType.MAVEN), settings.forceCVSPackages());
    }

    @Test
    void deserialize_missingForceCVSPackages_defaultsToEmptySet() throws Exception {
        CurationSettings settings = readSettings("""
                {"enabled":true,"blockDownloadEnabled":false,"indexVersionsCompliantEnabled":true,
                "allowDownloadWhenUnavailable":false}
                """);

        assertTrue(settings.forceCVSPackages().isEmpty());
    }

    @Test
    void deserialize_filtersUnknownPackageTypes() throws Exception {
        CurationSettings settings = readSettings("""
                {"enabled":true,"blockDownloadEnabled":false,"indexVersionsCompliantEnabled":true,
                "allowDownloadWhenUnavailable":false,"forceCVSPackages":["npm","WRONG"]}
                """);

        assertEquals(Set.of(PackageType.NPM), settings.forceCVSPackages());
    }

    @Test
    void toBuilder_preservesForceCVSPackages() {
        CurationSettings settings = CurationSettings.builder()
                .enabled(true)
                .forceCVSPackages(Set.of(PackageType.NPM))
                .build();

        CurationSettings updated = settings.toBuilder()
                .blockDownloadEnabled(true)
                .build();

        assertEquals(Set.of(PackageType.NPM), updated.forceCVSPackages());
    }

    private static CurationSettings readSettings(String json) {
        return JsonUtils.getInstance().readValue(json, CurationSettings.class);
    }
}
