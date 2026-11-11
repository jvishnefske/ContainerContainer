package org.artifactory.ph.exception;

import com.jfrog.ph.acl.exception.PackageForbiddenException;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author Shahafg
 */
class PackageForbiddenExceptionTest {

    @Test
    void forbiddenExceptionContent() {
        PackageForbiddenException forbiddenException = new PackageForbiddenException();
        Assertions.assertEquals(HttpStatus.SC_FORBIDDEN, forbiddenException.getStatus());
        Assertions.assertEquals("Forbidden", forbiddenException.getInternalReason());
        Assertions.assertEquals("Forbidden", forbiddenException.getInternalReason());
    }

    @Test
    void forbiddenExceptionWithMessageContent() {
        PackageForbiddenException forbiddenException =
                new PackageForbiddenException("Public reason", "Internal reason");
        Assertions.assertEquals(HttpStatus.SC_FORBIDDEN, forbiddenException.getStatus());
        Assertions.assertEquals("Public reason", forbiddenException.getPublicReason());
        Assertions.assertEquals("Internal reason", forbiddenException.getInternalReason());
    }

    @Test
    void forbiddenExceptionWithHeadersContent() {
        PackageForbiddenException forbiddenException = new PackageForbiddenException("Public reason", "Internal reason",
                Map.of("Header1", "Value1"));
        Assertions.assertEquals(HttpStatus.SC_FORBIDDEN, forbiddenException.getStatus());
        Assertions.assertEquals("Public reason", forbiddenException.getPublicReason());
        Assertions.assertEquals("Internal reason", forbiddenException.getInternalReason());
        Assertions.assertEquals("Value1", forbiddenException.getAdditionalHeaders().get("Header1"));
    }
}