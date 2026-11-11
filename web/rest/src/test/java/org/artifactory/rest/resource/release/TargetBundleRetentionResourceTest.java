package org.artifactory.rest.resource.release;

import com.jfrog.distribution.handler.acl.bridge.DistributionHandlerBridgeToModule;
import org.apache.http.HttpStatus;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;

import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TargetBundleRetentionResourceTest {

    @Mock
    DistributionHandlerBridgeToModule bridgeToModule;

    private AutoCloseable mocks;

    @BeforeMethod
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    void shutDown() throws Exception {
        mocks.close();
    }

    @Test(testName = "Test that bridge to module is called with expected parameter when resource is called")
    public void tetPropagateOperation() {
        // setup
        TargetBundleRetentionResource resource = new TargetBundleRetentionResource(bridgeToModule);

        // invoke
        try(Response response = resource.propagateOperation("model")) {
            assertEquals(response.getStatus(), HttpStatus.SC_OK);
        } finally {
            // verify
            verify(bridgeToModule).onPropagated("model");
        }
    }

    @Test(testName = "Test that propagateOperation is properly annotated")
    public void testPropagateOperationAnnotations() throws NoSuchMethodException {
        Method propagateOperation = TargetBundleRetentionResource.class.getMethod("propagateOperation", String.class);
        assertNotNull(propagateOperation, "propagateOperation(String) method is not found");

        Path path = propagateOperation.getAnnotation(Path.class);
        assertNotNull(path, "Path annotation is not found");
        assertEquals(path.value(), "/target/retention/propagate");

        RolesAllowed rolesAllowed = propagateOperation.getAnnotation(RolesAllowed.class);
        assertNotNull(rolesAllowed, "RolesAllowed annotation is not found");
        assertEquals(rolesAllowed.value(), new String[] {HaRestConstants.ROLE_HA});

        Consumes consumes = propagateOperation.getAnnotation(Consumes.class);
        assertNotNull(consumes, "Consumes annotation is not found");
        assertEquals(consumes.value(), new String[] {MediaType.TEXT_PLAIN});

        Produces produces = propagateOperation.getAnnotation(Produces.class);
        assertNotNull(produces, "Produces annotation is not found");
        assertEquals(produces.value(), new String[] {MediaType.TEXT_PLAIN});

        POST post = propagateOperation.getAnnotation(POST.class);
        assertNotNull(post, "POST annotation is not found");
    }

    @Test
    public void testNonNull() {
        // constructor
        Assert.assertThrows(NullPointerException.class, () -> new TargetBundleRetentionResource(null));

        // methods
        TargetBundleRetentionResource resource = new TargetBundleRetentionResource(bridgeToModule);
        Assert.assertThrows(NullPointerException.class, () -> resource.propagateOperation(null));
    }
}