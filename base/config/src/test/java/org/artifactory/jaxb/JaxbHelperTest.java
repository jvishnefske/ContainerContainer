package org.artifactory.jaxb;

import org.artifactory.exception.DocumentBuildException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Hussein Alshqerat
 */
class JaxbHelperTest {

    @Test
    void shouldReadExternalConfigSuccessfully() throws IOException {
        String config = Files.readString(
                Path.of(
                        JaxbHelperTest.class.getResource("/config/test/validConfig.xml").getPath()
                )
        );
        Assertions.assertNotNull(JaxbHelper.readExternalConfig(config));
    }

    @Test
    void shouldFailReadExternalConfigWithDoctype() throws IOException {
        String config = Files.readString(
                Path.of(
                        JaxbHelperTest.class.getResource("/config/test/invalidXxe.xml").getPath()
                )
        );
        Assertions.assertThrows(DocumentBuildException.class, () -> {
            JaxbHelper.readExternalConfig(config);
        }, "Error on line 2: DOCTYPE is disallowed when the feature " +
                "\\\"http://apache.org/xml/features/disallow-doctype-decl\\\" set to true.");
    }
}