package org.artifactory.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PackageTypeUtilTest {

    @ParameterizedTest
    @MethodSource("provideUrlsForExtractPackageType")
    void extractPackageType(String requestUrl, String expectedPackageType) {
        String packageType = PackageTypeUtil.extractPackageType(requestUrl);
        if (expectedPackageType == null) {
            assertNull(packageType);
        } else {
            assertEquals(expectedPackageType, packageType);
        }
    }

    private static Stream<Arguments> provideUrlsForExtractPackageType() {
        return Stream.of(
                Arguments.of("/api/test/", null),
                Arguments.of("something/long/before/api/test/and/after", null),
                Arguments.of("/api/go/", "go"),
                Arguments.of("something/long/before/api/vagrant/and/after", "vagrant"),
                Arguments.of("/non_api/at/all", null),
                Arguments.of("/api/terraform/remote/whatever", "terraformbackend"),
                Arguments.of("/api/terraform/whatever", "terraform"),
                Arguments.of("/api/npm/whatever", "npm"),
                Arguments.of("/api/curation/whatever", null),
                Arguments.of("/invalid/url", null),
                Arguments.of("", null),
                Arguments.of(null, null)
        );
    }
}
