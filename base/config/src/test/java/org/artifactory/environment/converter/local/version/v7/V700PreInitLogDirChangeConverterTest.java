package org.artifactory.environment.converter.local.version.v7;

import org.apache.commons.io.FileUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * @author Nadav Yogev
 */
public class V700PreInitLogDirChangeConverterTest {

    private static final String OLD_CONTENT = "old-content";

    @Test
    public void testConvert() throws IOException {
        File tempFile = File.createTempFile("logback", "tmp");
        FileUtils.write(tempFile, OLD_CONTENT, Charset.defaultCharset(), false);
        tempFile.deleteOnExit();
        ArtifactoryHome home = mock(ArtifactoryHome.class);
        when(home.getLogbackConfig()).thenReturn(tempFile);
        new V700PreInitReplaceLogbackConverter().convert(home, null, null);
        File backupFile = Arrays.stream(tempFile.getParentFile().listFiles())
                .filter(r -> r.getName().startsWith("logback.xml.date."))
                .findAny()
                .orElse(null);
        assertNotNull(backupFile);
        backupFile.deleteOnExit();
        assertEquals(FileUtils.readFileToString(backupFile, Charset.defaultCharset()), OLD_CONTENT);

    }

}