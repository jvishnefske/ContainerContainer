package org.artifactory.maven.curation;

import com.jfrog.ph.acl.exception.PackageCurationException;
import com.jfrog.ph.acl.model.curation.CurationPackageForResponse;
import com.jfrog.ph.acl.model.curation.CurationRequest;
import com.jfrog.ph.acl.model.curation.CurationResponse;
import com.jfrog.ph.acl.service.PackageArtifactService;
import com.jfrog.ph.acl.service.PackageSecurityService;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.artifactory.common.repo.PackageType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.common.ConstantValues.gradleCurationComplaintVersionEnabled;
import static org.artifactory.common.ConstantValues.mavenCurationComplaintVersionEnabled;
import static org.artifactory.common.ConstantValues.mavenReleaseDatesParserForCurationEnabled;
import static org.artifactory.common.ConstantValues.sbtCurationComplaintVersionEnabled;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class GAVCPackageVersionCurationControllerTest extends ArtifactoryHomeBoundJUnitTest {

    private PackageSecurityService packageSecurityService;
    private PackageArtifactService packageArtifactService;
    private GAVCPackageVersionCurationController controller;

    @BeforeEach
    void setUp() {
        packageSecurityService = mock(PackageSecurityService.class);
        packageArtifactService = mock(PackageArtifactService.class);
        controller = new GAVCPackageVersionCurationController(
                packageSecurityService, packageArtifactService, PackageType.MAVEN);
    }

    @Test
    void testCreateMetadataCurationRequest() throws PackageCurationException {
        Metadata metadata = new Metadata();
        metadata.setGroupId("com.example");
        metadata.setArtifactId("demo");
        Versioning versioning = new Versioning();
        versioning.setLatest("1.0.0");
        versioning.setRelease("2.0.0");
        versioning.setVersions(List.of("1.0.0", "2.0.0"));
        metadata.setVersioning(versioning);

        when(packageSecurityService.getCurrentUserName()).thenReturn("user1");

        // Simulate version dates
        Map<String, Instant> versionDates = new HashMap<>();
        versionDates.put("1.0.0", Instant.parse("2023-01-01T00:00:00Z"));
        versionDates.put("2.0.0", Instant.parse("2023-02-01T00:00:00Z"));
        GAVCPackageVersionCurationController spy = spy(controller);
        doReturn(versionDates).when(spy)
                .getMavenMetadataDatesInfo(anyString(), anyString(), eq(new HashSet<>(List.of("1.0.0", "2.0.0"))));

        CurationRequest request =
                spy.createMetadataCurationRequest(new GAVCPackagesCurationMetadataWrapper(metadata), "repo1",
                        "http://host/path/maven-metadata.xml");
        assertEquals("repo1", request.repoName());
        assertEquals("com.example:demo", request.packages().getFirst().packageName());
        assertEquals(2, request.packages().getFirst().versions().size());
    }

    @Test
    void testGetCuratedMetadataModelRemovesBlockedVersions() throws Exception {
        Metadata metadata = new Metadata();
        metadata.setGroupId("g");
        metadata.setArtifactId("a");
        Versioning versioning = new Versioning();
        versioning.setLatest("1.0");
        versioning.setRelease("1.0");
        versioning.setVersions(new ArrayList<>(List.of("1.0", "2.0", "5.0.0.Alpha2")));
        versioning.setLastUpdated("20240101");
        metadata.setVersioning(versioning);

        CurationResponse response =
                new CurationResponse(List.of(new CurationPackageForResponse("g:a", List.of("5.0.0.Alpha2"))));

        Metadata curated =
                controller.getCuratedMetadataModel(response, new GAVCPackagesCurationMetadataWrapper(metadata))
                        .getMetadata();
        assertFalse(curated.getVersioning().getVersions().contains("5.0.0.Alpha2"));
        assertEquals("2.0", curated.getVersioning().getLatest());
    }

    @Test
    void testGetCuratedMetadataModelAllVersionsAreBlocked() throws Exception {
        Metadata metadata = new Metadata();
        metadata.setGroupId("g");
        metadata.setArtifactId("a");
        Versioning versioning = new Versioning();
        versioning.setLatest("1.0");
        versioning.setRelease("1.0");
        versioning.setVersions(new ArrayList<>(List.of("1.0", "2.0", "5.0.0.Alpha2")));
        versioning.setLastUpdated("20240101");
        metadata.setVersioning(versioning);

        CurationResponse response =
                new CurationResponse(List.of(
                        new CurationPackageForResponse("g:a", List.of("1.0", "2.0", "5.0.0.Alpha2"))));

        Metadata curated =
                controller.getCuratedMetadataModel(response, new GAVCPackagesCurationMetadataWrapper(metadata))
                        .getMetadata();
        assertFalse(curated.getVersioning().getVersions().isEmpty());
    }

    @Test
    void testGetMetadataModelOriginSuccess() throws Exception {
        String validXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                    <groupId>com.example</groupId>
                    <artifactId>test-artifact</artifactId>
                    <versioning>
                        <latest>2.0.0</latest>
                        <release>2.0.0</release>
                        <versions>
                            <version>1.0.0</version>
                            <version>2.0.0</version>
                        </versions>
                        <lastUpdated>20240101120000</lastUpdated>
                    </versioning>
                </metadata>
                """;

        InputStream inputStream = new ByteArrayInputStream(validXml.getBytes());

        GAVCPackagesCurationMetadataWrapper result = controller.getMetadataModelOrigin(inputStream);

        assertNotNull(result);
        assertNotNull(result.getMetadata());
        assertEquals("com.example", result.getMetadata().getGroupId());
        assertEquals("test-artifact", result.getMetadata().getArtifactId());
        assertEquals(2, result.getMetadata().getVersioning().getVersions().size());
        assertTrue(result.getMetadata().getVersioning().getVersions().contains("1.0.0"));
        assertTrue(result.getMetadata().getVersioning().getVersions().contains("2.0.0"));
    }

    @Test
    void testGetMetadataModelOriginThrowsOnInvalidXml() {
        InputStream invalid = new ByteArrayInputStream("<not-xml>".getBytes());
        assertThrows(PackageCurationException.class, () -> controller.getMetadataModelOrigin(invalid));
    }

    @Test
    void testVerifyCurationResponseNullResponse() {
        assertThrows(PackageCurationException.class, () -> {
            controller.getCuratedMetadataModel(null, new GAVCPackagesCurationMetadataWrapper(new Metadata()));
        });
    }

    @Test
    void testVerifyCurationResponseEmptyBlockedPackagesThrowsException() {
        CurationResponse response = new CurationResponse(List.of());
        assertThrows(PackageCurationException.class, () -> {
            controller.getCuratedMetadataModel(response, new GAVCPackagesCurationMetadataWrapper(new Metadata()));
        });
    }

    @Test
    void testVerifyCurationResponseInvalidPackageNameThrowsException() {
        CurationPackageForResponse invalidPackage = new CurationPackageForResponse("", List.of("1.0"));
        CurationResponse response = new CurationResponse(List.of(invalidPackage));
        assertThrows(PackageCurationException.class, () -> {
            controller.getCuratedMetadataModel(response, new GAVCPackagesCurationMetadataWrapper(new Metadata()));
        });

        invalidPackage = new CurationPackageForResponse(null, List.of("1.0"));
        CurationResponse response2 = new CurationResponse(List.of(invalidPackage));
        assertThrows(PackageCurationException.class, () -> {
            controller.getCuratedMetadataModel(response2, new GAVCPackagesCurationMetadataWrapper(new Metadata()));
        });
    }

    @Test
    void testShouldCheckForCuration() {
        this.setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
        // Mock enabled
        assertTrue(controller.shouldCheckForCuration("some/path/maven-metadata.xml"));
        assertFalse(controller.shouldCheckForCuration("some/path/other-file.txt"));

        this.setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(false));
        // Mock disabled
        assertFalse(controller.shouldCheckForCuration("some/path/maven-metadata.xml"));
    }

    @Test
    void testShouldCheckForCuration_sbtUsesSbtSystemProperty() {
        GAVCPackageVersionCurationController sbtController =
                new GAVCPackageVersionCurationController(packageSecurityService, packageArtifactService,
                        PackageType.SBT);

        this.setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(true));
        assertTrue(sbtController.shouldCheckForCuration("some/path/maven-metadata.xml"));

        this.setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(false));
        assertFalse(sbtController.shouldCheckForCuration("some/path/maven-metadata.xml"));
    }

    @Test
    void testShouldCheckForCuration_gradleUsesGradleSystemProperty() {
        GAVCPackageVersionCurationController gradleController =
                new GAVCPackageVersionCurationController(packageSecurityService, packageArtifactService,
                        PackageType.GRADLE);

        this.setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(true));
        assertTrue(gradleController.shouldCheckForCuration("some/path/maven-metadata.xml"));

        this.setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(false));
        assertFalse(gradleController.shouldCheckForCuration("some/path/maven-metadata.xml"));
    }

    @Test
    void testShouldCheckForCuration_mavenIgnoresGradleAndSbtFlags() {
        this.setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(false));
        this.setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(true));
        this.setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(true));
        assertFalse(controller.shouldCheckForCuration("some/path/maven-metadata.xml"));

        this.setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
        this.setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(false));
        this.setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(false));
        assertTrue(controller.shouldCheckForCuration("some/path/maven-metadata.xml"));
    }

    @Test
    void testShouldCheckForCuration_gradleIgnoresMavenAndSbtFlags() {
        GAVCPackageVersionCurationController gradleController =
                new GAVCPackageVersionCurationController(packageSecurityService, packageArtifactService,
                        PackageType.GRADLE);

        this.setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(true));
        this.setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(false));
        this.setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(false));
        assertTrue(gradleController.shouldCheckForCuration("some/path/maven-metadata.xml"));

        this.setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(false));
        this.setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
        this.setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(true));
        assertFalse(gradleController.shouldCheckForCuration("some/path/maven-metadata.xml"));
    }

    @Test
    void testShouldCheckForCuration_sbtIgnoresMavenAndGradleFlags() {
        GAVCPackageVersionCurationController sbtController =
                new GAVCPackageVersionCurationController(packageSecurityService, packageArtifactService,
                        PackageType.SBT);

        this.setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(true));
        this.setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(false));
        this.setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(false));
        assertTrue(sbtController.shouldCheckForCuration("some/path/maven-metadata.xml"));

        this.setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(false));
        this.setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
        this.setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(true));
        assertFalse(sbtController.shouldCheckForCuration("some/path/maven-metadata.xml"));
    }

    /** Package types not branched in the complaint-version switch always yield {@code false} for metadata paths. */
    @Test
    void testShouldCheckForCuration_unmappedPackageTypeIgnoresAllComplaintVersionFlags() {
        GAVCPackageVersionCurationController ivyController =
                new GAVCPackageVersionCurationController(packageSecurityService, packageArtifactService,
                        PackageType.IVY);

        try {
            this.setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
            this.setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(true));
            this.setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(true));
            assertFalse(ivyController.shouldCheckForCuration("some/path/maven-metadata.xml"));
            assertFalse(ivyController.shouldCheckForCuration("org/foo/bar/maven-metadata.xml"));

            this.setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(false));
            this.setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(false));
            this.setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(false));
            assertFalse(ivyController.shouldCheckForCuration("some/path/maven-metadata.xml"));
        } finally {
            this.setStringSystemProperty(mavenCurationComplaintVersionEnabled, String.valueOf(true));
            this.setStringSystemProperty(gradleCurationComplaintVersionEnabled, String.valueOf(false));
            this.setStringSystemProperty(sbtCurationComplaintVersionEnabled, String.valueOf(false));
        }
    }

    @Test
    void testGetMavenMetadataDatesInfoWhenReleaseDatesParserDisabled() {
        this.setStringSystemProperty(mavenReleaseDatesParserForCurationEnabled, String.valueOf(false));
        Map<String, Instant> result =
                controller.getMavenMetadataDatesInfo("repo", "http://host/path/maven-metadata.xml",
                        new HashSet<>(List.of("1.0.0", "2.0.0")));
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetMavenMetadataDatesInfoParsesDatesSuccessfully() throws Exception {
        String html = """
                <a href="1.0.0/" title="1.0.0/">1.0.0/</a> 2023-01-01 12:00
                <a href="2.0.0/" title="2.0.0/">2.0.0/</a> 2023-02-01 13:30
                """;
        Set<String> versionsSet = new HashSet<>(List.of("1.0.0", "2.0.0"));
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(new BasicHttpEntity() {{
                setContent(new ByteArrayInputStream(html.getBytes()));
            }});
        GAVCPackageVersionCurationController spy = spy(controller);
        doReturn(response).when(packageArtifactService).executeRemoteHttpRequest(anyString(), any());
        Map<String, Instant> result =
                spy.getMavenMetadataDatesInfo("repo", "http://host/path/maven-metadata.xml", versionsSet);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("1.0.0"));
        assertTrue(result.containsKey("2.0.0"));
    }

    @Test
    void testToInputStreamSuccess() throws Exception {
        Metadata metadata = new Metadata();
        metadata.setGroupId("com.example");
        metadata.setArtifactId("demo");
        Versioning versioning = new Versioning();
        versioning.setVersions(List.of("1.0.0"));
        metadata.setVersioning(versioning);

        InputStream inputStream = controller.toInputStream(new GAVCPackagesCurationMetadataWrapper(metadata));
        assertNotNull(inputStream);
        String content = new String(inputStream.readAllBytes());
        assertTrue(content.contains("<groupId>com.example</groupId>"));
        assertTrue(content.contains("<artifactId>demo</artifactId>"));
    }

    @Test
    void testToInputStreamException() {
        GAVCPackageVersionCurationController spyController = spy(controller);
        assertThrows(PackageCurationException.class, () -> {
            spyController.toInputStream(null);
        });
    }

    @Test
    void testCreateMetadataCurationRequestReleaseDateIsNull() throws PackageCurationException {
        Metadata metadata = new Metadata();
        metadata.setGroupId("com.example");
        metadata.setArtifactId("demo");
        Versioning versioning = new Versioning();
        versioning.setVersions(List.of("1.0.0", "2.0.0"));
        versioning.setLatest("2.0.0");
        versioning.setRelease("2.0.0");
        metadata.setVersioning(versioning);
        Set<String> versionsSet = new HashSet<>(versioning.getVersions());

        when(packageSecurityService.getCurrentUserName()).thenReturn("user1");

        // Provide an empty map so releaseDate will be null for all versions
        GAVCPackageVersionCurationController spy = spy(controller);
        doReturn(new HashMap<String, Instant>()).when(spy)
                .getMavenMetadataDatesInfo(anyString(), anyString(), eq(versionsSet));

        CurationRequest request =
                spy.createMetadataCurationRequest(new GAVCPackagesCurationMetadataWrapper(metadata), "repo1",
                        "http://host/path/maven-metadata.xml");
        assertEquals("repo1", request.repoName());
        assertEquals("com.example:demo", request.packages().getFirst().packageName());
        // All release dates should be Instant.EPOCH
        request.packages().getFirst().versions().forEach(v -> assertEquals(Instant.EPOCH, v.releaseDate()));
    }

    @Test
    void testVerifyMetadata() {
        Metadata metadata = new Metadata();
        assertThrows(PackageCurationException.class, () -> {
            controller.verifyMetadata(metadata);
        });

        Versioning versioning = new Versioning();
        versioning.setLatest("1.0");
        versioning.setRelease("1.0");
        metadata.setVersioning(versioning);
        assertThrows(PackageCurationException.class, () -> {
            controller.verifyMetadata(metadata);
        });

        versioning.setVersions(List.of("1.0"));
        metadata.setVersioning(versioning);
        assertThrows(PackageCurationException.class, () -> {
            controller.verifyMetadata(metadata);
        });

        metadata.setGroupId("g");
        assertThrows(PackageCurationException.class, () -> {
            controller.verifyMetadata(metadata);
        });

        metadata.setArtifactId("a");
        // Now it should pass
        assertDoesNotThrow(() -> controller.verifyMetadata(metadata));
    }

    @Test
    void testCreateMetadataCurationRequestNewerVersionByReleaseDateButOlderIsLatest() throws PackageCurationException {
        Metadata metadata = new Metadata();
        metadata.setGroupId("com.example");
        metadata.setArtifactId("demo");
        Versioning versioning = new Versioning();
        versioning.setVersions(List.of("1.0.0", "2.0.0"));
        versioning.setLatest("1.0.0"); // Older version is marked as latest
        versioning.setRelease("1.0.0"); // Same as latest
        metadata.setVersioning(versioning);

        when(packageSecurityService.getCurrentUserName()).thenReturn("user1");

        // Version 2.0.0 has a newer release date but is not marked as latest
        Map<String, Instant> versionDates = new HashMap<>();
        versionDates.put("1.0.0", Instant.parse("2023-01-01T00:00:00Z")); // Latest but older release date
        versionDates.put("2.0.0", Instant.parse("2023-06-01T00:00:00Z")); // Newer release date but not latest

        GAVCPackageVersionCurationController spy = spy(controller);
        doReturn(versionDates).when(spy)
                .getMavenMetadataDatesInfo(anyString(), anyString(), eq(new HashSet<>(List.of("1.0.0", "2.0.0"))));

        CurationRequest request =
                spy.createMetadataCurationRequest(new GAVCPackagesCurationMetadataWrapper(metadata), "repo1",
                        "http://host/path/maven-metadata.xml");

        // Verify the latest version (1.0.0) is first in the list despite having an older release date
        assertEquals("1.0.0", request.packages().getFirst().versions().getFirst().version());
        assertEquals("2.0.0", request.packages().getFirst().versions().get(1).version());

        // Verify that despite 2.0.0 having a newer release date, 1.0.0 comes first because it's marked as latest
        assertEquals(Instant.parse("2023-01-01T00:00:00Z"),
                request.packages().getFirst().versions().getFirst().releaseDate());
        assertEquals(Instant.parse("2023-06-01T00:00:00Z"),
                request.packages().getFirst().versions().get(1).releaseDate());
    }

    @Test
    void testCreateMetadataCurationRequestWithVersionTagButNoLatestOrRelease() throws PackageCurationException {
        // Test case where latest and release are blank but version tag is present
        Metadata metadata = new Metadata();
        metadata.setGroupId("com.example");
        metadata.setArtifactId("demo");
        metadata.setVersion("2.0.0");
        Versioning versioning = new Versioning();
        versioning.setVersions(List.of("1.0.0", "2.0.0"));
        // latest and release are not set (blank)
        metadata.setVersioning(versioning);

        when(packageSecurityService.getCurrentUserName()).thenReturn("user1");

        // Simulate version dates
        Map<String, Instant> versionDates = new HashMap<>();
        versionDates.put("1.0.0", Instant.parse("2023-01-01T00:00:00Z"));
        versionDates.put("2.0.0", Instant.parse("2023-02-01T00:00:00Z"));
        GAVCPackageVersionCurationController spy = spy(controller);
        doReturn(versionDates).when(spy)
                .getMavenMetadataDatesInfo(anyString(), anyString(), eq(new HashSet<>(List.of("1.0.0", "2.0.0"))));

        CurationRequest request =
                spy.createMetadataCurationRequest(new GAVCPackagesCurationMetadataWrapper(metadata), "repo1",
                        "http://host/path/maven-metadata.xml");
        assertEquals("repo1", request.repoName());
        assertEquals("com.example:demo", request.packages().getFirst().packageName());
        assertEquals(2, request.packages().getFirst().versions().size());
        // Version tag (2.0.0) should be first in the list
        assertEquals("2.0.0", request.packages().getFirst().versions().getFirst().version());
    }

    @Test
    void testCreateMetadataCurationRequestUsesCorrectDownloadUrls() throws PackageCurationException {
        // Test that download URLs are generated correctly using MavenModelUtils.getDownloadUrlForPomFile
        Metadata metadata = new Metadata();
        metadata.setGroupId("com.example");
        metadata.setArtifactId("demo");
        metadata.setVersion("2.0.0");
        Versioning versioning = new Versioning();
        versioning.setVersions(List.of("1.0.0", "2.0.0"));
        metadata.setVersioning(versioning);

        when(packageSecurityService.getCurrentUserName()).thenReturn("user1");

        Map<String, Instant> versionDates = new HashMap<>();
        versionDates.put("1.0.0", Instant.parse("2023-01-01T00:00:00Z"));
        versionDates.put("2.0.0", Instant.parse("2023-02-01T00:00:00Z"));
        GAVCPackageVersionCurationController spy = spy(controller);
        doReturn(versionDates).when(spy)
                .getMavenMetadataDatesInfo(anyString(), anyString(), eq(new HashSet<>(List.of("1.0.0", "2.0.0"))));

        CurationRequest request =
                spy.createMetadataCurationRequest(new GAVCPackagesCurationMetadataWrapper(metadata), "repo1",
                        "http://host/path/maven-metadata.xml");

        // Verify URLs are correctly formatted
        String baseUrl = "http://host/path";
        String expectedUrl1 = baseUrl + "/1.0.0/demo-1.0.0.pom";
        String expectedUrl2 = baseUrl + "/2.0.0/demo-2.0.0.pom";
        assertEquals(expectedUrl1, request.packages().getFirst().versions().get(1).downloadUrl());
        assertEquals(expectedUrl2, request.packages().getFirst().versions().getFirst().downloadUrl());
    }

    @Test
    void testGetCuratedMetadataModelWithVersionTagButNoLatestOrRelease() throws PackageCurationException {
        // Test case where latest and release are blank but version tag is present
        // When versions are blocked, the version tag should be updated to the last remaining version
        Metadata metadata = new Metadata();
        metadata.setGroupId("com.example");
        metadata.setArtifactId("demo");
        metadata.setVersion("3.0.0");
        Versioning versioning = new Versioning();
        versioning.setVersions(List.of("1.0.0", "2.0.0", "3.0.0"));
        // latest and release are not set (blank)
        metadata.setVersioning(versioning);

        // Block version 3.0.0 (the current version tag)
        CurationResponse response =
                new CurationResponse(List.of(new CurationPackageForResponse("com.example:demo", List.of("3.0.0"))));

        Metadata curated =
                controller.getCuratedMetadataModel(response, new GAVCPackagesCurationMetadataWrapper(metadata))
                        .getMetadata();

        // Verify that blocked version 3.0.0 is removed
        assertFalse(curated.getVersioning().getVersions().contains("3.0.0"));
        // Verify that version tag is updated to the last remaining version (2.0.0)
        assertEquals("2.0.0", curated.getVersion());
        // Verify that versions list still contains the non-blocked versions
        assertTrue(curated.getVersioning().getVersions().contains("1.0.0"));
        assertTrue(curated.getVersioning().getVersions().contains("2.0.0"));
    }

    @Test
    void testGetCuratedMetadataModelWithVersionTagButNoLatestOrReleaseWhenVersionTagNotBlocked()
            throws PackageCurationException {
        // Test case where latest and release are blank but version tag is present
        // When the version tag is not blocked, it should remain unchanged
        Metadata metadata = new Metadata();
        metadata.setGroupId("com.example");
        metadata.setArtifactId("demo");
        metadata.setVersion("2.0.0");
        Versioning versioning = new Versioning();
        versioning.setVersions(List.of("1.0.0", "2.0.0", "3.0.0"));
        // latest and release are not set (blank)
        metadata.setVersioning(versioning);

        // Block version 3.0.0 (not the version tag)
        CurationResponse response =
                new CurationResponse(List.of(new CurationPackageForResponse("com.example:demo", List.of("3.0.0"))));

        Metadata curated =
                controller.getCuratedMetadataModel(response, new GAVCPackagesCurationMetadataWrapper(metadata))
                        .getMetadata();

        // Verify that blocked version 3.0.0 is removed
        assertFalse(curated.getVersioning().getVersions().contains("3.0.0"));
        // Verify that version tag remains as 2.0.0 (the last remaining version)
        assertEquals("2.0.0", curated.getVersion());
        // Verify that versions list still contains the non-blocked versions
        assertTrue(curated.getVersioning().getVersions().contains("1.0.0"));
        assertTrue(curated.getVersioning().getVersions().contains("2.0.0"));
    }
}