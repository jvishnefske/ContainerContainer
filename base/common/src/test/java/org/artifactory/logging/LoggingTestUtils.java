package org.artifactory.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.vavr.collection.Array;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

public class LoggingTestUtils {
    public static LoggingEvent createEvent(Array<?> argumentArray, Class<?> clazz) {
        return createEvent(argumentArray, clazz, Level.INFO, Map.of("uber-trace-id", "traceid"));
    }

    public static LoggingEvent createEvent(Array argumentArray, Class clazz, Level level, Map<String,String> mdc) {
        Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(clazz);
        LoggingEvent event = new LoggingEvent("fqcn", logger, level, "", null,
                argumentArray.toJavaArray());
        event.setTimeStamp(Instant.ofEpochMilli(System.currentTimeMillis()).toEpochMilli());
        event.setMDCPropertyMap(mdc);
        return event;
    }
}
