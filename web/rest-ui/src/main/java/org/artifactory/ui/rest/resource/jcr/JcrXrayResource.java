package org.artifactory.ui.rest.resource.jcr;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.http.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Base64;

/**
 * @author dudim
 */
@Path("jcr/xray")
@RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class JcrXrayResource {
    private static final Logger log = LoggerFactory.getLogger(JcrXrayResource.class);

    @Autowired
    private JcrXrayService jcrXrayService;

    @GET
    @Path("enabled")
    public Response enabled() {
        boolean enabled = ConstantValues.jcrXrayDemoEnabled.getBoolean();
        return Response.ok().entity(enabled).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("paginatedIssues")
    public Response paginatedIssues(String body, @QueryParam("direction") String direction,
            @QueryParam("num_of_rows") String num_of_rows, @QueryParam("order_by") String order_by,
            @QueryParam("page_num") String page_num) throws IOException {
        assertDemoMode();
        String url = getXrayUrl() + "/ui/component/details/paginatedIssues?direction=" + direction + "&num_of_rows=" +
                num_of_rows + "&order_by=" + order_by + "&page_num=" + page_num + "";
        return getResponse(body, url);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("issueDetails")
    public Response issueDetails(String body) throws IOException {
        assertDemoMode();
        String url = getXrayUrl() + "/ui/component/issueDetails";
        return getResponse(body, url);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("impactPath")
    public Response impactPath(String body) throws IOException {
        assertDemoMode();
        String url = getXrayUrl() + "/ui/impactPath?type=security";
        return getResponse(body, url);
    }

    private String getXrayUrl() {
        String xrayUrl = ConstantValues.jcrXrayUrl.getString();
        if (StringUtils.isBlank(xrayUrl)) {
            String message = "JCR Xray is not enabled, Xray URL is missing.";
            log.warn(message);
            throw new BadRequestException(message);
        }
        return xrayUrl;
    }

    private void assertDemoMode() {
        if (!ConstantValues.jcrXrayDemoEnabled.getBoolean()) {
            throw new BadRequestException("JCR Xray is not enabled");
        }
    }

    private Response getResponse(String body, String url) throws IOException {
        String creds = ConstantValues.jcrXrayCreds.getString();
        if (StringUtils.isBlank(creds)) {
            String message = "JCR Xray is not enabled, Xray credentials are missing.";
            log.warn(message);
            throw new BadRequestException(message);
        }

        HttpPost httpPost = new HttpPost(url);
        String encoding = Base64.getEncoder().encodeToString(creds.getBytes());
        httpPost.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
        httpPost.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
        httpPost.setEntity(new StringEntity(body));

        CloseableHttpResponse response = jcrXrayService.getClient().execute(httpPost);
        return Response.status(response.getStatusLine().getStatusCode()).entity(response.getEntity().getContent())
                .build();
    }
}

