package org.artifactory.debug;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.debug.threads.SingleThreadDump;
import org.artifactory.debug.threads.ThreadDumpResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.util.ThreadDumpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Amir Avidan
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DebugServiceImpl implements DebugService {

    private final AddonsManager addonsManager;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private Map<String, Level> oldConfigState = new HashMap<>();
    private ScheduledFuture<?> restoreLoggersTask;

    @Override
    public String getLogLevelFromClass(String className) {
        return getLogLevel(className).toString();
    }

    private Level getLogLevel(String className) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        return Optional.ofNullable(lc.getLogger(className).getLevel())
                .orElse(lc.getLogger(Logger.ROOT_LOGGER_NAME).getLevel());
    }

    @Override
    public void changeLogLevel(DebugLevelConfig config) {
        assertInputConfig(config);
        configLogLevel(config);
        propagateRequest(config);
    }

    private void assertInputConfig(DebugLevelConfig config) {
        if (config.classes() == null) {
            throw new BadRequestException("Classes mandatory field");
        }
        if (config.timer() <= 0) {
            throw new BadRequestException("Timer must be greater then 0");
        }
        if (config.classes().size() > 100) {
            throw new BadRequestException("Number of classes is greater than 100");
        }
    }

    private void configLogLevel(DebugLevelConfig debugLevelConfig) {
        oldConfigState = debugLevelConfig.classes().keySet().stream()
                .collect(Collectors.toMap(k -> k, this::getLogLevel));
        setLoggingLevel(debugLevelConfig.classes());
        restoreLoggersTask =
                executorService.schedule(this::restoreLoggersToOriginalState, debugLevelConfig.timer(), SECONDS);
        log.debug("Scheduled restore loggers settings in {} seconds", debugLevelConfig.timer());
    }

    private void setLoggingLevel(Map<String, Level> state) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        state.forEach((className, level) -> {
            lc.getLogger(className).setLevel(level);
            log.debug("Set class: {} logger level: {}", className, level);
        });
    }

    private void restoreLoggersToOriginalState() {
        log.debug("Start loggers level rollback");
        setLoggingLevel(oldConfigState);
        oldConfigState.clear();
        restoreLoggersTask = null;
    }

    @Override
    public void restoreDebugSettings() {
        if (restoreLoggersTask == null) {
            log.debug("No loggers settings to delete");
            return;
        }
        cancelTaskIfNeeded();
        propagateRequest(DebugLevelConfig.EMPTY);
    }

    private void cancelTaskIfNeeded() {
        boolean taskNotStarted = restoreLoggersTask.cancel(false);
        if (taskNotStarted) {
            log.debug("Found loggers settings to delete");
            restoreLoggersToOriginalState();
        } else {
            log.debug("Delete loggers config task already started");
        }
    }

    private void propagateRequest(DebugLevelConfig debugLevelConfig) {
        log.debug("Start propagate loggers config");
        addonsManager.addonByType(HaAddon.class).propagateDebugLevelChange(debugLevelConfig);
    }

    @Override
    public void propagateLoggersSettings(DebugLevelConfig debugLevelConfig) {
        if (debugLevelConfig.equals(DebugLevelConfig.EMPTY)) {
            cancelTaskIfNeeded();
        } else {
            configLogLevel(debugLevelConfig);
        }
        log.debug("Finished propagate loggers config");
    }

    @Override
    public ThreadDumpResponse generateThreadDump(boolean propagatePods) {
        ThreadDumpResponse threadDumpResponse = new ThreadDumpResponse();
        threadDumpResponse.addSingleThreadDump(generateSingleThreadDump());

        if (propagatePods) {
            List<SingleThreadDump> singleThreadDumps = addonsManager.addonByType(HaAddon.class).propagateThreadDumps();
            threadDumpResponse.addAllThreadDumps(singleThreadDumps);
        }

        return threadDumpResponse;
    }

    @Override
    public SingleThreadDump generateSingleThreadDump() {
        String dump = ThreadDumpUtils.getSingleThreadDump();
        String serverName = ContextHelper.get().getServerId();

        return new SingleThreadDump(serverName, dump);
    }
}
