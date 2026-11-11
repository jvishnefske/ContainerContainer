package org.artifactory.traffic;

import ch.qos.logback.classic.Level;
import com.jfrog.test.LogTestUtils;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.traffic.entry.RequestEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.stream.Stream;

import static org.artifactory.traffic.RequestLogger.request;
import static org.testng.Assert.assertTrue;

@Slf4j(topic = "org.artifactory.traffic.RequestLogger")
class RequestLoggerTest {
    private static final String USERNAME = "username";
    private static final String USER_ADDRESS = "100.100.100.100";
    private static final String METHOD = "GET";
    private static final String PATH = "/path/to/file";
    private static final int RETURN_CODE = 200;
    private static final long REQ_RES_CONTENT_LENGTH = 100L;
    private static final long DURATION = 100L;
    private PrintStream printStream;
    private FileOutputStream fileOutputStream;
    private File logFile;

    private static Stream<Arguments> getRequestEntity() {
        return Stream.of(
                Arguments.of("Prometheus"),
                Arguments.of("JFrog Observability"),
                Arguments.of("JFrog-Frontend"),
                Arguments.of("JFrog-Router"),
                Arguments.of("JFConnect"),
                Arguments.of("JFLink"),
                Arguments.of("Xray")
        );
    }

    @BeforeEach
    void setup() throws IOException {
        logFile = File.createTempFile("log", ".txt");
        fileOutputStream = new FileOutputStream(logFile.getAbsoluteFile().getPath());
        printStream = new PrintStream(fileOutputStream);
        System.setOut(printStream);
    }

    @AfterEach
    void tearDown() throws Exception {
        System.setOut(System.out);
        if (printStream != null) {
            printStream.close();
        }
        if (fileOutputStream != null) {
            fileOutputStream.close();
        }
    }

    @ParameterizedTest
    @MethodSource("getRequestEntity")
    void testRequestLogInfo(String userAgent) {
        LogTestUtils.setLoggingLevel(log.getName(), Level.INFO);
        request(new RequestEntry(USER_ADDRESS,
                USERNAME,
                METHOD,
                PATH,
                RETURN_CODE,
                REQ_RES_CONTENT_LENGTH,
                REQ_RES_CONTENT_LENGTH,
                DURATION, userAgent));
        assertTrue(logFile.getAbsoluteFile().length() > 0);
    }
}