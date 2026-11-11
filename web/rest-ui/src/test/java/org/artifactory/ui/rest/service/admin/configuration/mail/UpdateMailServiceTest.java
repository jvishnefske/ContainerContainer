package org.artifactory.ui.rest.service.admin.configuration.mail;

import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.configuration.mail.MailServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateMailServiceTest extends ArtifactoryHomeBoundTest {

    private UpdateMailService updateMailService;
    @Mock
    private ArtifactoryRestRequest artifactoryRequest;
    @Mock
    private RestResponse artifactoryResponse;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private MutableCentralConfigDescriptor configDescriptor;
    @Mock
    private ArtifactoryContext artifactoryContext;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        updateMailService = spy(new UpdateMailService(centralConfigService));
        when(artifactoryContext.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(centralConfigService.getMutableDescriptor()).thenReturn(configDescriptor);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }


    @ParameterizedTest
    @ValueSource(strings = {"password", ""})
    @NullSource
    void testUpdateMailServerConfiguration(String password) {
        MailServer mailServer = new MailServer();
        mailServer.setPassword(password);
        mailServer.setTestReceipt("receipt");
        mailServer.setEnabled(true);
        mailServer.setHost("host");
        mailServer.setPort(443);
        when(artifactoryRequest.getImodel()).thenReturn(mailServer);
        doNothing().when(centralConfigService).saveEditedDescriptorAndReload(any(), any());
        updateMailService.execute(artifactoryRequest, artifactoryResponse);
        verify(configDescriptor).setMailServer(mailServer);
        doAnswer(invocationOnMock -> {
            MailServer mailServer1 = invocationOnMock.getArgument(1);
            Assertions.assertEquals(password, mailServer1.getPassword());
            return null;
        }).when(centralConfigService).saveEditedDescriptorAndReload(configDescriptor, mailServer);
        verify(artifactoryResponse).info("Successfully updated Mail server settings");
    }

    @Test
    void testUpdateMailServerConfigurationWithInvalidUrl() {
        MailServer mailServer = mock(MailServer.class);
        when(mailServer.getArtifactoryUrl()).thenReturn("invalidUrl");
        when(artifactoryRequest.getImodel()).thenReturn(mailServer);
        assertThrows(BadRequestException.class,
                () -> updateMailService.execute(artifactoryRequest, artifactoryResponse));
        verify(centralConfigService, never()).saveEditedDescriptorAndReload(any(), any());
        verify(artifactoryResponse, never()).info(anyString());
    }
}