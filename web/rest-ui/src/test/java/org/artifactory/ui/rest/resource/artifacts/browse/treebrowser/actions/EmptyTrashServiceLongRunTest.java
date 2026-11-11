package org.artifactory.ui.rest.resource.artifacts.browse.treebrowser.actions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import lombok.val;
import org.apache.http.HttpStatus;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.ServiceExecutor;
import org.artifactory.rest.common.service.trash.EmptyTrashService;
import org.artifactory.ui.rest.service.artifacts.browse.BrowseServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Igor Usenko
 */
class EmptyTrashServiceLongRunTest {

    @BeforeEach
    void setUp() {
        val context = mock(ArtifactoryContext.class);
        val tracer = mock(ArtifactoryTracer.class);
        when(context.beanForType(ArtifactoryTracer.class)).thenReturn(tracer);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void whenRequestFitsInTime() {
        val resource = createResourceWithDelay(0);

        val response = resource.emptyTrash();

        assertEquals(202, response.getStatus());
        assertTrue(response.getEntity().toString().contains("Successfully deleted all trashcan items"));
    }

    @Test
    void whenRequestDoesNotFitInTime() {
        val resource = createResourceWithDelay(1000);

        val response = resource.emptyTrash();

        assertEquals(202, response.getStatus());
        assertTrue(response.getEntity().toString().contains("Empty trash operation in progress"));
    }

    private ArtifactActionsResourceForTest createResourceWithDelay(long sleep) {
        val service = new EmptyTrashServiceForTest();
        service.sleep = sleep;
        val factory = mock(BrowseServiceFactory.class);
        when(factory.emptyTrashService()).thenReturn(service);

        val servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        val uriInfo = mock(UriInfo.class);
        when(uriInfo.getQueryParameters()).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getPathParameters()).thenReturn(mock(MultivaluedMap.class));

        val resource = new ArtifactActionsResourceForTest();
        ReflectionTestUtils.setField(resource, "uriInfo", uriInfo);
        resource.setRequest(servletRequest);
        resource.setBrowseFactory(factory);
        resource.setArtifactoryResponse(new ArtifactoryRestResponse());
        resource.setExecutor();

        return resource;
    }

    static class EmptyTrashServiceForTest extends EmptyTrashService {

        private long sleep = 0;

        @Override
        public void execute(ArtifactoryRestRequest request, RestResponse response) {
            try {
                Thread.sleep(sleep);
                response.info("Successfully deleted all trashcan items");
                response.responseCode(HttpStatus.SC_ACCEPTED);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class ArtifactActionsResourceForTest extends ArtifactActionsResource {

        public void setRequest(HttpServletRequest servletRequest) {
            this.servletRequest = servletRequest;
        }

        public void setExecutor() {
            this.serviceExecutor = new ServiceExecutor();
        }

        @Override
        protected long getTimeout() {
            return 500L;
        }
    }
}