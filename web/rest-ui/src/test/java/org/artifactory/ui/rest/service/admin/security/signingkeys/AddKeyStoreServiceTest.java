package org.artifactory.ui.rest.service.admin.security.signingkeys;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.ui.rest.model.security.KeyStoreFileUpload;
import org.artifactory.ui.rest.service.admin.security.signingkeys.keystore.AddKeyStoreService;
import org.glassfish.jersey.media.multipart.BodyPartEntity;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.message.internal.MessageBodyFactory;
import org.jfrog.storage.StringInputStream;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.MessageBodyReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

/**
 * @author Omri Ziv
 */
@Test
public class AddKeyStoreServiceTest {
    @Mock
    private ArtifactoryRestRequest request;
    @Mock
    private ArtifactoryRestResponse response;
    @Mock
    private ArtifactoryApplicationContext context;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private MutableCentralConfigDescriptor centralConfigDescriptor;
    @Mock
    private MessageBodyFactory messageBodyFactory;
    @Mock
    private MessageBodyReader<InputStream> messageBodyReader;
    private AddKeyStoreService service;
    private FormDataMultiPart formDataMultiPart;
    private File home;
    private AutoCloseable autoCloseable;


    @BeforeMethod
    public void setup() throws IOException {
        autoCloseable = MockitoAnnotations.openMocks(this);
        service = new AddKeyStoreService();
        ReflectionTestUtils.setField(service, "centralConfigService", centralConfigService);
        formDataMultiPart = new FormDataMultiPart();
        formDataMultiPart.field("password", "password1");
        BodyPartEntity bodyPartEntity = mock(BodyPartEntity.class);
        when(bodyPartEntity.getInputStream())
                .thenReturn(new StringInputStream("this is a key"));
        formDataMultiPart.field("file", bodyPartEntity, MediaType.APPLICATION_OCTET_STREAM_TYPE);
        formDataMultiPart.getField("file").setMessageBodyWorkers(messageBodyFactory);
        formDataMultiPart.getField("file").contentDisposition(FormDataContentDisposition.name("name")
                .fileName("a.jks").build());
        formDataMultiPart.getHeaders().add(HttpHeaders.CONTENT_LENGTH, "3000");
        home = Files.createTempDir();
        ArtifactoryHome artifactoryHome = new ArtifactoryHome(home.getAbsolutePath());
        when(context.getArtifactoryHome()).thenReturn(artifactoryHome);
        when(centralConfigService.getMutableDescriptor())
                .thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getFileUploadMaxSizeMb())
                .thenReturn(100);
        when(messageBodyFactory.getMessageBodyReader(eq(InputStream.class),
                any(Type.class), any(), any(MediaType.class)))
                .thenReturn(messageBodyReader);
        when(messageBodyReader.readFrom(any(), any(), any(), any(), any(), any()))
                .thenReturn(new StringInputStream("this is a key"));

        ArtifactoryHome.bind(artifactoryHome);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    public void teardown() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        FileUtils.deleteQuietly(home);
        autoCloseable.close();
    }

    public void testAddKeyStore() {
        when(request.getImodel()).thenReturn(new KeyStoreFileUpload(formDataMultiPart));
        service.execute(request, response);
        String uploadDir = ContextHelper.get().getArtifactoryHome().getTempUploadDir().getAbsolutePath();
        assertTrue(new File(uploadDir, "a.jks").exists());
    }

}
