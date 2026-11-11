package org.artifactory.addon.curation;

import org.artifactory.common.repo.PackageType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForceCVSPackagesParserTest {

    @Test
    void fromStrings_nullOrEmpty_returnsEmptySet() {
        assertTrue(ForceCVSPackagesParser.fromStrings(null).isEmpty());
        assertTrue(ForceCVSPackagesParser.fromStrings(List.of()).isEmpty());
    }

    @Test
    void fromStrings_ignoresUnknownValues() {
        Set<PackageType> parsed = ForceCVSPackagesParser.fromStrings(List.of("NPM", "WRONG"));
        assertEquals(Set.of(PackageType.NPM), parsed);
    }

    @Test
    void fromStrings_isCaseInsensitive() {
        Set<PackageType> parsed = ForceCVSPackagesParser.fromStrings(List.of("npm", "Maven"));
        assertEquals(Set.of(PackageType.NPM, PackageType.MAVEN), parsed);
    }

    @Test
    void emptyIfNull_null_returnsEmptySet() {
        assertTrue(ForceCVSPackagesParser.emptyIfNull(null).isEmpty());
    }

    @Test
    void emptyIfNull_emptySet_returnsEmptySet() {
        assertTrue(ForceCVSPackagesParser.emptyIfNull(Set.of()).isEmpty());
    }

    @Test
    void emptyIfNull_nonEmptySet_returnsCopy() {
        Set<PackageType> input = Set.of(PackageType.NPM, PackageType.MAVEN);
        Set<PackageType> result = ForceCVSPackagesParser.emptyIfNull(input);
        assertEquals(input, result);
    }

    @Test
    void fromStrings_ignoresBlankValues() {
        Set<PackageType> parsed = ForceCVSPackagesParser.fromStrings(List.of(" ", "", "npm"));
        assertEquals(Set.of(PackageType.NPM), parsed);
    }

    @Test
    void fromStrings_deduplicatesPackageTypes() {
        Set<PackageType> parsed = ForceCVSPackagesParser.fromStrings(List.of("npm", "NPM", "npm"));
        assertEquals(Set.of(PackageType.NPM), parsed);
    }

    @Test
    void fromStrings_nullElement_isIgnored() {
        List<String> raw = new ArrayList<>();
        raw.add("npm");
        raw.add(null);
        Set<PackageType> parsed = ForceCVSPackagesParser.fromStrings(raw);
        assertEquals(Set.of(PackageType.NPM), parsed);
    }

    @Test
    void utilityClassHasPrivateConstructor() throws Exception {
        Constructor<ForceCVSPackagesParser> constructor = ForceCVSPackagesParser.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    @Test
    void parseString_nullOrBlank_returnsEmpty() throws Exception {
        Method parseString = ForceCVSPackagesParser.class.getDeclaredMethod("parseString", String.class);
        parseString.setAccessible(true);

        assertEquals(Optional.empty(), parseString.invoke(null, new Object[] {null}));
        assertEquals(Optional.empty(), parseString.invoke(null, " "));
    }

    @Test
    void parseString_unknownValue_returnsEmpty() throws Exception {
        Method parseString = ForceCVSPackagesParser.class.getDeclaredMethod("parseString", String.class);
        parseString.setAccessible(true);

        assertEquals(Optional.empty(), parseString.invoke(null, "UNKNOWN_PKG"));
    }

    @Test
    void parseString_validPackageType_returnsPackageType() throws Exception {
        Method parseString = ForceCVSPackagesParser.class.getDeclaredMethod("parseString", String.class);
        parseString.setAccessible(true);

        assertEquals(Optional.of(PackageType.NPM), parseString.invoke(null, "npm"));
    }
}
