package org.artifactory.addon.curation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.common.repo.PackageType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForceCVSPackagesDeserializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void toggleCuration_missingForceCVSPackages_defaultsToEmptySet() throws Exception {
        ToggleCuration toggleCuration = objectMapper.readValue("""
                {"enabled":true,"blockDownloadEnabled":false,"indexVersionsCompliantEnabled":true}
                """, ToggleCuration.class);

        assertTrue(toggleCuration.forceCVSPackages().isEmpty());
    }

    @Test
    void toggleCuration_filtersUnknownPackageTypes() throws Exception {
        ToggleCuration toggleCuration = objectMapper.readValue("""
                {"enabled":true,"blockDownloadEnabled":false,"indexVersionsCompliantEnabled":true,
                "forceCVSPackages":["NPM","WRONG"]}
                """, ToggleCuration.class);

        assertEquals(Set.of(PackageType.NPM), toggleCuration.forceCVSPackages());
    }

    @Test
    void toggleCuration_acceptsLowerCasePackageTypes() throws Exception {
        ToggleCuration toggleCuration = objectMapper.readValue("""
                {"enabled":true,"blockDownloadEnabled":false,"indexVersionsCompliantEnabled":true,
                "forceCVSPackages":["npm"]}
                """, ToggleCuration.class);

        assertEquals(Set.of(PackageType.NPM), toggleCuration.forceCVSPackages());
    }

    @Test
    void deserialize_emptyArray_returnsEmptySet() throws Exception {
        ForceCVSPackagesDeserializer deserializer = new ForceCVSPackagesDeserializer();
        Set<PackageType> result = deserializer.deserialize(
                objectMapper.createParser("[]"),
                objectMapper.getDeserializationContext());

        assertTrue(result.isEmpty());
    }

    @Test
    void deserialize_arrayWithOnlyNullElements_returnsEmptySet() throws Exception {
        ForceCVSPackagesDeserializer deserializer = new ForceCVSPackagesDeserializer();
        Set<PackageType> result = deserializer.deserialize(
                objectMapper.createParser("[null, null]"),
                objectMapper.getDeserializationContext());

        assertTrue(result.isEmpty());
    }

    @Test
    void deserialize_nullNode_returnsEmptySet() throws Exception {
        ForceCVSPackagesDeserializer deserializer = new ForceCVSPackagesDeserializer();
        Set<PackageType> result = deserializer.deserialize(
                objectMapper.createParser("null"),
                objectMapper.getDeserializationContext());

        assertTrue(result.isEmpty());
    }

    @Test
    void deserialize_nonArray_returnsEmptySet() throws Exception {
        ForceCVSPackagesDeserializer deserializer = new ForceCVSPackagesDeserializer();
        Set<PackageType> result = deserializer.deserialize(
                objectMapper.createParser("\"npm\""),
                objectMapper.getDeserializationContext());

        assertTrue(result.isEmpty());
    }

    @Test
    void deserialize_ignoresNonTextualArrayElements() throws Exception {
        ForceCVSPackagesDeserializer deserializer = new ForceCVSPackagesDeserializer();
        Set<PackageType> result = deserializer.deserialize(
                objectMapper.createParser("[\"npm\", 123, true, null]"),
                objectMapper.getDeserializationContext());

        assertEquals(Set.of(PackageType.NPM), result);
    }
}
