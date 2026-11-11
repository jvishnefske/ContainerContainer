package org.artifactory.curation.gems;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author avivbr
 */
class GemVersionUploadInfoTest {

    @ParameterizedTest
    @MethodSource("versionForIndexFileCases")
    void getVersionForIndexFile_shouldReturnCorrectFormat_basedOnPlatform(String number, String platform,
            String expected) {
        // Arrange
        GemVersionUploadInfo info = new GemVersionUploadInfo();
        info.setNumber(number);
        info.setPlatform(platform);

        // Act & Assert
        assertEquals(expected, info.getVersionForIndexFile());
    }

    private static Stream<Arguments> versionForIndexFileCases() {
        return Stream.of(
                // Default "ruby" platform - version number only (case-insensitive check)
                Arguments.of("1.0.0", "ruby", "1.0.0"),
                Arguments.of("2.5.3", "RUBY", "2.5.3"),
                // Non-ruby platforms - version-platform format
                Arguments.of("1.0.0", "java", "1.0.0-java"),
                Arguments.of("2.0.0", "x86_64-linux", "2.0.0-x86_64-linux"),
                // Empty platform - version number only
                Arguments.of("3.2.1", "", "3.2.1"),
                // Null platform - version number only
                Arguments.of("4.0.0", null, "4.0.0")
        );
    }
}
