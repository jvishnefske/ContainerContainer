package org.artifactory.ui.rest.service.admin.configuration.mail;

import org.artifactory.addon.AddonsManager;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.admin.configuration.mail.MailServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GetMailServiceTest {

    @Mock
    CentralConfigService centralConfigService;
    GetMailService getMailService;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        getMailService = spy(new GetMailService());
        getMailService.setCentralConfigService(centralConfigService);
        ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
        AddonsManager addonsManagerMock = mock(AddonsManager.class);
        AolService aolService = mock(AolService.class);

        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManagerMock);

        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        when(aolService.isAol()).thenReturn(false);

        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        verifyNoMoreInteractions(centralConfigService);
        mocks.close();
    }

    @Test
    void testGetMailServer() {
        ArtifactoryRestRequest<?> artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader(any())).thenReturn("http://localhost:8081");
        when(artifactoryRestRequest.getServletRequest()).thenReturn(servletRequest);
        MailServer mailServer = mock(MailServer.class);
        when(mailServer.getPassword()).thenReturn("password");
        MutableCentralConfigDescriptor mutableCentralConfigDescriptor = mock(MutableCentralConfigDescriptor.class);
        when(mutableCentralConfigDescriptor.getMailServer()).thenReturn(mailServer);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableCentralConfigDescriptor);
        RestResponse<MailServer> artifactoryRestResponse = new ArtifactoryRestResponse<>();
        getMailService.execute(artifactoryRestRequest, artifactoryRestResponse);
        MailServer result = artifactoryRestResponse.getIModel();
        assertNull(result.getPassword());
        verify(centralConfigService, times(1)).getMutableDescriptor();
    }

}