package org.artifactory.rest.resource.system;

import jakarta.ws.rs.core.Response;
import org.artifactory.api.mail.MailService;
import org.artifactory.api.rest.mail.MailMessageRequest;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.resource.email.EmailResource;
import org.artifactory.security.utils.AuthenticationUtils;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.EmailException;
import org.jfrog.access.client.model.MessageModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletResponse;

import static org.artifactory.common.ConstantValues.securityAllowAdminSendMail;
import static org.artifactory.security.permissionscope.PermissionScopeToken.SEND_MAIL_EXECUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class MailResourceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private MailService mailService;
    @Mock
    private AuthorizationService authorizationService;
    private EmailResource mailResource;
    private MockedStatic<AuthenticationUtils> authenticationUtils;
    private AutoCloseable mocks;

    @BeforeEach
    public void beforeMethod() {
        mocks = openMocks(this);
        mailResource = new EmailResource(mailService, authorizationService);
        this.authenticationUtils = mockStatic(AuthenticationUtils.class);
    }

    @Test
    void testSendMailWithAccessServiceTokenAuthentication() {
        authenticationUtils.when(AuthenticationUtils::isAccessServiceTokenAuthentication).thenReturn(true);
        doNothing().when(mailService).sendMail(any(), eq("subject"), any());
        MailMessageRequest mailReq = new MailMessageRequest("a@a.com", "subject", "body");
        assertEquals(HttpServletResponse.SC_OK, mailResource.sendMail(mailReq).getStatus());
        assertFalse(authorizationService.isGrantedByAccessToken(SEND_MAIL_EXECUTE));
        assertFalse(securityAllowAdminSendMail.getBoolean());
        verify(mailService).sendMail(new String[] {"a@a.com"}, "subject", "body");
    }

    @Test
    void testSendMailWithSendMailExecuteToken() {
        when(authorizationService.isGrantedByAccessToken(SEND_MAIL_EXECUTE)).thenReturn(true);
        doNothing().when(mailService).sendMail(any(), eq("subject"), any());
        MailMessageRequest mailReq = new MailMessageRequest("a@a.com", "subject", "body");
        assertEquals(HttpServletResponse.SC_OK, mailResource.sendMail(mailReq).getStatus());
        assertFalse(AuthenticationUtils.isAccessServiceTokenAuthentication());
        assertFalse(securityAllowAdminSendMail.getBoolean());
        verify(mailService).sendMail(new String[] {"a@a.com"}, "subject", "body");
    }

    @Test
    void testSendMailWithSystemProperty() {
        getOrCreateArtifactoryHomeStub().getArtifactoryProperties()
                .setProperty(securityAllowAdminSendMail.getPropertyName(), "true");
        doNothing().when(mailService).sendMail(any(), eq("subject"), any());
        MailMessageRequest mailReq = new MailMessageRequest("a@a.com", "subject", "body");
        assertEquals(HttpServletResponse.SC_OK, mailResource.sendMail(mailReq).getStatus());
        assertFalse(AuthenticationUtils.isAccessServiceTokenAuthentication());
        assertFalse(authorizationService.isGrantedByAccessToken(SEND_MAIL_EXECUTE));
        verify(mailService).sendMail(new String[] {"a@a.com"}, "subject", "body");
    }

    @Test
    void testSendMailFailure() {
        authenticationUtils.when(AuthenticationUtils::isAccessServiceTokenAuthentication).thenReturn(true);
        doThrow(new EmailException("err")).when(mailService).sendMail(any(), eq("1subject1"), any());
        MailMessageRequest mailReq = new MailMessageRequest("a@a.com", "1subject1", "body");
        Response response = mailResource.sendMail(mailReq);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        assertEquals("Failed to send email", ((MessageModel) response.getEntity()).getMessage());
        verify(mailService).sendMail(new String[] {"a@a.com"}, "1subject1", "body");
    }

    @Test
    void testSendMailUnauthorized() {
        authenticationUtils.when(AuthenticationUtils::isAccessServiceTokenAuthentication).thenReturn(false);
        when(authorizationService.isGrantedByAccessToken(SEND_MAIL_EXECUTE)).thenReturn(false);
        getOrCreateArtifactoryHomeStub().getArtifactoryProperties()
                .setProperty(securityAllowAdminSendMail.getPropertyName(), "false");
        MailMessageRequest mailReq = new MailMessageRequest("a@a.com", "subject", "body");
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, mailResource.sendMail(mailReq).getStatus());
        verify(mailService, never()).sendMail(any(), any(), any());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        authenticationUtils.close();
    }
}