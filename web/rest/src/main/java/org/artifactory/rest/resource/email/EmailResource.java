package org.artifactory.rest.resource.email;

import jakarta.annotation.security.RolesAllowed;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.mail.MailService;
import org.artifactory.api.rest.mail.MailMessageRequest;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.util.EmailException;
import org.jfrog.access.client.model.MessageModel;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.artifactory.api.rest.constant.SystemRestConstants.PATH_SEND_MAIL;
import static org.artifactory.common.ConstantValues.securityAllowAdminSendMail;
import static org.artifactory.security.permissionscope.PermissionScopeToken.SEND_MAIL_EXECUTE;
import static org.artifactory.security.utils.AuthenticationUtils.isAccessServiceTokenAuthentication;


@Path(PATH_SEND_MAIL)
@Component
@AllArgsConstructor
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
public class EmailResource {
    private static final String EMAIL_FAILURE_MESSAGE = "Failed to send email";
    private final MailService mailService;
    private final AuthorizationService authorizationService;

    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @POST
    public Response sendMail(MailMessageRequest mailMsg) {
        if (!isAuthorizedToSendMail()) {
            return buildUnauthorizedResponse();
        }
        try {
            sendEmail(mailMsg);
        } catch (EmailException ex) {
            return buildErrorResponse(ex);
        }
        return buildSuccessResponse();
    }

    private boolean isAuthorizedToSendMail() {
        if (securityAllowAdminSendMail.getBoolean()) {
            log.debug("Email sending authorized for admin.");
            return true;
        }
        if (isAccessServiceTokenAuthentication()) {
            log.debug("Email sending authorized via access service token.");
            return true;
        }
        if (authorizationService.isGrantedByAccessToken(SEND_MAIL_EXECUTE)) {
            log.debug("Email sending authorized via access token.");
            return true;
        }
        return false;
    }

    private void sendEmail(MailMessageRequest mailMsg) throws EmailException {
        log.debug("Sending email to '{}'.", mailMsg.getRecipient());
        mailService.sendMail(new String[] {mailMsg.getRecipient()}, mailMsg.getSubject(), mailMsg.getBody());
    }

    private Response buildUnauthorizedResponse() {
        log.warn("Unauthorized access to send email.");
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    private Response buildErrorResponse(EmailException ex) {
        log.warn("Failed to send email.", ex);
        MessageModel msg =
                new MessageModel(Response.Status.BAD_REQUEST.toString(), EMAIL_FAILURE_MESSAGE, ex.getMessage());
        return Response.status(Response.Status.BAD_REQUEST).entity(msg).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    private Response buildSuccessResponse() {
        log.debug("Email sent successfully.");
        MessageModel msg = new MessageModel(Response.Status.OK.toString(), "", "");
        return Response.ok().entity(msg).type(MediaType.APPLICATION_JSON_TYPE).build();
    }
}