package org.artifactory.rest.resource.upload;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link InternalUploadResource}.
 *
 * <p>The resource is a thin pass-through: it has no business logic and no validation. Auth is
 * enforced by the standard upload pipeline (deploy permission), identical to the regular deploy
 * API. The tests below verify only that:
 *
 * <ul>
 *   <li>arguments reach the service unchanged, including nested file paths produced by the
 *       JAX-RS {@code .+} path matcher, and</li>
 *   <li>a successful service call returns {@code 201 Created}.</li>
 * </ul>
 *
 * @author Fawaz Atwa
 */
class InternalUploadResourceTest {

    private InternalUploadServiceImpl uploadService;
    private InternalUploadResource resource;

    @BeforeEach
    void setUp() {
        uploadService = mock(InternalUploadServiceImpl.class);
        resource = new InternalUploadResource(uploadService);
    }

    @Test
    void upload_delegatesToServiceAndReturns201() throws Exception {
        InputStream content = new ByteArrayInputStream(new byte[]{1, 2, 3});

        Response response = resource.upload("my-remote", ".evidence/foo.evidence", content);

        verify(uploadService).upload("my-remote", ".evidence/foo.evidence", content);
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus(),
                "Successful upload must respond with 201 Created");
    }

    @Test
    void upload_passesNestedFilePathToService() throws Exception {
        InputStream content = new ByteArrayInputStream(new byte[]{0});

        resource.upload("remote", ".evidence/sub/dir/foo.json", content);

        verify(uploadService).upload(
                eq("remote"),
                eq(".evidence/sub/dir/foo.json"),
                any(InputStream.class));
    }
}
