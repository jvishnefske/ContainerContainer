package org.artifactory.logging.filters;

import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URL;
import java.util.stream.Stream;

import static ch.qos.logback.core.spi.FilterReply.ACCEPT;
import static ch.qos.logback.core.spi.FilterReply.DENY;
import static com.jfrog.sysconf.SysConfigConstants.KEY_JF_PRODUCT_HOME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Dor Solomon
 */
class ConsoleJsonFormatFilterTest {

    ConsoleJsonFormatFilter consoleJsonFormatFilter;

    @BeforeEach
    public void setUp() {
        consoleJsonFormatFilter = new ConsoleJsonFormatFilter();
    }

    @AfterEach
    public void tearDown() throws Exception {
        ConsoleJsonFormatFilter.isEnabled = null;
        System.clearProperty("jf." + KEY_JF_PRODUCT_HOME);
    }

    @ParameterizedTest
    @MethodSource("consoleJsonFormat")
    void testDecide(String homeDir, FilterReply filterReply) {
        setHomeDir(homeDir);
        assertEquals(filterReply, consoleJsonFormatFilter.decide(null));
    }

    @ParameterizedTest
    @MethodSource("forceConsoleJsonFormat")
    void testForceJsonLog(String force, FilterReply filterReply) {
        System.setProperty("FORCE_CONSOLE_JSON_LOG", force);
        assertEquals(filterReply, consoleJsonFormatFilter.decide(null));
    }

    private void setHomeDir(String homeDir) {
        URL path = ConsoleJsonFormatFilterTest.class.getResource("/filters/" + homeDir);
        assertNotNull(path);
        System.setProperty("jf." + KEY_JF_PRODUCT_HOME, path.getPath());
    }

    static Stream<Arguments> consoleJsonFormat() {
        return Stream.of(
                Arguments.of("json_log_default", DENY),
                Arguments.of("json_log_disabled", DENY),
                Arguments.of("json_log_enabled", ACCEPT)
        );
    }

    static Stream<Arguments> forceConsoleJsonFormat() {
        return Stream.of(
                Arguments.of("true", ACCEPT),
                Arguments.of("false", DENY)
        );
    }
}