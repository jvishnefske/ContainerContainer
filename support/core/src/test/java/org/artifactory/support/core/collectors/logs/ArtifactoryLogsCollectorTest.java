package org.artifactory.support.core.collectors.logs;

import org.apache.commons.io.FileUtils;
import org.jfrog.support.common.config.SystemLogsConfiguration;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Eli Skoran 09/12/2019
 */
public class ArtifactoryLogsCollectorTest {

    @Test
    public void testCollectLogs() throws IOException {
        File logsDir = Files.createTempDirectory("_logs").toFile();
        File archivedLogDir = Files.createDirectory(new File(logsDir, "archived").toPath()).toFile();
        File logsOutputDir = Files.createDirectory(new File(logsDir, "log_output").toPath()).toFile();

        createLogs(logsDir, archivedLogDir);
        new ArtifactoryLogsCollector(logsDir).collect(new SystemLogsConfiguration(), logsOutputDir);

        Set<String> logsCollected = Arrays.stream(new File(logsOutputDir, "logs").listFiles()).filter(File::isFile)
                .map(File::getName).collect(
                        Collectors.toSet());
        assertTrue(logsCollected.stream()
                .allMatch(log -> log.startsWith("artifactory-") ||
                        log.startsWith("filebeat")
                ));
        assertEquals(logsCollected.size(), 8);
        Set<String> archivedLogsCollected = Arrays.stream(new File(logsOutputDir, "logs/archived").listFiles())
                .filter(File::isFile).map(File::getName).collect(Collectors.toSet());
        assertFalse(archivedLogsCollected.isEmpty());
        assertEquals(archivedLogsCollected.size(), 2);
        assertTrue(archivedLogsCollected.stream()
                .allMatch(log -> log.startsWith("artifactory")));
    }

    private void createLogs(File logsDir, File archivedLogDir) throws IOException {
        createLogFile(logsDir, "access-audit.log");
        createLogFile(logsDir, "access-request.log");
        createLogFile(logsDir, "access-security-audit.log");
        createLogFile(logsDir, "access-service.log");
        createLogFile(logsDir, "artifactory-service.log");
        createLogFile(logsDir, "artifactory-event.log");
        createLogFile(logsDir, "artifactory-access.log");
        createLogFile(logsDir, "artifactory-import-export.log");
        createLogFile(logsDir, "artifactory-request-trace.log");
        createLogFile(logsDir, "artifactory-request.log");
        createLogFile(logsDir, "derby.log");
        createLogFile(logsDir, "frontend-request.log");
        createLogFile(logsDir, "router-request.log");
        createLogFile(logsDir, "router-traefik.log");
        createLogFile(logsDir, "filebeat.log");
        createLogFile(logsDir, "filebeat.log.1");
        createLogFile(archivedLogDir, "access-service.2019-10-06T09-38-26.714.log.gz");
        createLogFile(archivedLogDir, "artifactory-service.2019-10-06T09-38-26.714.log.gz");
        createLogFile(archivedLogDir, "artifactory-request.2019-10-06T09-38-26.714.log.gz");
        createLogFile(archivedLogDir, "router-request-2019-09-26T13-44-11.411.log.gz");
    }

    private String createLogFile(File logsDir, String fileName) throws IOException {
        File logFile = new File(logsDir, fileName);
        FileUtils.touch(logFile);
        return logFile.getAbsolutePath();
    }
}
