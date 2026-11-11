package org.artifactory.util;

import lombok.experimental.UtilityClass;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.springframework.util.StopWatch;

import java.util.concurrent.Callable;

/**
 * @author Mor Merhav
 */
@UtilityClass
public class StopWatchUtil {

    public static <T> T measureTime(StopWatch stopWatch, Callable<T> callable) throws Exception {
        try {
            stopWatch.start();
            T result = callable.call();
            stopWatch.stop();
            return result;
        } finally {
            if (stopWatch.isRunning()) {
                stopWatch.stop();
            }
        }
    }

    public static void measureTime(StopWatch stopWatch, Runnable runnable) {
        try {
            stopWatch.start();
            runnable.run();
            stopWatch.stop();
        } finally {
            if (stopWatch.isRunning()) {
                stopWatch.stop();
            }
        }
    }

    public static void measureAndLogTime(Logger logger, Level level, String logTemplate, Runnable runnable) {
        StopWatch stopWatch = new StopWatch();
        StopWatchUtil.measureTime(stopWatch, runnable);
        LogUtil.log(logger, level, logTemplate, stopWatch.getTotalTimeMillis());
    }
}