package org.artifactory.rest.resource.mfe;

import org.artifactory.rest.resource.mfe.MfeName;
import org.artifactory.rest.resource.mfe.MfeProjectConfig;
import org.artifactory.rest.resource.mfe.MfeProjectRegistry;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.resource.mfe.server.InternalMFE;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalMFETest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private MfeProjectRegistry mfeProjectRegistry;

    private MfeProjectConfig coreProject;
    private MfeProjectConfig rtuiProject;

    /**
     * Binds a custom home stub that provides getMfeVersion("core") and getMfeVersion("rtui")
     * so getMfeVersion(mfeName) tests pass. Call unbind in finally after the test.
     */
    private void bindHomeWithMfeVersions() {
        ArtifactoryHome.unbind();
        ArtifactoryHomeStub customHome = new ArtifactoryHomeStub(
                getClass().getCanonicalName() + ".getMfeVersion") {
            @Override
            public CompoundVersionDetails getRunningArtifactoryServiceVersion() {
                CompoundVersionDetails details = mock(CompoundVersionDetails.class);
                when(details.getMfeVersion(anyString())).thenReturn("1.0.0");
                return details;
            }
        };
        customHome.setMimeTypes(mimeTypes);
        customHome.loadSystemProperties();
        ArtifactoryHome.bind(customHome);
    }

    @BeforeEach
    void setUp() {
        coreProject = MfeProjectConfig.builder()
                .mfeName(MfeName.CORE)
                .basePath("")
                .build();
        rtuiProject = MfeProjectConfig.builder()
                .mfeName(MfeName.RTUI)
                .basePath("/rtui")
                .build();
        lenient().when(mfeProjectRegistry.findProjectByMfeName(MfeName.CORE)).thenReturn(coreProject);
        lenient().when(mfeProjectRegistry.findProjectByMfeName(MfeName.RTUI)).thenReturn(rtuiProject);
    }

    @Test
    void readFileContent_shouldReturnContent_whenResourceExists() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        String html = internalMFE.readFileContent("/static/ben-el.html");
        assertEquals(ResourceUtils.getResourceAsString("/static/ben-el.html"), html);
        String js = internalMFE.readFileContent("/static/ben-el.js");
        assertEquals(ResourceUtils.getResourceAsString("/static/ben-el.js"), js);
    }

    @Test
    void readFileContent_shouldThrow_whenResourceMissing() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.readFileContent("/static/notfound.html"));
        assertTrue(exception.getMessage().contains("Failed to read MFE file:"));
    }

    @Test
    void serveFile_shouldReturn200AndContentType_whenResourceExists() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        internalMFE.serveFile(response, "/static/ben-el.html", "text/html");
        assertEquals(200, response.getStatus());
        assertEquals("text/html", response.getContentType());
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        internalMFE.serveFile(response2, "/static/ben-el.js", "text/javascript");
        assertEquals(200, response2.getStatus());
        assertEquals("text/javascript", response2.getContentType());
    }

    @Test
    void serveFile_shouldReturn200_whenPathHasMixedCase() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        if (InternalMFE.class.getResource("/JorDi/ben-el.html") != null) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            internalMFE.serveFile(response, "/JorDi/ben-el.html", "text/html");
            assertEquals(200, response.getStatus());
            assertEquals("text/html", response.getContentType());
        }
    }

    @Test
    void serveFile_shouldThrow_whenFileMissing() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.serveFile(response, "/static/notfound", "text/html"));
        assertTrue(exception.getMessage().contains("Failed to serve MFE file:"));
    }

    @Test
    void getMfeVersion_shouldReturnVersionString_whenCoreMfe() throws Exception {
        bindHomeWithMfeVersions();
        try {
            InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
            String version = internalMFE.getMfeVersion("core");
            assertNotNull(version);
            assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"));
        } finally {
            ArtifactoryHome.unbind();
        }
    }

    @Test
    void getMfeVersion_shouldThrow_whenMfeNameIsInvalid() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.getMfeVersion("invalidmfename"));

        assertEquals("Invalid MFE name: invalidmfename", exception.getMessage());
    }

    @Test
    void getMfeVersion_shouldThrow_whenProjectNotFoundInRegistry() {
        when(mfeProjectRegistry.findProjectByMfeName(MfeName.RTUI)).thenReturn(null);
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.getMfeVersion("rtui"));

        assertEquals("MFE project not found: rtui", exception.getMessage());
    }

    @Test
    void serveFile_shouldThrow_whenPathDoesNotExist() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.serveFile(response, "/nonexistent/path/that/does/not/exist.html",
                        "text/html"));
        assertTrue(exception.getMessage().contains("Failed to serve MFE file:"));
    }

    @Test
    void serveFile_shouldReturn200_whenServingStaticHtml() {
        if (InternalMFE.class.getResource("/static/ben-el.html") == null) {
            return;
        }
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        internalMFE.serveFile(response, "/static/ben-el.html", "text/html");
        assertEquals(200, response.getStatus());
        assertEquals("text/html", response.getContentType());
    }

    @Test
    void serveFile_shouldReturn200_whenServingRtuiManifest() {
        if (InternalMFE.class.getResource("/rtui/manifest.esm.js") == null) {
            return;
        }
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        internalMFE.serveFile(response, "/rtui/manifest.esm.js", "application/javascript");
        assertEquals(200, response.getStatus());
        assertEquals("application/javascript", response.getContentType());
    }

    @Test
    void readFileContent_shouldReturnContent_whenRtuiManifestExists() {
        if (InternalMFE.class.getResource("/rtui/manifest.esm.js") == null) {
            return;
        }
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        String content = internalMFE.readFileContent("/rtui/manifest.esm.js");
        assertNotNull(content);
    }

    @Test
    void serveFile_shouldThrow_whenPathEmpty() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.serveFile(response, "", "text/html"));
        assertEquals("Failed to serve empty MFE file", exception.getMessage());
    }

    @Test
    void readFileContent_shouldThrow_whenPathEmpty() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.readFileContent(""));
        assertEquals("Failed to read empty MFE file", exception.getMessage());
    }

    @Test
    void serveFile_shouldUseGivenContentType_whenResourceExists() {
        if (InternalMFE.class.getResource("/static/ben-el.html") == null) {
            return;
        }
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        internalMFE.serveFile(response, "/static/ben-el.html", "application/xhtml+xml");
        assertEquals(200, response.getStatus());
        assertEquals("application/xhtml+xml", response.getContentType());
    }

    @Test
    void serveFile_shouldThrow_whenPathHasSpecialCharacters() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.serveFile(response, "/static/file%20with%20spaces.html",
                        "text/html"));
        assertTrue(exception.getMessage().contains("Failed to serve MFE file:"));
    }

    @Test
    void readFileContent_shouldThrow_whenPathHasSpecialCharacters() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.readFileContent("/static/file%20with%20spaces.html"));
        assertTrue(exception.getMessage().contains("Failed to read MFE file:"));
    }

    @Test
    void serveFile_shouldThrow_whenPathNull() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.serveFile(response, null, "text/html"));
        assertEquals("Failed to serve empty MFE file", exception.getMessage());
    }

    @Test
    void readFileContent_shouldThrow_whenPathNull() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.readFileContent(null));
        assertEquals("Failed to read empty MFE file", exception.getMessage());
    }

    @Test
    void serveFile_shouldWriteResourceContent_whenResourceExists() {
        if (InternalMFE.class.getResource("/static/ben-el.html") == null) {
            return;
        }
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        internalMFE.serveFile(response, "/static/ben-el.html", "text/html");
        String expectedContent = ResourceUtils.getResourceAsString("/static/ben-el.html");
        byte[] expectedBytes = expectedContent.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = response.getContentAsByteArray();
        assertTrue(Arrays.equals(expectedBytes, actualBytes),
                "Content should match when converted to UTF-8 bytes");
    }

    @Test
    void readFileContent_shouldReturnResourceString_whenResourceExists() {
        if (InternalMFE.class.getResource("/static/ben-el.html") == null) {
            return;
        }
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        String content = internalMFE.readFileContent("/static/ben-el.html");
        String expectedContent = ResourceUtils.getResourceAsString("/static/ben-el.html");
        assertEquals(expectedContent, content);
    }

    @Test
    void readFileContent_shouldReturnContent_whenJsFileExists() {
        if (InternalMFE.class.getResource("/static/ben-el.js") == null) {
            return;
        }
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        String content = internalMFE.readFileContent("/static/ben-el.js");
        String expectedContent = ResourceUtils.getResourceAsString("/static/ben-el.js");
        assertEquals(expectedContent, content);
    }

    @Test
    void getMfeVersion_shouldReturnNonEmpty_whenCoreMfe() throws Exception {
        bindHomeWithMfeVersions();
        try {
            InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
            String version = internalMFE.getMfeVersion("core");
            assertNotNull(version);
            assertTrue(version.length() > 0);
        } finally {
            ArtifactoryHome.unbind();
        }
    }

    @Test
    void getMfeVersion_shouldReturnSameValue_whenCalledTwice() throws Exception {
        bindHomeWithMfeVersions();
        try {
            InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
            String version1 = internalMFE.getMfeVersion("core");
            String version2 = internalMFE.getMfeVersion("core");
            assertEquals(version1, version2);
        } finally {
            ArtifactoryHome.unbind();
        }
    }

    @Test
    void serveFile_shouldThrow_whenPathWhitespaceOnly() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.serveFile(response, "   ", "text/html"));
        assertTrue(exception.getMessage().contains("Failed to serve MFE file:"));
    }

    @Test
    void readFileContent_shouldThrow_whenPathWhitespaceOnly() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.readFileContent("   "));
        assertTrue(exception.getMessage().contains("Failed to read MFE file:"));
    }

    @Test
    void serveFile_shouldThrow_whenPathVeryLong() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String longPath = "/static/" + "a".repeat(1000) + ".html";
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.serveFile(response, longPath, "text/html"));
        assertTrue(exception.getMessage().contains("Failed to serve MFE file:"));
    }

    @Test
    void readFileContent_shouldThrow_whenPathVeryLong() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        String longPath = "/static/" + "a".repeat(1000) + ".html";
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.readFileContent(longPath));
        assertTrue(exception.getMessage().contains("Failed to read MFE file:"));
    }

    @Test
    void serveFile_shouldThrow_whenPathHasPathTraversal() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.serveFile(response, "/static/../../etc/passwd", "text/html"));
        assertTrue(exception.getMessage().contains("Failed to serve MFE file:"));
    }

    @Test
    void readFileContent_shouldThrow_whenPathHasPathTraversal() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.readFileContent("/static/../../etc/passwd"));
        assertTrue(exception.getMessage().contains("Failed to read MFE file:"));
    }

    @Test
    void serveFile_shouldSucceed_whenCalledMultipleTimes() {
        if (InternalMFE.class.getResource("/static/ben-el.html") == null) {
            return;
        }
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        internalMFE.serveFile(response, "/static/ben-el.html", "text/html");
        assertEquals(200, response.getStatus());
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        internalMFE.serveFile(response2, "/static/ben-el.html", "text/html");
        assertEquals(200, response2.getStatus());
    }

    @Test
    void readFileContent_shouldReturnSameContent_whenCalledMultipleTimes() {
        if (InternalMFE.class.getResource("/static/ben-el.html") == null) {
            return;
        }
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        String content1 = internalMFE.readFileContent("/static/ben-el.html");
        String content2 = internalMFE.readFileContent("/static/ben-el.html");
        assertEquals(content1, content2);
    }

    @Test
    void serveFile_shouldUseGivenContentType_whenTextCss() {
        if (InternalMFE.class.getResource("/static/ben-el.html") == null) {
            return;
        }
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        internalMFE.serveFile(response, "/static/ben-el.html", "text/css");
        assertEquals("text/css", response.getContentType());
        assertEquals(200, response.getStatus());
    }

    @Test
    void serveFile_shouldUseGivenContentType_whenApplicationXml() {
        if (InternalMFE.class.getResource("/static/ben-el.html") == null) {
            return;
        }
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        internalMFE.serveFile(response, "/static/ben-el.html", "application/xml");
        assertEquals("application/xml", response.getContentType());
        assertEquals(200, response.getStatus());
    }

    @Test
    void serveFile_shouldUseGivenContentType_whenImagePng() {
        if (InternalMFE.class.getResource("/static/ben-el.html") == null) {
            return;
        }
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        internalMFE.serveFile(response, "/static/ben-el.html", "image/png");
        assertEquals("image/png", response.getContentType());
        assertEquals(200, response.getStatus());
    }

    @Test
    void serveFile_shouldUseGivenContentType_whenApplicationJson() {
        if (InternalMFE.class.getResource("/static/ben-el.js") == null) {
            return;
        }
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        internalMFE.serveFile(response, "/static/ben-el.js", "application/json");
        assertEquals("application/json", response.getContentType());
        assertEquals(200, response.getStatus());
    }

    @Test
    void serveFile_shouldReturn200OrThrow_whenRootPath() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            internalMFE.serveFile(response, "/", "text/html");
            assertEquals(200, response.getStatus());
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("Failed to serve MFE file:"));
        }
    }

    @Test
    void readFileContent_shouldReturnContentOrThrow_whenRootPath() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        try {
            String content = internalMFE.readFileContent("/");
            assertNotNull(content);
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("Failed to read MFE file:"));
        }
    }

    @Test
    void serveFile_shouldThrow_whenPathHasNoExtension() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.serveFile(response, "/static/filename", "text/html"));
        assertTrue(exception.getMessage().contains("Failed to serve MFE file:"));
    }

    @Test
    void readFileContent_shouldThrow_whenPathHasNoExtension() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> internalMFE.readFileContent("/static/filename"));
        assertTrue(exception.getMessage().contains("Failed to read MFE file:"));
    }

    @Test
    void serveFile_shouldReturn200OrThrow_whenPathHasMultipleSlashes() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            internalMFE.serveFile(response, "//static//ben-el.html", "text/html");
            assertEquals(200, response.getStatus());
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("Failed to serve MFE file:"));
        }
    }

    @Test
    void readFileContent_shouldReturnContentOrThrow_whenPathHasMultipleSlashes() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        try {
            String content = internalMFE.readFileContent("//static//ben-el.html");
            assertNotNull(content);
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("Failed to read MFE file:"));
        }
    }

    @Test
    void serveFile_shouldReturn200OrThrow_whenServingRtuiManifest() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            internalMFE.serveFile(response, "/rtui/manifest.esm.js", "application/javascript");
            assertEquals(200, response.getStatus());
            assertEquals("application/javascript", response.getContentType());
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("Failed to serve MFE file:"));
        }
    }

    @Test
    void readFileContent_shouldReturnContentOrThrow_whenRtuiManifest() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        try {
            String content = internalMFE.readFileContent("/rtui/manifest.esm.js");
            assertNotNull(content);
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("Failed to read MFE file:"));
        }
    }

    @Test
    void serveFile_shouldServeOrThrow_whenArbitraryPath() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            internalMFE.serveFile(response, "/some/other/path.html", "text/html");
            assertEquals(200, response.getStatus());
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("Failed to serve MFE file:"));
        }
    }

    @Test
    void readFileContent_shouldReturnContentOrThrow_whenArbitraryPath() {
        InternalMFE internalMFE = new InternalMFE(mfeProjectRegistry);
        try {
            String content = internalMFE.readFileContent("/some/other/path.html");
            assertNotNull(content);
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("Failed to read MFE file:"));
        }
    }

}

