package org.artifactory.util;

import lombok.experimental.UtilityClass;
import org.slf4j.Logger;
import org.slf4j.event.Level;

/**
 * This utility class is a workaround until <a href="https://jfrog-int.atlassian.net/browse/RTDEV-58341">RTDEV-58341</a>
 * is resolved.
 * @author Mor Merhav
 */
@UtilityClass
public class LogUtil {

    public void log(Logger logger, Level level, String message, Object ... params) {
        if (params != null && params.length == 1) {
            switch (level) {
                case INFO -> logger.info(message, params[0]);
                case DEBUG -> logger.debug(message, params[0]);
                case WARN -> logger.warn(message, params[0]);
                case ERROR -> logger.error(message, params[0]);
                case TRACE -> logger.trace(message, params[0]);
            }
        } else if (params != null && params.length > 1) {
            switch (level) {
                case INFO -> logger.info(message, params);
                case DEBUG -> logger.debug(message, params);
                case WARN -> logger.warn(message, params);
                case ERROR -> logger.error(message, params);
                case TRACE -> logger.trace(message, params);
            }
        } else {
            switch (level) {
                case INFO -> logger.info(message);
                case DEBUG -> logger.debug(message);
                case WARN -> logger.warn(message);
                case ERROR -> logger.error(message);
                case TRACE -> logger.trace(message);
            }
        }
    }
}
