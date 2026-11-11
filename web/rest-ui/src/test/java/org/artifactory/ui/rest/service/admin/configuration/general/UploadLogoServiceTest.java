package org.artifactory.ui.rest.service.admin.configuration.general;

import lombok.NonNull;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.utils.FileUpload;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.common.ConstantValues.uiLogoMaxSizeBytes;
import static org.jfrog.common.ResourceUtils.getResourceAsFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadLogoServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private final UploadLogoService service = new UploadLogoService();
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private CentralConfigService centralConfigService;
    private ArtifactoryHome artifactoryHome;

    @BeforeEach
    public void setUp() {
        artifactoryHome = getBound();
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        service.centralConfigService = centralConfigService;
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void fileIsTooBig() throws Exception {
        when(artifactoryContext.getArtifactoryHome()).thenReturn(artifactoryHome);
        artifactoryHome.getArtifactoryProperties().setProperty(uiLogoMaxSizeBytes.getPropertyName(), "29198");

        File rceFile = getResourceAsFile("/uiresource.malicious/jfrog.png");
        byte[] fileBytes = FileUtils.readFileToByteArray(rceFile);
        ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
        setMockForReadFile(fileBytes, request);
        try (var files = mockStatic(Files.class); var ioUtils = mockStatic(IOUtils.class)) {
            ioUtils.when(() -> IOUtils.toByteArray(any(InputStream.class))).thenReturn(fileBytes);
            files.when(() -> Files.createTempFile("imageio", ".tmp")).thenReturn(Paths.get("tmp/"));
            BasicFileAttributes attributes = mock(BasicFileAttributes.class);
            files.when(() -> Files.readAttributes(any(), eq(BasicFileAttributes.class))).thenReturn(attributes);

            ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>() {
            };
            service.execute(request, response);
            String entity = (String) response.getEntity();
            assertTrue(entity.contains("Logo file size must not exceed"));
            assertEquals(SC_BAD_REQUEST, response.getResponseCode());
        }
    }

    @Test
    void isARealImage() throws Exception {
        when(artifactoryContext.getArtifactoryHome()).thenReturn(artifactoryHome);
        artifactoryHome.getArtifactoryProperties().setProperty(uiLogoMaxSizeBytes.getPropertyName(), "29199");

        File rceFile = getResourceAsFile("/uiresource.malicious/jfrog.png");
        byte[] fileBytes = FileUtils.readFileToByteArray(rceFile);
        ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
        setMockForReadFile(fileBytes, request);
        try (var files = mockStatic(Files.class); var ioUtils = mockStatic(IOUtils.class)) {
            ioUtils.when(() -> IOUtils.toByteArray(any(InputStream.class))).thenReturn(fileBytes);
            files.when(() -> Files.createTempFile("imageio", ".tmp")).thenReturn(Paths.get("tmp/"));
            BasicFileAttributes attributes = mock(BasicFileAttributes.class);
            files.when(() -> Files.readAttributes(any(), eq(BasicFileAttributes.class))).thenReturn(attributes);

            ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>() {
            };
            service.execute(request, response);
            assertEquals(SC_OK, response.getResponseCode());
        }
    }

    @Test
    void isNonImageFile() throws Exception {
        when(artifactoryContext.getArtifactoryHome()).thenReturn(artifactoryHome);

        File rceFile = getResourceAsFile("/browse/treebrowser/test.zip");
        byte[] fileBytes = FileUtils.readFileToByteArray(rceFile);
        ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
        setMockForReadFile(fileBytes, request);

        try (var files = mockStatic(Files.class); var ioUtils = mockStatic(IOUtils.class)) {
            ioUtils.when(() -> IOUtils.toByteArray(any(InputStream.class))).thenReturn(fileBytes);
            files.when(() -> Files.createTempFile("imageio", ".tmp")).thenReturn(Paths.get("tmp/"));
            BasicFileAttributes attributes = mock(BasicFileAttributes.class);
            files.when(() -> Files.readAttributes(any(), eq(BasicFileAttributes.class))).thenReturn(attributes);

            ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>() {
            };
            service.execute(request, response);
            String entity = (String) response.getEntity();
            assertTrue(entity.contains("Uploaded logo is not an image file"));
            assertEquals(SC_BAD_REQUEST, response.getResponseCode());
        }
    }

    @Test
    void imageContainsEmbeddedRCEInJpgFile() throws Exception {
        when(artifactoryContext.getArtifactoryHome()).thenReturn(artifactoryHome);

        File rceFile = getResourceAsFile("/uiresource.malicious/POC_phpinfo-metadata.jpeg");
        byte[] fileBytes = FileUtils.readFileToByteArray(rceFile);
        ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
        setMockForReadFile(fileBytes, request);

        try (var files = mockStatic(Files.class); var ioUtils = mockStatic(IOUtils.class)) {
            ioUtils.when(() -> IOUtils.toByteArray(any(InputStream.class))).thenReturn(fileBytes);
            files.when(() -> Files.createTempFile("imageio", ".tmp")).thenReturn(Paths.get("tmp/"));
            BasicFileAttributes attributes = mock(BasicFileAttributes.class);
            files.when(() -> Files.readAttributes(any(), eq(BasicFileAttributes.class))).thenReturn(attributes);

            ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>() {
            };
            service.execute(request, response);
            String entity = (String) response.getEntity();
            assertTrue(entity.contains("Image contains potentially malicious code, check logs for details"));
            assertEquals(SC_BAD_REQUEST, response.getResponseCode());
        }
    }

    @Test
    void testIsExifMalicious() throws Exception {
        var imageBytes = service.getImageBytes(getResourceAsFile("/uiresource.malicious/POC_phpinfo-metadata.jpeg"));
        assertThrows(ImageReadException.class, () -> service.isExifMalicious(imageBytes, new File("bla")));
    }

    private void setMockForReadFile(byte[] fileBytes, @NonNull ArtifactoryRestRequest request) {
        FileUpload fileUpload = mock(FileUpload.class);
        when(request.getImodel()).thenReturn(fileUpload);
        FormDataMultiPart formDataMultiPart = mock(FormDataMultiPart.class);
        when(fileUpload.getFormDataMultiPart()).thenReturn(formDataMultiPart);
        FormDataBodyPart formDataBodyPart = mock(FormDataBodyPart.class);
        when(formDataBodyPart.getValueAs(byte[].class)).thenReturn(fileBytes);
        Map<String, List<FormDataBodyPart>> fields = Map.of("file", List.of(formDataBodyPart));
        when(formDataMultiPart.getFields()).thenReturn(fields);

        MutableCentralConfigDescriptor descriptor = mock(MutableCentralConfigDescriptor.class);
        when(centralConfigService.getMutableDescriptor()).thenReturn(descriptor);
        when(descriptor.getFileUploadMaxSizeMb()).thenReturn(100);
    }
}
