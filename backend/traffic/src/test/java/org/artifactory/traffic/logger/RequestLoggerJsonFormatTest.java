package org.artifactory.traffic.logger;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.logging.filters.ConsoleJsonFormatFilter;
import org.artifactory.logging.layout.request.RequestJsonLayout;
import org.artifactory.traffic.RequestLogger;
import org.artifactory.traffic.entry.RequestEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.artifactory.traffic.RequestLogger.request;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author William Arustamyan
 */


class RequestLoggerJsonFormatTest {

    private static final String USERNAME = "username";
    private static final String USER_ADDRESS = "100.100.100.100";
    private static final String METHOD = "GET";
    private static final String PATH = "/path/to/file";
    private static final int RETURN_CODE = 200;
    private static final long REQ_RES_CONTENT_LENGTH = 100L;
    private static final long DURATION = 100L;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    private AutoCloseable closable;

    @BeforeEach
    void setup() {
        closable = MockitoAnnotations.openMocks(this);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactoryHome.bind(artifactoryHome);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.requestLogsQueryParamsEnabled)).thenReturn(
                false);
        ConsoleJsonFormatFilter.isEnabled = true;
        System.setOut(new PrintStream(out));
    }

    @AfterEach
    void tearDown() throws Exception {
        closable.close();
        ArtifactoryHome.unbind();
        System.setOut(originalOut);
    }


    @Test
    void testRequestLoggerLogOutputWithJsonFormat() {

        Logger log = (Logger) LoggerFactory.getLogger(RequestLogger.class);
        log.setLevel(Level.INFO);
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        consoleAppender.addFilter(new ConsoleJsonFormatFilter());
        consoleAppender.setLayout(new RequestJsonLayout());
        log.addAppender(consoleAppender);
        consoleAppender.setOutputStream(out);
        consoleAppender.start();

        request(new RequestEntry(USER_ADDRESS,
                USERNAME,
                METHOD,
                PATH,
                RETURN_CODE,
                REQ_RES_CONTENT_LENGTH,
                REQ_RES_CONTENT_LENGTH,
                DURATION, "userAgent"));

        final String output = out.toString();
        Assertions.assertTrue(output.contains("\"log_name\":\"artifactory-request.log\""));
        Assertions.assertTrue(output.contains("\"user\":\"username\""));
        Assertions.assertTrue(output.contains("\"method\":\"GET\""));
        Assertions.assertTrue(output.contains("\"path\":\"/path/to/file\""));
        Assertions.assertTrue(output.contains("\"client\":\"100.100.100.100\""));
        Assertions.assertTrue(output.contains("\"useragent\":\"userAgent\""));
        Assertions.assertTrue(output.contains("\"length\":100"));
        Assertions.assertTrue(output.contains("\"duration\":100"));
    }
}
