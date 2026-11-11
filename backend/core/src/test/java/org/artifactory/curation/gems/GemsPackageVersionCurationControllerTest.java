package org.artifactory.curation.gems;

import com.jfrog.ph.acl.exception.PackageCurationException;
import com.jfrog.ph.acl.model.curation.CurationPackageForResponse;
import com.jfrog.ph.acl.model.curation.CurationRequest;
import com.jfrog.ph.acl.model.curation.CurationResponse;
import com.jfrog.ph.acl.service.PackageSecurityService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.artifactory.common.ConstantValues.gemsCurationComplaintVersionEnabled;
import static org.artifactory.common.ConstantValues.gemsInfoFileMaxSizeBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author avivbr
 */
class GemsPackageVersionCurationControllerTest extends ArtifactoryHomeBoundJUnitTest {

    private PackageSecurityService packageSecurityService;
    private GemsVersionUploadInfoFetcher versionUploadInfoFetcher;
    private GemsPackageVersionCurationController controller;

    @BeforeEach
    void setUp() {
        packageSecurityService = mock(PackageSecurityService.class);
        versionUploadInfoFetcher = mock(GemsVersionUploadInfoFetcher.class);
        controller = new GemsPackageVersionCurationController(packageSecurityService, versionUploadInfoFetcher);
    }

    @Test
    void createMetadataCurationRequest_shouldBuildCorrectRequest_whenFetcherReturnsVersions() throws Exception {
        // Arrange
        when(packageSecurityService.getCurrentUserName()).thenReturn("user1");
        // v1 is older, v2 is newer - expect v2 to come first after sorting
        when(versionUploadInfoFetcher.fetchVersions(anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "1.0.0", Instant.parse("2024-01-01T00:00:00Z"),
                        "1.1.0-java", Instant.parse("2024-02-01T00:00:00Z")
                ));

        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0 |checksum:aaa", "1.1.0-java |checksum:bbb"));

        // Act
        CurationRequest request = controller.createMetadataCurationRequest(
                wrapper, "repo1", "http://host/compact/info/mygem");

        // Assert
        assertEquals("repo1", request.repoName());
        assertEquals("mygem", request.packages().getFirst().packageName());
        assertEquals(2, request.packages().getFirst().versions().size());
        // Versions should be sorted by date descending - newer (v2) comes first
        assertEquals("1.1.0-java", request.packages().getFirst().versions().get(0).version());
        assertEquals(Instant.parse("2024-02-01T00:00:00Z"),
                request.packages().getFirst().versions().get(0).releaseDate());
        assertEquals("1.0.0", request.packages().getFirst().versions().get(1).version());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"),
                request.packages().getFirst().versions().get(1).releaseDate());
    }

    @Test
    void createMetadataCurationRequest_shouldSortAccordingToVersionSemantics_whenAnyReleaseDateMissing()
            throws Exception {
        // Arrange
        when(packageSecurityService.getCurrentUserName()).thenReturn("user1");
        // 2.0.0 and 1.20.0 have dates, 1.10.0 has none
        when(versionUploadInfoFetcher.fetchVersions(anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "2.0.0", Instant.parse("2024-01-01T00:00:00Z"),
                        "1.20.0", Instant.parse("2024-02-01T00:00:00Z")
                ));

        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.10.0 |checksum:aaa", "2.0.0 |checksum:bbb", "1.20.0 |checksum:ccc"));

        // Act
        CurationRequest request = controller.createMetadataCurationRequest(
                wrapper, "repo1", "http://host/compact/info/mygem");

        // Assert
        assertEquals(3, request.packages().getFirst().versions().size());
        // Any missing date forces version semantics for all
        assertEquals("2.0.0", request.packages().getFirst().versions().get(0).version());
        assertEquals("1.20.0", request.packages().getFirst().versions().get(1).version());
        assertEquals("1.10.0", request.packages().getFirst().versions().get(2).version());
    }

    @Test
    void createMetadataCurationRequest_shouldSortAccordingToVersionSemantics_whenFetcherReturnsEmpty()
            throws Exception {
        // Arrange
        when(versionUploadInfoFetcher.fetchVersions(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyMap());

        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0 |checksum:aaa", "2.0.0 |checksum:bbb"));

        // Act
        CurationRequest request = controller.createMetadataCurationRequest(
                wrapper, "repo1", "http://host/compact/info/mygem");

        // Assert
        assertEquals("repo1", request.repoName());
        assertEquals("mygem", request.packages().getFirst().packageName());
        assertEquals(2, request.packages().getFirst().versions().size());
        // Sort falls back to version comparison when no createdAt is available
        assertEquals("2.0.0", request.packages().getFirst().versions().get(0).version());
        assertEquals("1.0.0", request.packages().getFirst().versions().get(1).version());
    }

    @Test
    void createMetadataCurationRequest_shouldParseVersionFromLineWithoutSpace_whenChecksumMissing() throws Exception {
        // Arrange
        when(versionUploadInfoFetcher.fetchVersions(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyMap());

        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0", "2.0.0 |checksum:bbb"));

        // Act
        CurationRequest request = controller.createMetadataCurationRequest(
                wrapper, "repo1", "http://host/compact/info/mygem");

        // Assert
        assertEquals(2, request.packages().getFirst().versions().size());
        assertEquals("2.0.0", request.packages().getFirst().versions().get(0).version());
        assertEquals("1.0.0", request.packages().getFirst().versions().get(1).version());
    }

    @Test
    void createMetadataCurationRequest_shouldHandleEmptyMetadata_whenNoVersionsPresent() throws Exception {
        // Arrange
        when(versionUploadInfoFetcher.fetchVersions(anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyMap());

        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("", "   "));

        // Act
        CurationRequest request = controller.createMetadataCurationRequest(
                wrapper, "repo1", "http://host/compact/info/mygem");

        // Assert
        assertTrue(request.packages().getFirst().versions().isEmpty());
    }

    @Test
    void getMetadataModelOrigin_shouldParseLines_whenGivenValidInput() throws Exception {
        // Act
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0 |checksum:aaa", "2.0.0-java deps|checksum:bbb"));

        // Assert
        assertEquals(List.of("1.0.0 |checksum:aaa", "2.0.0-java deps|checksum:bbb"),
                wrapper.getMetadataLines());
    }

    @Test
    void getMetadataModelOrigin_shouldIgnoreBlankLines_whenInputContainsWhitespace() throws Exception {
        // Act
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0 |checksum:aaa", "", "   ", "2.0.0-java deps|checksum:bbb"));

        // Assert
        assertEquals(List.of("1.0.0 |checksum:aaa", "2.0.0-java deps|checksum:bbb"),
                wrapper.getMetadataLines());
    }

    @Test
    void getMetadataModelOrigin_shouldExposeOriginLines_whenGivenValidInput() throws Exception {
        // Arrange
        String firstLine = "1.0.0 |checksum:aaa";
        String secondLine = "2.0.0-java deps|checksum:bbb";
        String content = toContent(firstLine, secondLine);

        // Act
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
        );

        // Assert
        assertEquals(List.of(firstLine, secondLine), wrapper.getMetadataLines());
    }

    @Test
    void getMetadataModelOrigin_shouldThrow_whenInputStreamReadFails() {
        // Arrange
        InputStream failingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };

        // Act & Assert
        assertThrows(PackageCurationException.class, () -> controller.getMetadataModelOrigin(failingStream));
    }

    @Test
    void getMetadataModelOrigin_shouldThrow_whenInputStreamExceedsSizeLimit() {
        // Arrange
        int maxBytes = gemsInfoFileMaxSizeBytes.getInt();
        InputStream largeStream = new InputStream() {
            private int remaining = maxBytes + 1;

            @Override
            public int read() {
                if (remaining-- <= 0) {
                    return -1;
                }
                return 'a';
            }
        };

        // Act & Assert
        assertThrows(PackageCurationException.class, () -> controller.getMetadataModelOrigin(largeStream));
    }

    @Test
    void getCuratedMetadataModel_shouldRemoveBlockedVersions_whenSomeVersionsAreBlocked() throws Exception {
        // Arrange
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0", "1.1.0-java |checksum:bbb"));
        wrapper.setPackageName("mygem");
        CurationResponse response = new CurationResponse(
                List.of(new CurationPackageForResponse("mygem", List.of("1.0.0")))
        );

        // Act
        GemsPackagesCurationMetadataWrapper curated = controller.getCuratedMetadataModel(response, wrapper);

        // Assert
        assertEquals(List.of("1.1.0-java |checksum:bbb"), curated.getMetadataLines());
    }

    @Test
    void getCuratedMetadataModel_shouldReturnOriginal_whenResponseIsNullOrEmpty() throws Exception {
        // Arrange
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0 |checksum:aaa"));
        wrapper.setPackageName("mygem");

        // Act & Assert
        assertSame(wrapper, controller.getCuratedMetadataModel(null, wrapper));
        assertSame(wrapper, controller.getCuratedMetadataModel(new CurationResponse(List.of()), wrapper));
        assertSame(wrapper, controller.getCuratedMetadataModel(
                new CurationResponse(List.of(new CurationPackageForResponse("mygem", List.of()))), wrapper));
    }

    @Test
    void getCuratedMetadataModel_shouldReturnOriginal_whenPackageNameMismatches() throws Exception {
        // Arrange
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0 |checksum:aaa"));
        wrapper.setPackageName("mygem");
        CurationResponse response = new CurationResponse(
                List.of(new CurationPackageForResponse("other", List.of("1.0.0")))
        );

        // Act
        GemsPackagesCurationMetadataWrapper result = controller.getCuratedMetadataModel(response, wrapper);

        // Assert - mismatch is treated as no blocked packages, original returned unchanged
        assertSame(wrapper, result);
    }

    @Test
    void getCuratedMetadataModel_shouldReturnEmpty_whenAllVersionsAreBlocked() throws Exception {
        // Arrange
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0 |checksum:aaa", "1.1.0-java |checksum:bbb"));
        wrapper.setPackageName("mygem");
        CurationResponse response = new CurationResponse(
                List.of(new CurationPackageForResponse("mygem", List.of("1.0.0", "1.1.0-java")))
        );

        // Act
        GemsPackagesCurationMetadataWrapper curated = controller.getCuratedMetadataModel(response, wrapper);

        // Assert
        assertEquals(List.of(), curated.getMetadataLines());
    }

    @Test
    void getCuratedMetadataModel_shouldKeepAllLines_whenBlockedVersionNotInOrigin()
            throws Exception {
        // Arrange
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0 |checksum:aaa", "1.1.0-java |checksum:bbb"));
        // need to set package name in test because in regular flow it's set in
        // createMetadataCurationRequest
        wrapper.setPackageName("mygem");
        CurationResponse response = new CurationResponse(
                List.of(new CurationPackageForResponse("mygem", List.of("9.9.9")))
        );

        // Act
        GemsPackagesCurationMetadataWrapper curated =
                controller.getCuratedMetadataModel(response, wrapper);

        // Assert - version 9.9.9 doesn't exist in origin, so all lines kept
        assertEquals(List.of("1.0.0 |checksum:aaa", "1.1.0-java |checksum:bbb"),
                curated.getMetadataLines());
        assertEquals("mygem", curated.getPackageName());
    }

    @Test
    void toInputStream_shouldProduceValidOutput_whenGivenParsedMetadata() throws Exception {
        // Arrange
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0 |checksum:aaa"));

        // Act
        InputStream inputStream = controller.toInputStream(wrapper);

        // Assert
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("---\n1.0.0 |checksum:aaa\n", content);
    }

    @Test
    void toInputStream_shouldReturnHeaderOnly_whenMetadataIsEmpty() throws Exception {
        // Arrange
        GemsPackagesCurationMetadataWrapper wrapper =
                new GemsPackagesCurationMetadataWrapper(List.of());

        // Act
        InputStream inputStream = controller.toInputStream(wrapper);

        // Assert
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("---\n", content);
    }

    @Test
    void toInputStream_shouldPreserveMultipleLines_whenGivenSeveralEntries()
            throws Exception {
        // Arrange
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0 |checksum:aaa",
                        "1.1.0-java deps|checksum:bbb",
                        "2.0.0 |checksum:ccc"));

        // Act
        InputStream inputStream = controller.toInputStream(wrapper);

        // Assert
        String content = new String(
                inputStream.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(
                "---\n1.0.0 |checksum:aaa\n"
                        + "1.1.0-java deps|checksum:bbb\n"
                        + "2.0.0 |checksum:ccc\n",
                content);
    }

    @Test
    void roundTrip_shouldPreserveContent_whenParsedCuratedAndSerialized()
            throws Exception {
        // Arrange - parse original metadata
        GemsPackagesCurationMetadataWrapper wrapper =
                controller.getMetadataModelOrigin(toInputStream(
                        "1.0.0 |checksum:aaa",
                        "1.1.0-java |checksum:bbb",
                        "2.0.0 |checksum:ccc"));
        wrapper.setPackageName("mygem");

        // Act - curate (remove 1.1.0-java), serialize, then re-parse
        CurationResponse response = new CurationResponse(
                List.of(new CurationPackageForResponse(
                        "mygem", List.of("1.1.0-java"))));
        GemsPackagesCurationMetadataWrapper curated =
                controller.getCuratedMetadataModel(response, wrapper);
        InputStream serialized = controller.toInputStream(curated);
        GemsPackagesCurationMetadataWrapper reparsed =
                controller.getMetadataModelOrigin(serialized);

        // Assert - round-trip should preserve the unblocked lines
        assertEquals(
                List.of("1.0.0 |checksum:aaa", "2.0.0 |checksum:ccc"),
                reparsed.getMetadataLines());
    }

    @Test
    void getMetadataModelOrigin_shouldParseRealCompactIndexResource() throws Exception {
        // Arrange
        byte[] content;
        try (InputStream resourceStream = getClass().getResourceAsStream("/gems/info/abbrev")) {
            assertNotNull(resourceStream, "Missing test resource: /gems/info/abbrev");
            content = resourceStream.readAllBytes();
        }

        // Act
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                new ByteArrayInputStream(content));

        // Assert
        assertEquals(List.of(
                "0.1.0 |checksum:6dd2980cc3fbff5950a63a46f9e5771a2a0235de18cb1f82b5ba0657777125e0,ruby:>= 2.3.0",
                "0.1.1 |checksum:3724b0d5420f4b26d31795ba28e1ffc58608c37d07a6cb283b548dc0e8b98300,ruby:>= 2.3.0",
                "0.1.2 |checksum:ad1b4eaaaed4cb722d5684d63949e4bde1d34f2a95e20db93aecfe7cbac74242,ruby:>= 2.3.0"
        ), wrapper.getMetadataLines());
    }

    @Test
    void createMetadataCurationRequest_shouldThrow_whenUrlMissingInfoPath() throws Exception {
        // Arrange
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0 |checksum:aaa"));

        // Act & Assert
        assertThrows(PackageCurationException.class, () -> controller.createMetadataCurationRequest(
                wrapper, "repo1", "http://host/compact/versions/mygem"));
    }

    @Test
    void createMetadataCurationRequest_shouldThrow_whenUrlHasEmptyPackageName() throws Exception {
        // Arrange
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0 |checksum:aaa"));

        // Act & Assert
        assertThrows(PackageCurationException.class, () -> controller.createMetadataCurationRequest(
                wrapper, "repo1", "http://host/compact/info/"));
    }

    @Test
    void createMetadataCurationRequest_shouldThrow_whenUrlIsInvalid() throws Exception {
        // Arrange
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream("1.0.0 |checksum:aaa"));

        // Act & Assert
        assertThrows(PackageCurationException.class, () -> controller.createMetadataCurationRequest(
                wrapper, "repo1", "http://host/compact/info/%"));
    }

    @Test
    void getCuratedMetadataModel_shouldPreserveOriginalOrder_whenBlockedVersionsOrderedDifferently()
            throws Exception {
        // Arrange - original index has versions in a specific order
        GemsPackagesCurationMetadataWrapper wrapper = controller.getMetadataModelOrigin(
                toInputStream(
                        "0.1.0 |checksum:aaa",
                        "1.0.0-java deps|checksum:bbb",
                        "1.1.0 |checksum:ccc",
                        "2.0.0-beta |checksum:ddd",
                        "2.0.0 |checksum:eee",
                        "3.0.0 |checksum:fff",
                        "3.1.0 |checksum:ggg"));
        wrapper.setPackageName("mygem");

        // CVS response lists blocked versions in reverse order compared to the index
        CurationResponse response = new CurationResponse(
                List.of(new CurationPackageForResponse("mygem",
                        List.of("3.0.0", "2.0.0-beta", "1.0.0-java"))));

        // Act
        GemsPackagesCurationMetadataWrapper curated =
                controller.getCuratedMetadataModel(response, wrapper);

        // Assert - remaining versions keep the original index order
        assertEquals(List.of(
                "0.1.0 |checksum:aaa",
                "1.1.0 |checksum:ccc",
                "2.0.0 |checksum:eee",
                "3.1.0 |checksum:ggg"), curated.getMetadataLines());
    }

    private ByteArrayInputStream toInputStream(String... rows) {
        return new ByteArrayInputStream(toContent(rows).getBytes(StandardCharsets.UTF_8));
    }

    private String toContent(String... rows) {
        StringBuilder builder = new StringBuilder("---");
        for (String row : rows) {
            builder.append('\n').append(row);
        }
        return builder.toString();
    }

    @Test
    void shouldCheckForCuration_shouldReturnCorrectly_basedOnPathAndConfig() {
        // Arrange & Act & Assert
        this.setStringSystemProperty(gemsCurationComplaintVersionEnabled, String.valueOf(true));
        assertTrue(controller.shouldCheckForCuration("info/mygem"));
        assertTrue(controller.shouldCheckForCuration("/info/mygem"));
        assertFalse(controller.shouldCheckForCuration("versions"));
        assertFalse(controller.shouldCheckForCuration("info/mygem/extra"));

        this.setStringSystemProperty(gemsCurationComplaintVersionEnabled, String.valueOf(false));
        assertFalse(controller.shouldCheckForCuration("info/mygem"));
    }

}
