package org.artifactory.log.logback;

import ch.qos.logback.classic.LoggerContext;
import org.jfrog.common.logging.logback.LogbackContextConfigurator;
import org.mockito.Mockito;
import org.springframework.util.ReflectionUtils;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * @author Andrey Tashlitsky
 */
public class ArtifactoryLoggerConfigTest {

    @Test
    public void testArtifactoryLoggerConfigConfigure() {
        LoggerContext loggerContext = new LoggerContext();
        LogbackContextConfigurator logbackContextConfigurator = LogbackContextConfigurator.configure(loggerContext);
        File logDirMock = Mockito.mock(File.class);
        when(logDirMock.getAbsolutePath()).thenReturn("/some/path/log");
        File dummyFile1 = Mockito.mock(File.class);
        when(logDirMock.getParentFile()).thenReturn(dummyFile1);
        when(dummyFile1.getParentFile()).thenReturn(null);
        File dataDirMock = Mockito.mock(File.class);
        when(dataDirMock.getAbsolutePath()).thenReturn("/some/path/data");
        File logbackConfigFileMock = Mockito.mock(File.class);
        ArtifactoryLoggerConfig artifactoryLoggerConfig =
                new ArtifactoryLoggerConfig("123", logDirMock, dataDirMock, logbackConfigFileMock);
        artifactoryLoggerConfig.configure(logbackContextConfigurator);
        Field f = ReflectionUtils.findField(LogbackContextConfigurator.class, "properties");
        assertNotNull(f);
        ReflectionUtils.makeAccessible(f);
        Map<String, String> properties = (Map<String, String>) ReflectionUtils.getField(f, logbackContextConfigurator);
        assertNotNull(properties);
        assertEquals(properties.get("artifactory.contextId"), "123 ");
        assertEquals(properties.get("log.dir"), "/some/path/log");
        assertEquals(properties.get("log.dir.archived"), "/some/path/log/archived");
        assertEquals(properties.get("artifactory.usage.dir"), "/some/path/data/artifactory/usage");
        assertEquals(properties.get("artifactory.usage.dir.archived"), "/some/path/data/artifactory/usage/archived");
    }
}
