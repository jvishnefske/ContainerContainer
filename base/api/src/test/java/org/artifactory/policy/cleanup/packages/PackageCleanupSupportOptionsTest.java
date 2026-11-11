package org.artifactory.policy.cleanup.packages;

import org.artifactory.common.repo.PackageType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackageCleanupSupportOptionsTest {

    static Stream<Arguments> compareToTestData() {
        return Stream.of(
                Arguments.of(
                        PackageCleanupSupportOptions.builder().packageType(PackageType.BOWER).nVersionSupport(true)
                                .build(),
                        PackageCleanupSupportOptions.builder().packageType(PackageType.COCOAPODS).nVersionSupport(false)
                                .build(),
                        false
                ),
                Arguments.of(
                        PackageCleanupSupportOptions.builder().packageType(PackageType.COCOAPODS).nVersionSupport(false)
                                .build(),
                        PackageCleanupSupportOptions.builder().packageType(PackageType.BOWER).nVersionSupport(true)
                                .build(),
                        false
                ),
                Arguments.of(
                        PackageCleanupSupportOptions.builder().packageType(PackageType.BOWER).nVersionSupport(true)
                                .build(),
                        PackageCleanupSupportOptions.builder().packageType(PackageType.BOWER).nVersionSupport(true)
                                .build(),
                        true
                ),
                Arguments.of(
                        PackageCleanupSupportOptions.builder().packageType(PackageType.BOWER).nVersionSupport(false)
                                .build(),
                        PackageCleanupSupportOptions.builder().packageType(PackageType.BOWER).nVersionSupport(true)
                                .build(),
                        true
                )
        );
    }

    @ParameterizedTest
    @MethodSource("compareToTestData")
    void compareTo(PackageCleanupSupportOptions option1, PackageCleanupSupportOptions option2, boolean expected) {
        assertEquals(expected, option1.compareTo(option2) == 0);
    }
}