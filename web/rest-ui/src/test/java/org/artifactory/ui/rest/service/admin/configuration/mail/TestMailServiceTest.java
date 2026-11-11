package org.artifactory.ui.rest.service.admin.configuration.mail;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.mail.MailServerConfiguration;
import org.artifactory.api.mail.MailService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.configuration.mail.MailServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TestMailServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    MailService mailService;
    AutoCloseable closeable;
    TestMailService<?> testMailService;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        testMailService = spy(new TestMailService<>());
        testMailService.setMailService(mailService);
        ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
        AolService aolService = mock(AolService.class);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        when(aolService.isAol()).thenReturn(false);

        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void cleanup() throws Exception {
        closeable.close();
        ArtifactoryContextThreadBinder.unbind();
        verifyNoMoreInteractions(mailService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", ""})
    @NullSource
    void testMailWithRegularPassword(String password) {
        MailServer requestMailServer = new MailServer();
        requestMailServer.setPassword(password);
        requestMailServer.setTestReceipt("receipt");
        requestMailServer.setEnabled(true);
        requestMailServer.setHost("host");
        requestMailServer.setPort(443);
        ArtifactoryRestRequest artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        when(artifactoryRestRequest.getImodel()).thenReturn(requestMailServer);
        RestResponse artifactoryRestResponse = new ArtifactoryRestResponse();
        doAnswer(invocationOnMock -> {
            String[] recipients = invocationOnMock.getArgument(0);
            MailServerConfiguration mailServerConfiguration = invocationOnMock.getArgument(3);
            Assertions.assertEquals("receipt", recipients[0]);
            Assertions.assertEquals(password, mailServerConfiguration.getPassword());
            return null;
        }).when(mailService).sendMail(any(), any(), any(), any());
        testMailService.execute(artifactoryRestRequest, artifactoryRestResponse);
        verify(mailService, times(1)).sendMail(any(), any(), any(), any());
    }
}