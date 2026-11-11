package org.artifactory.webapp.servlet;

import com.google.common.base.Charsets;
import com.jfrog.sysconf.SysConfigConstants;
import com.jfrog.sysconf.SysLayout;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.lifecycle.webapp.servlet.LogbackConfigListener;
import org.jfrog.common.ResourceUtils;
import org.jfrog.common.test.FileTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.artifactory.api.context.ArtifactoryContext.CTX_ATTR_LOGBACK_CONFIG_MANAGER;
import static org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter.META_INF_DEFAULT_PATH;
import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_SERVICE_NAME;
import static org.artifactory.common.home.ArtifactoryHome.LOGBACK_CONFIG_FILE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the expected behavior of the pre-init logger configuration.
 *
 * @author Dan Feldman
 */
@Slf4j
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(MockitoExtension.class)
class ArtifactoryPreInitLoggerTest {

    private static final String LINE_ERR = ".*ERROR.*- MY LOGGER";
    private static final String LINE_WARN = ".*WARN.*- BRINGS";
    private static final String LINE_INFO = ".*INFO.*- ALL THE";
    private static final String LINE_DEBUG = ".*DEBUG.*- LINES";
    private static final String LINE_TRACE = ".*TRACE.*- TO THE YARD";

    private static final String ARTIFACTORY_LOGGER_INFO = "level=\"info\"";
    private static final String ARTIFACTORY_LOGGER_ERROR = "level=\"error\"";
    private static final String ARTIFACTORY_LOGGER_WARN = "level=\"warn\"";
    private static final String ARTIFACTORY_LOGGER_DEBUG = "level=\"debug\"";
    private static final String ARTIFACTORY_LOGGER_TRACE = "level=\"trace\"";

    @Mock
    private ServletContextEvent servletContextEvent;
    private LogbackConfigListener configListener;
    private SysLayout sysLayout;

    @BeforeEach
    public void setUp() throws IOException {
        String homeDir = testDir();
        System.setProperty("FORCE_CONSOLE_JSON_LOG", "false");
        sysLayout = new SysLayout(new File(homeDir), ARTIFACTORY_SERVICE_NAME);
        ServletContextStub servletContext = mock(ServletContextStub.class, CALLS_REAL_METHODS);
        servletContext.setHomeDir(homeDir);
        when(servletContextEvent.getServletContext()).thenReturn(servletContext);

        logbackFile().getParentFile().mkdirs();
        configListener = new LogbackConfigListener();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("FORCE_CONSOLE_JSON_LOG");

        logbackFile().delete();
        logFile().delete();
    }

    @Test
    void testInfo() throws IOException {
        initLogWithLevel(ARTIFACTORY_LOGGER_INFO);
        logEntries();

        assertThat(log.isErrorEnabled()).isTrue();
        assertThat(log.isWarnEnabled()).isTrue();
        assertThat(log.isInfoEnabled()).isTrue();
        assertThat(log.isDebugEnabled()).isFalse();
        assertThat(log.isTraceEnabled()).isFalse();

        final List<String> logLines = getLogFileLines();
        assertThat(logLines).hasSize(3);
        assertThat(logLines.get(0)).matches(LINE_ERR);
        assertThat(logLines.get(1)).matches(LINE_WARN);
        assertThat(logLines.get(2)).matches(LINE_INFO);
    }

    @Test
    void testError() throws IOException {
        initLogWithLevel(ARTIFACTORY_LOGGER_ERROR);
        logEntries();

        assertThat(log.isErrorEnabled()).isTrue();
        assertThat(log.isWarnEnabled()).isFalse();
        assertThat(log.isInfoEnabled()).isFalse();
        assertThat(log.isDebugEnabled()).isFalse();
        assertThat(log.isTraceEnabled()).isFalse();

        final List<String> logLines = getLogFileLines();
        assertThat(logLines).hasSize(1);
        assertThat(logLines.getFirst()).matches(LINE_ERR);
    }

    @Test
    void testWarn() throws IOException {
        initLogWithLevel(ARTIFACTORY_LOGGER_WARN);
        logEntries();

        assertThat(log.isErrorEnabled()).isTrue();
        assertThat(log.isWarnEnabled()).isTrue();
        assertThat(log.isInfoEnabled()).isFalse();
        assertThat(log.isDebugEnabled()).isFalse();
        assertThat(log.isTraceEnabled()).isFalse();

        final List<String> logLines = getLogFileLines();
        assertThat(logLines).hasSize(2);
        assertThat(logLines.get(0)).matches(LINE_ERR);
        assertThat(logLines.get(1)).matches(LINE_WARN);
    }

    @Test
    void testDebug() throws IOException {
        initLogWithLevel(ARTIFACTORY_LOGGER_DEBUG);
        logEntries();

        assertThat(log.isErrorEnabled()).isTrue();
        assertThat(log.isWarnEnabled()).isTrue();
        assertThat(log.isInfoEnabled()).isTrue();
        assertThat(log.isDebugEnabled()).isTrue();
        assertThat(log.isTraceEnabled()).isFalse();

        final List<String> logLines = getLogFileLines();
        assertThat(logLines).hasSize(4);
        assertThat(logLines.get(0)).matches(LINE_ERR);
        assertThat(logLines.get(1)).matches(LINE_WARN);
        assertThat(logLines.get(2)).matches(LINE_INFO);
        assertThat(logLines.get(3)).matches(LINE_DEBUG);
    }

    @Test
    void testTrace() throws IOException {
        initLogWithLevel(ARTIFACTORY_LOGGER_TRACE);
        logEntries();

        assertThat(log.isErrorEnabled()).isTrue();
        assertThat(log.isWarnEnabled()).isTrue();
        assertThat(log.isInfoEnabled()).isTrue();
        assertThat(log.isDebugEnabled()).isTrue();
        assertThat(log.isTraceEnabled()).isTrue();

        final List<String> logLines = getLogFileLines();
        assertThat(logLines).hasSize(5);
        assertThat(logLines.get(0)).matches(LINE_ERR);
        assertThat(logLines.get(1)).matches(LINE_WARN);
        assertThat(logLines.get(2)).matches(LINE_INFO);
        assertThat(logLines.get(3)).matches(LINE_DEBUG);
        assertThat(logLines.get(4)).matches(LINE_TRACE);
    }

    private void initLogWithLevel(String logLevel) throws IOException {
        String logbackContent = ResourceUtils.getResourceAsString(META_INF_DEFAULT_PATH + LOGBACK_CONFIG_FILE_NAME);
        logbackContent = logbackContent.replaceFirst(ARTIFACTORY_LOGGER_INFO, logLevel);
        FileUtils.writeStringToFile(logbackFile(), logbackContent, Charsets.UTF_8, false);
        configListener.contextInitialized(servletContextEvent);
    }

    private File logbackFile() {
        return new File(sysLayout.getServiceEtc(), ArtifactoryHome.LOGBACK_CONFIG_FILE_NAME);
    }

    private File logFile() {
        return new File(sysLayout.getProductLog().getAbsolutePath(), "artifactory-service.log");
    }

    private void logEntries() {
        log.error("MY LOGGER");
        log.warn("BRINGS");
        log.info("ALL THE");
        log.debug("LINES");
        log.trace("TO THE YARD");
    }

    private List<String> getLogFileLines() throws IOException {
        File logFile = logFile();
        assertThat(logFile).exists();
        return Files.readAllLines(logFile.toPath());
    }

    private String testDir() {
        return FileTestUtils.createTempDir(getClass()).getAbsolutePath();
    }

    private static abstract class ServletContextStub implements ServletContext {

        private String homeDir;
        private Object configManager;

        void setHomeDir(String homeDir) {
            this.homeDir = homeDir;
        }

        @Override
        public String getContextPath() {
            return "";
        }

        @Override
        public String getInitParameter(String name) {
            if (name.equals(SysConfigConstants.KEY_JF_PRODUCT_HOME)) {
                return homeDir;
            } else if (name.equals("logbackRefreshInterval")) {
                return "0";
            }
            return null;
        }

        @Override
        public void setAttribute(String name, Object object) {
            if (name.equals(CTX_ATTR_LOGBACK_CONFIG_MANAGER)) {
                configManager = object;
            }
        }

        @Override
        public Object getAttribute(String name) {
            if (name.equals(CTX_ATTR_LOGBACK_CONFIG_MANAGER)) {
                return configManager;
            }
            return null;
        }
    }
}
