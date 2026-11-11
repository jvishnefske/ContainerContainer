package org.artifactory.logging.version;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.XmlConverterUtils;
import org.artifactory.version.converter.XmlConverter;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LoggingVersionTest {

    @Test
    void test7Conversion() throws IOException {
        File tempDir = Files.createTempDir();
        tempDir.deleteOnExit();
        File logbackFile = new File(tempDir, ArtifactoryHome.LOGBACK_CONFIG_FILE_NAME);
        File logbackResource =
                ResourceUtils.getResourceAsFile("/org/artifactory/logging/version/v20/fresh_700_logback.xml");
        Files.copy(logbackResource, logbackFile);
        LoggingVersion.convert(ArtifactoryServiceVersion.valueOf("7.0.0-m001"), tempDir, tempDir);

        List<XmlConverter> converters = Arrays.stream(LoggingVersion.values())
                .map(LoggingVersion::getXmlConverters)
                .flatMap(r -> Arrays.stream(r.clone()))
                .collect(Collectors.toList());

        String expectedResult = XmlConverterUtils.convert(converters, FileUtils
                .readFileToString(logbackResource, StandardCharsets.UTF_8));
        assertEquals(expectedResult, Files.asCharSource(logbackFile, Charset.defaultCharset()).read());
    }

    /**
     * When converting from 6, V700PreInitReplaceLogbackConverter will delete the logback.xml.
     * We expect no exception to be thrown if logback.xml doesn't exists, as he will be created as default later on.
     * {@see RTFACT-23885}
     */
    @Test
    void testConversionFromBefore6() {
        File tempDir = Files.createTempDir();
        tempDir.deleteOnExit();
        assertDoesNotThrow(
                () -> LoggingVersion.convert(ArtifactoryServiceVersion.valueOf("7.0.0-m001"), tempDir, tempDir));
    }

}