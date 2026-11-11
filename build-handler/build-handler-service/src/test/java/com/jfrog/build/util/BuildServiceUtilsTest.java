package com.jfrog.build.util;

import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.exception.http.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.jfrog.build.util.BuildServiceUtils.validateBuildCoordinatesNotHidden;
import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildServiceUtilsTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void shouldDecodeParams_whenUserAgentMissing_returnsFalse() {
        when(request.getHeader(USER_AGENT)).thenReturn(null);

        assertFalse(BuildServiceUtils.shouldDecodeParams(request));
    }

    @Test
    void shouldDecodeParams_whenNotBuildClient_returnsFalse() {
        when(request.getHeader(USER_AGENT)).thenReturn("curl/8.0");

        assertFalse(BuildServiceUtils.shouldDecodeParams(request));
    }

    @Test
    void shouldDecodeParams_whenBuildInfoOlderThanCutover_returnsTrue() {
        when(request.getHeader(USER_AGENT)).thenReturn("ArtifactoryBuildClient/2.0.10");

        assertTrue(BuildServiceUtils.shouldDecodeParams(request));
    }

    @Test
    void shouldDecodeParams_whenBuildInfoSnapshot_returnsFalse() {
        when(request.getHeader(USER_AGENT)).thenReturn("ArtifactoryBuildClient/2.0.10-SNAPSHOT");

        assertFalse(BuildServiceUtils.shouldDecodeParams(request));
    }

    @Test
    void shouldDecodeParams_whenBuildInfoNewerThanCutover_returnsFalse() {
        when(request.getHeader(USER_AGENT)).thenReturn("ArtifactoryBuildClient/3.0.0");

        assertFalse(BuildServiceUtils.shouldDecodeParams(request));
    }

    @Test
    void shouldDecodeParams_whenBuildInfoExactlyCutover_returnsTrue() {
        when(request.getHeader(USER_AGENT)).thenReturn("ArtifactoryBuildClient/2.0.11");

        assertTrue(BuildServiceUtils.shouldDecodeParams(request));
    }

    @Test
    void validateBuildCoordinatesNotHidden_shouldAcceptPlainCoordinates() {
        assertDoesNotThrow(() -> validateBuildCoordinatesNotHidden("my-build", "42"));
    }

    @Test
    void validateBuildCoordinatesNotHidden_shouldAcceptDotsThatAreNotLeading() {
        // Dotted versions are common and legitimate as long as the value does not *start* with a dot.
        assertDoesNotThrow(() -> validateBuildCoordinatesNotHidden("my.build", "1.0.0"));
        assertDoesNotThrow(() -> validateBuildCoordinatesNotHidden("release-2.3", "2.170.0-hotfix.1"));
    }

    @Test
    void validateBuildCoordinatesNotHidden_shouldRejectHiddenBuildName() {
        // The RTDEV-94003 exploit used a ".jfrog" build name to land under a hidden directory.
        assertThrows(BadRequestException.class, () -> validateBuildCoordinatesNotHidden(".jfrog", "1"));
    }

    @Test
    void validateBuildCoordinatesNotHidden_shouldRejectDotAndDotDotNames() {
        assertThrows(BadRequestException.class, () -> validateBuildCoordinatesNotHidden(".", "1"));
        assertThrows(BadRequestException.class, () -> validateBuildCoordinatesNotHidden("..", "1"));
        assertThrows(BadRequestException.class, () -> validateBuildCoordinatesNotHidden(".hidden", "1"));
    }

    @Test
    void validateBuildCoordinatesNotHidden_shouldRejectLeadingWhitespaceBeforeDot() {
        assertThrows(BadRequestException.class, () -> validateBuildCoordinatesNotHidden(" .jfrog", "1"));
    }

    @Test
    void validateBuildCoordinatesNotHidden_shouldRejectHiddenBuildNumber() {
        assertThrows(BadRequestException.class, () -> validateBuildCoordinatesNotHidden("my-build", ".5"));
    }
}
