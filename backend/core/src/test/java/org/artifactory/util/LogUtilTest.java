package org.artifactory.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LogUtilTest {

    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = mock(Logger.class);
    }

    @Test
    void testLogWithNoParams() {
        LogUtil.log(logger, Level.INFO, "info");
        verify(logger).info("info");

        LogUtil.log(logger, Level.DEBUG, "debug");
        verify(logger).debug("debug");

        LogUtil.log(logger, Level.WARN, "warn");
        verify(logger).warn("warn");

        LogUtil.log(logger, Level.ERROR, "error");
        verify(logger).error("error");

        LogUtil.log(logger, Level.TRACE, "trace");
        verify(logger).trace("trace");
    }

    @Test
    void testLogWithOneParam() {
        Object param = "param";
        LogUtil.log(logger, Level.INFO, "info {}", param);
        verify(logger).info("info {}", param);

        LogUtil.log(logger, Level.DEBUG, "debug {}", param);
        verify(logger).debug("debug {}", param);

        LogUtil.log(logger, Level.WARN, "warn {}", param);
        verify(logger).warn("warn {}", param);

        LogUtil.log(logger, Level.ERROR, "error {}", param);
        verify(logger).error("error {}", param);

        LogUtil.log(logger, Level.TRACE, "trace {}", param);
        verify(logger).trace("trace {}", param);
    }

    @Test
    void testLogWithMultipleParams() {
        Object[] params = {"param1", "param2"};
        LogUtil.log(logger, Level.INFO, "info {} {}", params);
        verify(logger).info("info {} {}", params);

        LogUtil.log(logger, Level.DEBUG, "debug {} {}", params);
        verify(logger).debug("debug {} {}", params);

        LogUtil.log(logger, Level.WARN, "warn {} {}", params);
        verify(logger).warn("warn {} {}", params);

        LogUtil.log(logger, Level.ERROR, "error {} {}", params);
        verify(logger).error("error {} {}", params);

        LogUtil.log(logger, Level.TRACE, "trace {} {}", params);
        verify(logger).trace("trace {} {}", params);
    }

    @Test
    void testLogWithNullParams() {
        LogUtil.log(logger, Level.INFO, "info", (Object[]) null);
        verify(logger).info("info");

        LogUtil.log(logger, Level.DEBUG, "debug", (Object[]) null);
        verify(logger).debug("debug");

        LogUtil.log(logger, Level.WARN, "warn", (Object[]) null);
        verify(logger).warn("warn");

        LogUtil.log(logger, Level.ERROR, "error", (Object[]) null);
        verify(logger).error("error");

        LogUtil.log(logger, Level.TRACE, "trace", (Object[]) null);
        verify(logger).trace("trace");
    }
}