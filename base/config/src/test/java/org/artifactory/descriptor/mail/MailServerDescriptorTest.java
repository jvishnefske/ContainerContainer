package org.artifactory.descriptor.mail;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.ws.rs.BadRequestException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Hussein Alshqerat
 */

class MailServerDescriptorTest {

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "></a><script>alert(\"I got you\")</script>"})
    void testUpdateWithIllegalArtifactoryUrl(String url) {
        MailServerDescriptor mailServerDescriptor = new MailServerDescriptor();
        assertThrows(BadRequestException.class,
                () -> mailServerDescriptor.setArtifactoryUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "http://localhost"})
    void testUpdateArtifactoryUrl(String url) {
        MailServerDescriptor mailServerDescriptor = new MailServerDescriptor();
        mailServerDescriptor.setArtifactoryUrl(url);
        assertEquals(url, mailServerDescriptor.getArtifactoryUrl());
    }
}