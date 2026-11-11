package org.artifactory.rest.resource.mfe;

import com.github.zafarkhaja.semver.Version;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.repo.http.IdleConnectionMonitorService;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.resource.mfe.server.ExternalMFE;
import org.artifactory.schedule.TaskService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.client.http.CloseableObserver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.artifactory.common.ConstantValues.coreMfeExternalOverrideExternalVersion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class ExternalMFETest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    MeterRegistry meterRegistryMock;

    @Mock
    MfeProjectRegistry mfeProjectRegistryMock;

    @Test
    void getMfeVersionWithValidCoreMfe() throws Exception {
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .build();
        when(mfeProjectRegistryMock.findProjectByMfeName(MfeName.CORE)).thenReturn(coreProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        setField(externalMFE, "client", mockHttpClient());
        // Initialize projectVersions map
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        
        String version = externalMFE.getMfeVersion("core");
        assertNotNull(version);
        Assertions.assertDoesNotThrow(() -> Version.valueOf(version));
    }

    @Test
    void getMfeVersionWithValidRtuiMfe() throws Exception {
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .build();
        when(mfeProjectRegistryMock.findProjectByMfeName(MfeName.RTUI)).thenReturn(rtuiProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        setField(externalMFE, "client", mockHttpClient());
        // Initialize projectVersions map
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.RTUI, "1.2.3");
        
        String version = externalMFE.getMfeVersion("rtui");
        assertNotNull(version);
        Assertions.assertDoesNotThrow(() -> Version.valueOf(version));
    }


    @Test
    void getVersionFromManifest() throws Exception {
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        String jsonResponse = """
                {
                    "default": "1.2.3"
                }
                """;

        InputStream stream = IOUtils.toInputStream(jsonResponse, StandardCharsets.UTF_8);
        HttpEntity getEntity = mock(HttpEntity.class);
        when(getEntity.getContent()).thenReturn(stream);
        when(getResponse.getEntity()).thenReturn(getEntity);
        String expected = "1.2.3";
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .build();
        lenient().when(mfeProjectRegistryMock.findProjectForResource("")).thenReturn(coreProject);
        assertEquals(expected, externalMFE.getVersionFromVersionsManifest("1.2.1", coreProject));
    }

    @Test
    void getVersionFromManifestWithExceptionFromClient() throws Exception {
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        when(client.execute(any())).thenThrow(new IOException());
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .build();
        lenient().when(mfeProjectRegistryMock.findProjectForResource("")).thenReturn(coreProject);
        assertNull(externalMFE.getVersionFromVersionsManifest("1.2.1", coreProject));
    }

    @Test
    void readFileContent() throws Exception {
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("some/path")).thenReturn(coreProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        
        // Set up version in projectVersions map
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        String someContent = "some content";

        InputStream stream = IOUtils.toInputStream(someContent, StandardCharsets.UTF_8);
        HttpEntity getEntity = mock(HttpEntity.class);
        when(getEntity.getContent()).thenReturn(stream);
        when(getResponse.getEntity()).thenReturn(getEntity);
        assertEquals(someContent, externalMFE.readFileContent("some/path"));
    }

    @Test
    void readFileContentClientThrowsException() throws Exception {
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("some/path")).thenReturn(coreProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        
        // Set up version in projectVersions map
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        
        when(client.execute(any())).thenThrow(new IOException());
        assertThrows(NotFoundException.class, () -> externalMFE.readFileContent("some/path"));
    }

    @Test
    void readFileContentClientReturns404() throws Exception {
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("some/path")).thenReturn(coreProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        setField(externalMFE, "client", mockHttpClient());
        
        // Set up version in projectVersions map
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        
        NotFoundException exception = assertThrows(NotFoundException.class, () ->
                externalMFE.readFileContent("some/path"));

        assertTrue(exception.getMessage().contains("Failed to serve MFE file:"));
    }

    @Test
    void serveFileTest() throws Exception {
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("/some/path")).thenReturn(coreProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        
        // Set up version in projectVersions map
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        String someContent = "some content";

        InputStream stream = IOUtils.toInputStream(someContent, StandardCharsets.UTF_8);
        HttpEntity getEntity = mock(HttpEntity.class);
        when(getEntity.getContent()).thenReturn(stream);
        when(getResponse.getEntity()).thenReturn(getEntity);

        String resourcePath = "/some/path";
        String contentType = "text/plain";

        externalMFE.serveFile(httpResponse, resourcePath, contentType);

        assertEquals(contentType, httpResponse.getContentType());
        assertEquals(200, httpResponse.getStatus());
        assertEquals(someContent, httpResponse.getContentAsString());
    }

    @Test
    void serveFileTestWith404() throws Exception {
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("/some/path")).thenReturn(coreProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        setField(externalMFE, "client", mockHttpClient());
        
        // Set up version in projectVersions map
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");

        String resourcePath = "/some/path";
        String contentType = "text/plain";
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        NotFoundException exception = assertThrows(NotFoundException.class, () ->
                externalMFE.serveFile(httpResponse, resourcePath, contentType));

        assertTrue(exception.getMessage().contains("Failed to serve MFE file:"));
    }

    @Test
    void serveFileTestClientThrowsException() throws Exception {
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("/some/path")).thenReturn(coreProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        
        // Set up version in projectVersions map
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        when(client.execute(any())).thenThrow(new IOException());

        String resourcePath = "/some/path";
        String contentType = "text/plain";

        NotFoundException exception = assertThrows(NotFoundException.class, () ->
                externalMFE.serveFile(httpResponse, resourcePath, contentType));

        assertTrue(exception.getMessage().contains("Failed to serve MFE file:"));
    }

    @Test
    void testInit() throws Exception {
        // Set override version so init doesn't fail
        setStringSystemProperty(coreMfeExternalOverrideExternalVersion, "1.2.3");
        
        TaskService taskService = mock(TaskService.class);
        when(taskService.startTask(any(), anyBoolean())).thenReturn("taskId");
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .metricName("core.mfe.version")
                .build();
        when(mfeProjectRegistryMock.getAllProjects()).thenReturn(java.util.Arrays.asList(coreProject));
        ExternalMFE externalMFE = new ExternalMFE(taskService, meterRegistryMock, mfeProjectRegistryMock);
        setField(externalMFE, "client", mockHttpClient());
        externalMFE.init();
        // Verify that projectVersions map is populated
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        assertNotNull(projectVersions);
        assertEquals("1.2.3", projectVersions.get(MfeName.CORE));
    }

    @Test
    void testOverrideVersion() throws Exception {
        setStringSystemProperty(coreMfeExternalOverrideExternalVersion, "1.17.3");
        
        TaskService taskService = mock(TaskService.class);
        when(taskService.startTask(any(), anyBoolean())).thenReturn("taskId");
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .metricName("core.mfe.version")
                .build();
        when(mfeProjectRegistryMock.getAllProjects()).thenReturn(java.util.Arrays.asList(coreProject));
        ExternalMFE externalMFE = new ExternalMFE(taskService, meterRegistryMock, mfeProjectRegistryMock);
        setField(externalMFE, "client", mockHttpClient());
        externalMFE.init();
        // Check that projectVersions contains the override version
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        assertEquals("1.17.3", projectVersions.get(MfeName.CORE));
    }

    @Test
    void testMetrics() throws Exception {
        // Set override version to control the version used
        setStringSystemProperty(coreMfeExternalOverrideExternalVersion, "1.2.3");
        
        MeterRegistry registry = new SimpleMeterRegistry();
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .metricName("core.mfe.version")
                .build();
        ExternalMFE externalMFE = new ExternalMFE(null, registry, mfeProjectRegistryMock);
        // No need to set up HTTP client since override version is set and no HTTP calls will be made
        // Call refresh which will use the override version
        callMethod(externalMFE, "refreshMFEVersionForProject", new Class<?>[]{MfeProjectConfig.class}, coreProject);
        Gauge metric = registry.find("core.mfe.version").gauge();
        assertNotNull(metric);
        String versionValue = metric.getId().getTag("version");
        assertEquals("1.2.3", versionValue);
        
        // Update override version and refresh again
        setStringSystemProperty(coreMfeExternalOverrideExternalVersion, "1.2.4");
        callMethod(externalMFE, "refreshMFEVersionForProject", new Class<?>[]{MfeProjectConfig.class}, coreProject);
        metric = registry.find("core.mfe.version").gauge();
        assertNotNull(metric);
        versionValue = metric.getId().getTag("version");
        assertEquals("1.2.4", versionValue);
    }

    @Test
    void refreshMFEVersionForProjects_shouldRefreshAllProjects() throws Exception {
        // Set override versions to control the versions used
        setStringSystemProperty(coreMfeExternalOverrideExternalVersion, "1.2.3");
        setStringSystemProperty(ConstantValues.coreMfeExternalOverrideExternalVersion, "1.2.3");
        
        MeterRegistry registry = new SimpleMeterRegistry();
        
        // Setup mock projects
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .metricName("core.mfe.version")
                .build();
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .metricName("rtui.mfe.version")
                .build();
        
        when(mfeProjectRegistryMock.getAllProjects()).thenReturn(java.util.Arrays.asList(coreProject, rtuiProject));
        
        ExternalMFE externalMFE = new ExternalMFE(null, registry, mfeProjectRegistryMock);
        // No need to set up HTTP client since override versions are set and no HTTP calls will be made
        
        // Act
        callMethod(externalMFE, "refreshMFEVersionForProjects", new Class<?>[0]);
        
        // Assert - both projects should have metrics registered
        Gauge coreMetric = registry.find("core.mfe.version").gauge();
        assertNotNull(coreMetric);
        assertEquals("1.2.3", coreMetric.getId().getTag("version"));
        
        Gauge rtuiMetric = registry.find("rtui.mfe.version").gauge();
        assertNotNull(rtuiMetric);
        assertEquals("1.2.3", rtuiMetric.getId().getTag("version"));
    }

    @Test
    void getVersionFromManifestWith500StatusCode() throws Exception {
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(500);
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .build();
        lenient().when(mfeProjectRegistryMock.findProjectForResource("")).thenReturn(coreProject);
        assertNull(externalMFE.getVersionFromVersionsManifest("1.2.1", coreProject));
    }

    @Test
    void getVersionFromManifestWith403StatusCode() throws Exception {
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(403);
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .build();
        lenient().when(mfeProjectRegistryMock.findProjectForResource("")).thenReturn(coreProject);
        assertNull(externalMFE.getVersionFromVersionsManifest("1.2.1", coreProject));
    }

    @Test
    void getVersionFromManifestWithInvalidJson() throws Exception {
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        String invalidJson = "not a valid json";
        InputStream stream = IOUtils.toInputStream(invalidJson, StandardCharsets.UTF_8);
        HttpEntity getEntity = mock(HttpEntity.class);
        when(getEntity.getContent()).thenReturn(stream);
        when(getResponse.getEntity()).thenReturn(getEntity);
        // Invalid JSON causes JsonParseException which is caught and returns null
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .build();
        lenient().when(mfeProjectRegistryMock.findProjectForResource("")).thenReturn(coreProject);
        assertNull(externalMFE.getVersionFromVersionsManifest("1.2.1", coreProject));
    }

    @Test
    void getVersionFromManifestWithMissingDefaultField() throws Exception {
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        String jsonResponse = """
                {
                    "other": "value"
                }
                """;
        InputStream stream = IOUtils.toInputStream(jsonResponse, StandardCharsets.UTF_8);
        HttpEntity getEntity = mock(HttpEntity.class);
        when(getEntity.getContent()).thenReturn(stream);
        when(getResponse.getEntity()).thenReturn(getEntity);
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .build();
        lenient().when(mfeProjectRegistryMock.findProjectForResource("")).thenReturn(coreProject);
        String result = externalMFE.getVersionFromVersionsManifest("1.2.1", coreProject);
        assertTrue(result == null || result.isEmpty());
    }

    @Test
    void getMfeVersionReturnsCurrentVersion() throws Exception {
        // Set override version to control the version used
        setStringSystemProperty(coreMfeExternalOverrideExternalVersion, "1.2.3");
        
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .metricName("core.mfe.version")
                .build();
        when(mfeProjectRegistryMock.findProjectByMfeName(MfeName.CORE)).thenReturn(coreProject);
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        // No need to set up HTTP client since override version is set and no HTTP calls will be made
        callMethod(externalMFE, "refreshMFEVersionForProject", new Class<?>[]{MfeProjectConfig.class}, coreProject);
        assertEquals("1.2.3", externalMFE.getMfeVersion("core"));
    }

    @Test
    void    refreshMFEVersionForProjectWhenVersionDoesNotChange() throws Exception {
        // Set override version to control the version used
        setStringSystemProperty(coreMfeExternalOverrideExternalVersion, "1.2.3");
        
        MeterRegistry registry = new SimpleMeterRegistry();
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .metricName("core.mfe.version")
                .build();
        ExternalMFE externalMFE = new ExternalMFE(null, registry, mfeProjectRegistryMock);
        // No need to set up HTTP client since override version is set and no HTTP calls will be made
        callMethod(externalMFE, "refreshMFEVersionForProject", new Class<?>[]{MfeProjectConfig.class}, coreProject);
        Gauge firstMetric = registry.find("core.mfe.version").gauge();
        assertNotNull(firstMetric);
        // Refresh again with same version
        callMethod(externalMFE, "refreshMFEVersionForProject", new Class<?>[]{MfeProjectConfig.class}, coreProject);
        Gauge secondMetric = registry.find("core.mfe.version").gauge();
        assertNotNull(secondMetric);
        // Should still be the same version
        assertEquals("1.2.3", secondMetric.getId().getTag("version"));
    }

    @Test
    void serveFileWithProjectSpecificBaseUrl() throws Exception {
        // Set the rtuiMfeExternalPathBase property
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.coreMfeExternalPathBase.getPropertyName(),
                "https://rtui-cdn.com/v1/artifactory-rtui-mfe");
        
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("/rtui/manifest.esm.js")).thenReturn(rtuiProject);
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        projectVersions.put(MfeName.RTUI, "1.2.3");
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        String someContent = "rtui content";
        InputStream stream = IOUtils.toInputStream(someContent, StandardCharsets.UTF_8);
        HttpEntity getEntity = mock(HttpEntity.class);
        when(getEntity.getContent()).thenReturn(stream);
        when(getResponse.getEntity()).thenReturn(getEntity);
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        // Test with rtui project path
        externalMFE.serveFile(httpResponse, "/rtui/manifest.esm.js", "application/javascript");
        assertEquals("application/javascript", httpResponse.getContentType());
        assertEquals(200, httpResponse.getStatus());
        assertEquals(someContent, httpResponse.getContentAsString());
    }

    @Test
    void readFileContentWithProjectSpecificBaseUrl() throws Exception {
        // Set the rtuiMfeExternalPathBase property
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.coreMfeExternalPathBase.getPropertyName(),
                "https://rtui-cdn.com/v1/artifactory-rtui-mfe");
        
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("/rtui/manifest.esm.js")).thenReturn(rtuiProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        projectVersions.put(MfeName.RTUI, "1.2.3");
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        String someContent = "rtui file content";
        InputStream stream = IOUtils.toInputStream(someContent, StandardCharsets.UTF_8);
        HttpEntity getEntity = mock(HttpEntity.class);
        when(getEntity.getContent()).thenReturn(stream);
        when(getResponse.getEntity()).thenReturn(getEntity);
        // Test with rtui project path
        String content = externalMFE.readFileContent("/rtui/manifest.esm.js");
        assertEquals(someContent, content);
    }

    @Test
    void serveFileWithRootProjectPath() throws Exception {
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("/manifest.js")).thenReturn(coreProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        projectVersions.put(MfeName.RTUI, "1.2.3");
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        String someContent = "root content";
        InputStream stream = IOUtils.toInputStream(someContent, StandardCharsets.UTF_8);
        HttpEntity getEntity = mock(HttpEntity.class);
        when(getEntity.getContent()).thenReturn(stream);
        when(getResponse.getEntity()).thenReturn(getEntity);
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        // Test with root project path
        externalMFE.serveFile(httpResponse, "/manifest.js", "application/javascript");
        assertEquals("application/javascript", httpResponse.getContentType());
        assertEquals(200, httpResponse.getStatus());
        assertEquals(someContent, httpResponse.getContentAsString());
    }

    @Test
    void readFileContentWithRootProjectPath() throws Exception {
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("/manifest.js")).thenReturn(coreProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        projectVersions.put(MfeName.RTUI, "1.2.3");
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        String someContent = "root file content";
        InputStream stream = IOUtils.toInputStream(someContent, StandardCharsets.UTF_8);
        HttpEntity getEntity = mock(HttpEntity.class);
        when(getEntity.getContent()).thenReturn(stream);
        when(getResponse.getEntity()).thenReturn(getEntity);
        // Test with root project path
        String content = externalMFE.readFileContent("/manifest.js");
        assertEquals(someContent, content);
    }

    @Test
    void testOverrideVersionWithBlankString() throws Exception {
        // When override is blank, ExternalMFE fetches version from manifest using classpath MFE version
        // to build the manifest URL. Bind a home stub that provides getMfeVersion("core") so the fetch runs.
        ArtifactoryHome.unbind();
        ArtifactoryHomeStub customHome = new ArtifactoryHomeStub(
                getClass().getCanonicalName() + ".testOverrideVersionWithBlankString") {
            @Override
            public CompoundVersionDetails getRunningArtifactoryServiceVersion() {
                CompoundVersionDetails details = mock(CompoundVersionDetails.class);
                when(details.getMfeVersion(anyString())).thenReturn("1.2.3");
                return details;
            }
        };
        customHome.setMimeTypes(mimeTypes);
        customHome.loadSystemProperties();
        customHome.setProperty(coreMfeExternalOverrideExternalVersion.getPropertyName(), "   ");
        ArtifactoryHome.bind(customHome);
        try {
            TaskService taskService = mock(TaskService.class);
            when(taskService.startTask(any(), anyBoolean())).thenReturn("taskId");
            MfeProjectConfig coreProject = MfeProjectConfig.builder()
                    .mfeName(MfeName.CORE)
                    .basePath("")
                    .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                    .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                    .metricName("core.mfe.version")
                    .build();
            when(mfeProjectRegistryMock.getAllProjects()).thenReturn(java.util.Arrays.asList(coreProject));
            ExternalMFE externalMFE = new ExternalMFE(taskService, meterRegistryMock, mfeProjectRegistryMock);
            CloseableHttpClient client = mock(CloseableHttpClient.class);
            setField(externalMFE, "client", client);
            CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
            lenient().when(client.execute(any())).thenReturn(getResponse);
            StatusLine statusLine = mock(StatusLine.class);
            lenient().when(getResponse.getStatusLine()).thenReturn(statusLine);
            lenient().when(statusLine.getStatusCode()).thenReturn(200);
            String jsonResponse = """
                    {
                        "default": "1.2.3"
                    }
                    """;
            HttpEntity getEntity = mock(HttpEntity.class);
            lenient().when(getEntity.getContent()).thenAnswer(invocation ->
                    IOUtils.toInputStream(jsonResponse, StandardCharsets.UTF_8));
            lenient().when(getResponse.getEntity()).thenReturn(getEntity);
            externalMFE.init();
            java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
            assertNotNull(projectVersions.get(MfeName.CORE));
            assertEquals("1.2.3", projectVersions.get(MfeName.CORE));
        } finally {
            ArtifactoryHome.unbind();
        }
    }

    @Test
    void testInitWithExistingClient() throws Exception {
        TaskService taskService = mock(TaskService.class);
        when(taskService.startTask(any(), anyBoolean())).thenReturn("taskId");
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                .metricName("core.mfe.version")
                .build();
        lenient().when(mfeProjectRegistryMock.getAllProjects()).thenReturn(java.util.Arrays.asList(coreProject));
        lenient().when(mfeProjectRegistryMock.findProjectForResource("")).thenReturn(coreProject);
        lenient().when(mfeProjectRegistryMock.findProjectForResource("/")).thenReturn(coreProject);
        lenient().when(mfeProjectRegistryMock.findProjectByMfeName(MfeName.CORE)).thenReturn(coreProject);
        ExternalMFE externalMFE = new ExternalMFE(taskService, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient existingClient = mock(CloseableHttpClient.class);
        // Mock the HTTP response for the version manifest request during init
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        lenient().when(existingClient.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        lenient().when(getResponse.getStatusLine()).thenReturn(statusLine);
        lenient().when(statusLine.getStatusCode()).thenReturn(404);
        setField(externalMFE, "client", existingClient);
        externalMFE.init();
        // Client should remain the same
        CloseableHttpClient actualClient = getField(externalMFE, "client");
        assertEquals(existingClient, actualClient);
    }

    @Test
    void serveFileWithIOExceptionOnCopy() throws Exception {
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("/some/path")).thenReturn(coreProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        projectVersions.put(MfeName.RTUI, "1.2.3");
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        HttpEntity getEntity = mock(HttpEntity.class);
        when(getEntity.getContent()).thenThrow(new IOException("Stream error"));
        when(getResponse.getEntity()).thenReturn(getEntity);
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        NotFoundException exception = assertThrows(NotFoundException.class, () ->
                externalMFE.serveFile(httpResponse, "/some/path", "text/plain"));
        assertTrue(exception.getMessage().contains("Failed to serve MFE file:"));
    }

    @Test
    void readFileContentWithIOExceptionOnRead() throws Exception {
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("/some/path")).thenReturn(coreProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        projectVersions.put(MfeName.RTUI, "1.2.3");
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        HttpEntity getEntity = mock(HttpEntity.class);
        when(getEntity.getContent()).thenThrow(new IOException("Read error"));
        when(getResponse.getEntity()).thenReturn(getEntity);
        NotFoundException exception = assertThrows(NotFoundException.class, () ->
                externalMFE.readFileContent("/some/path"));
        assertTrue(exception.getMessage().contains("Failed to serve MFE file:"));
    }

    @Test
    void testInitWithNullClient() throws Exception {
        // Set up ArtifactoryContext required by HttpClientConfigurator
        ArtifactoryContext contextMock = mock(InternalArtifactoryContext.class);
        // IdleConnectionMonitorService must implement CloseableObserver interface
        MockSettings monitorServiceSettings = withSettings().extraInterfaces(CloseableObserver.class);
        IdleConnectionMonitorService idleConnectionMonitorService = 
                mock(IdleConnectionMonitorService.class, monitorServiceSettings);
        DenyListCacheHolderService denyListCacheHolderService = mock(DenyListCacheHolderService.class);
        when(contextMock.beanForType(IdleConnectionMonitorService.class)).thenReturn(idleConnectionMonitorService);
        when(contextMock.beanForType(DenyListCacheHolderService.class)).thenReturn(denyListCacheHolderService);
        ArtifactoryContextThreadBinder.bind(contextMock);
        
        try {
            TaskService taskService = mock(TaskService.class);
            when(taskService.startTask(any(), anyBoolean())).thenReturn("taskId");
            ExternalMFE externalMFE = new ExternalMFE(taskService, meterRegistryMock, mfeProjectRegistryMock);
            // Ensure client is null
            setField(externalMFE, "client", null);
            externalMFE.init();
            // Client should be initialized (not null)
            CloseableHttpClient client = getField(externalMFE, "client");
            assertNotNull(client);
            // Verify the client can execute requests (it's a real HttpClientConfigurator instance)
            // We can't easily verify the exact configuration, but we can verify it's not null
            // Note: The HTTP request during init() may fail, but that's okay - it will fall back to classpath version
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @ParameterizedTest
    @CsvSource({
            // Root project path - uses core path base
            "/manifest.js, https://default-cdn.com/v1/artifactory-mfe/1.2/1.2.3",
            // RTUI project path - same path base constant, so same URL base
            "/rtui/manifest.esm.js, https://default-cdn.com/v1/artifactory-mfe/1.2/1.2.3"
    })
    void testGetBaseUrlForResourceWithProjectSpecificUrl(String resourcePath, String expectedUrl) throws Exception {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.coreMfeExternalPathBase.getPropertyName(),
                "https://default-cdn.com/v1/artifactory-mfe");

        // Setup mock projects (both use coreMfeExternalPathBase)
        MfeProjectConfig coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        lenient().when(mfeProjectRegistryMock.findProjectForResource("/manifest.js")).thenReturn(coreProject);
        lenient().when(mfeProjectRegistryMock.findProjectForResource("/rtui/manifest.esm.js")).thenReturn(rtuiProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        projectVersions.put(MfeName.RTUI, "1.2.3");
        
        // Use reflection to access the private method
        java.lang.reflect.Method method = ExternalMFE.class.getDeclaredMethod("getBaseUrlForResource",
                String.class);
        method.setAccessible(true);
        
        String actualUrl = (String) method.invoke(externalMFE, resourcePath);
        assertEquals(expectedUrl, actualUrl);
    }


    @Test
    void serveFileWithProjectSpecificExternalBaseUrl() throws Exception {
        // Set the rtuiMfeExternalPathBase property
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.coreMfeExternalPathBase.getPropertyName(),
                "https://rtui-cdn.com/v1/artifactory-rtui-mfe");
        
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("/rtui/manifest.esm.js")).thenReturn(rtuiProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        projectVersions.put(MfeName.RTUI, "1.2.3");
        
        // Mock HTTP response
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        String someContent = "test content from RTUI CDN";
        InputStream stream = IOUtils.toInputStream(someContent, StandardCharsets.UTF_8);
        HttpEntity getEntity = mock(HttpEntity.class);
        when(getEntity.getContent()).thenReturn(stream);
        when(getResponse.getEntity()).thenReturn(getEntity);
        
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        externalMFE.serveFile(httpResponse, "/rtui/manifest.esm.js",
                "application/javascript");
        
        // Verify it used the RTUI-specific baseUrl
        assertEquals(200, httpResponse.getStatus());
        assertEquals(someContent, httpResponse.getContentAsString());
        // Verify the request was made to the RTUI URL
        org.mockito.ArgumentCaptor<HttpGet> requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpGet.class);
        verify(client).execute(requestCaptor.capture());
        assertTrue(requestCaptor.getValue().getURI().toString().startsWith("https://rtui-cdn.com"));
    }

    @Test
    void readFileContentWithProjectSpecificExternalBaseUrl() throws Exception {
        // Set the rtuiMfeExternalPathBase property
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.coreMfeExternalPathBase.getPropertyName(),
                "https://rtui-cdn.com/v1/artifactory-rtui-mfe");
        
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("/rtui/manifest.esm.js")).thenReturn(rtuiProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        setField(externalMFE, "client", client);
        java.util.Map<MfeName, String> projectVersions = getField(externalMFE, "projectVersions");
        projectVersions.put(MfeName.CORE, "1.2.3");
        projectVersions.put(MfeName.RTUI, "1.2.3");
        
        // Mock HTTP response
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        when(getResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        String someContent = "test content from RTUI CDN";
        InputStream stream = IOUtils.toInputStream(someContent, StandardCharsets.UTF_8);
        HttpEntity getEntity = mock(HttpEntity.class);
        when(getEntity.getContent()).thenReturn(stream);
        when(getResponse.getEntity()).thenReturn(getEntity);
        
        // Test with rtui project path (will use rtuiMfeExternalPathBase)
        String content = externalMFE.readFileContent("/rtui/manifest.esm.js");
        assertEquals(someContent, content);
        
        // Verify the request was made to the RTUI URL
        org.mockito.ArgumentCaptor<HttpGet> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(HttpGet.class);
        verify(client).execute(requestCaptor.capture());
        assertTrue(requestCaptor.getValue().getURI().toString().startsWith("https://rtui-cdn.com"));
    }

    @Test
    void readFileContent_shouldThrowNotFoundException_whenProjectHasNoVersion() throws Exception {
        MfeProjectConfig rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("/rtui/js/manifest.esm.js")).thenReturn(rtuiProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        
        // Don't set up version in projectVersions map - simulating a project not in production yet
        
        NotFoundException exception = assertThrows(NotFoundException.class, () ->
                externalMFE.readFileContent("/rtui/js/manifest.esm.js"));
        
        assertTrue(exception.getMessage().contains("MFE version not available"));
        assertTrue(exception.getMessage().contains("rtui"));
        assertTrue(exception.getMessage().contains("not be in production yet"));
    }

    @Test
    void serveFile_shouldThrowNotFoundException_whenProjectHasNoVersion() throws Exception {
        MfeProjectConfig packagesProject = MfeProjectConfig.builder()
                .mfeName(MfeName.PACKAGES)
                .basePath("/packages")
                .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                .build();
        when(mfeProjectRegistryMock.findProjectForResource("/packages/js/app.js")).thenReturn(packagesProject);
        
        ExternalMFE externalMFE = new ExternalMFE(null, meterRegistryMock, mfeProjectRegistryMock);
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        
        // Don't set up version in projectVersions map - simulating a project not in production yet
        
        NotFoundException exception = assertThrows(NotFoundException.class, () ->
                externalMFE.serveFile(httpResponse, "/packages/js/app.js", "application/javascript"));
        
        assertTrue(exception.getMessage().contains("MFE version not available"));
        assertTrue(exception.getMessage().contains("packages"));
        assertTrue(exception.getMessage().contains("not be in production yet"));
    }

    private static CloseableHttpClient mockHttpClient() throws IOException {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse getResponse = mock(CloseableHttpResponse.class);
        lenient().when(client.execute(any())).thenReturn(getResponse);
        StatusLine statusLine = mock(StatusLine.class);
        lenient().when(getResponse.getStatusLine()).thenReturn(statusLine);
        lenient().when(statusLine.getStatusCode()).thenReturn(404);
        return client;
    }

    /**
     * Helper method to set package-private fields using reflection.
     * This is needed because the test is in a different package than ExternalMFE.
     */
    @SuppressWarnings({"java:S3011"})
    private static void setField(ExternalMFE target, String fieldName, Object value) throws Exception {
        Field field = ExternalMFE.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Helper method to get package-private fields using reflection.
     * This is needed because the test is in a different package than ExternalMFE.
     */
    @SuppressWarnings({"java:S3011", "unchecked"})
    private static <T> T getField(ExternalMFE target, String fieldName) throws Exception {
        Field field = ExternalMFE.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    /**
     * Helper method to call package-private methods using reflection.
     * This is needed because the test is in a different package than ExternalMFE.
     */
    @SuppressWarnings({"java:S3011"})
    private static void callMethod(ExternalMFE target, String methodName, Class<?>[] paramTypes, Object... args)
            throws Exception {
        java.lang.reflect.Method method = ExternalMFE.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        method.invoke(target, args);
    }

}

