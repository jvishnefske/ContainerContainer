/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.api.common;

import ch.qos.logback.classic.Level;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.common.status.StatusEntryLevel;
import org.artifactory.common.status.StatusEntryWithDifferentLogLevel;
import org.artifactory.exception.CancelException;
import org.artifactory.logging.throttle.LogThrottleMarker;
import org.slf4j.Logger;
import org.slf4j.Marker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static ch.qos.logback.classic.Level.ERROR;

/**
 * @author Yoav Landman
 */
@Slf4j
public class BasicStatusHolder implements MutableStatusHolder {

    // Queues are allocated lazily on first use (see getOrCreate) to avoid reserving ~2600 slots for holders that
    // never record an entry.
    private static final int STATUS_ENTRIES_CAPACITY = 500;
    private static final int ERROR_ENTRIES_CAPACITY = 2000;
    private static final int WARNING_ENTRIES_CAPACITY = 100;

    // save up to 500 messages. if exhausted, we manually drop the oldest element
    private final AtomicReference<ArrayBlockingQueue<StatusEntry>> statusEntries = new AtomicReference<>();
    // save up to 2000 errors messages. if exhausted, we manually drop the oldest element
    private final AtomicReference<ArrayBlockingQueue<StatusEntry>> errorEntries = new AtomicReference<>();
    // save up to 100 warning messages. if exhausted, we manually drop the oldest element
    private final AtomicReference<ArrayBlockingQueue<StatusEntry>> warningEntries = new AtomicReference<>();

    private static final int CODE_OK = 200;
    private static final int CODE_INTERNAL_ERROR = 500;

    private volatile boolean activateLogging;
    // the latest status
    private volatile boolean fastFail = false;
    protected volatile boolean verbose = false;
    private final AtomicReference<StatusEntry> lastStatusEntry = new AtomicReference<>();
    private final AtomicReference<StatusEntry> lastErrorStatusEntry = new AtomicReference<>();
    private final AtomicReference<StatusEntry> lastWarningStatusEntry = new AtomicReference<>();

    public BasicStatusHolder() {
        activateLogging = true;
    }

    private static ArrayBlockingQueue<StatusEntry> getOrCreate(
            AtomicReference<ArrayBlockingQueue<StatusEntry>> ref, int capacity) {
        ArrayBlockingQueue<StatusEntry> queue = ref.get();
        if (queue == null) {
            queue = new ArrayBlockingQueue<>(capacity);
            if (!ref.compareAndSet(null, queue)) {
                queue = ref.get();
            }
        }
        return queue;
    }

    private static void clearQueue(AtomicReference<ArrayBlockingQueue<StatusEntry>> ref) {
        ArrayBlockingQueue<StatusEntry> queue = ref.get();
        if (queue != null) {
            queue.clear();
        }
    }

    private static void record(AtomicReference<ArrayBlockingQueue<StatusEntry>> ref, int capacity,
            StatusEntry entry) {
        record(getOrCreate(ref, capacity), entry);
    }

    private static void record(ArrayBlockingQueue<StatusEntry> queue, StatusEntry entry) {
        while (!queue.offer(entry)) {
            queue.poll();
        }
    }

    @Override
    public void setFastFail(boolean fastFail) {
        this.fastFail = fastFail;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private boolean isFastFail() {
        return fastFail;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public StatusEntry getLastError() {
        return lastErrorStatusEntry.get();
    }

    @Override
    public StatusEntry getLastWarning() {
        return lastWarningStatusEntry.get();
    }

    @Override
    public StatusEntry getLastStatusEntry() {
        return lastStatusEntry.get();
    }

    @Override
    public final void debug(String statusMsg, @Nonnull Logger logger) {
        logEntryAndAddEntry(new StatusEntry(CODE_OK, StatusEntryLevel.DEBUG, statusMsg, null), logger);
    }

    public final void setDebug(String statusMsg, int statusCode, @Nonnull Logger logger) {
        logEntryAndAddEntry(new StatusEntry(statusCode, StatusEntryLevel.DEBUG, statusMsg, null), logger);
    }

    @Override
    public final void status(String statusMsg, @Nonnull Logger logger) {
        status(statusMsg, CODE_OK, logger);
    }

    @Override
    public final void status(String statusMsg, int statusCode, @Nonnull Logger logger) {
        logEntryAndAddEntry(new StatusEntry(statusCode, statusMsg), logger);
    }

    @Override
    public void error(String status, Throwable throwable, @Nonnull Logger logger) {
        error(status, CODE_INTERNAL_ERROR, throwable, logger);
    }

    @Override
    public void error(String statusMsg, Logger logger) {
        error(statusMsg, CODE_INTERNAL_ERROR, null, logger);
    }

    @Override
    public void error(String statusMsg, int statusCode, Logger logger) {
        error(statusMsg, statusCode, null, logger);
    }

    @Override
    public void error(String statusMsg, int statusCode, Throwable throwable, Logger logger) {
        addError(new StatusEntry(statusCode, StatusEntryLevel.ERROR, statusMsg, throwable), logger);
    }

    @Override
    public void error(String statusMsg, int statusCode, Logger logger, Marker marker) {
        addError(new StatusEntry(statusCode, StatusEntryLevel.ERROR, statusMsg, null), logger, null, marker);
    }

    @Override
    public void error(String statusMsg, int statusCode, Logger logger, Throwable throwable, Marker marker) {
        addError(new StatusEntry(statusCode, StatusEntryLevel.ERROR, statusMsg, throwable), logger, null, marker);
    }

    @Override
    public void errorWithStatusEntryWithDifferentLogLevel(String message, int statusCode, Throwable throwable,
            @Nonnull Logger logger,
            Level level) {
        addError(new StatusEntryWithDifferentLogLevel(statusCode, StatusEntryLevel.ERROR, message, throwable, level),
                logger);
    }

    @Override
    public void errorWithDifferentLogLevel(String message, int statusCode, Throwable throwable, @Nonnull Logger logger,
            Level level) {
        addError(new StatusEntry(statusCode, StatusEntryLevel.ERROR, message, throwable), logger, level);
    }

    @Override
    public void errorWithDifferentLogLevel(String message, int statusCode, @Nonnull Logger logger, Level level) {
        addError(new StatusEntry(statusCode, StatusEntryLevel.ERROR, message), logger, level);
    }

    /**
     * Convenience method to log an error with log throttling using LogThrottleMarker.
     * This ensures the error is recorded in the statusHolder and logged with throttling.
     *
     * @param statusMsg The error message
     * @param throwable The exception (can be null)
     * @param logger The logger to use
     */
    public void errorWithThrottle(String statusMsg, Throwable throwable, @Nonnull Logger logger) {
        error(statusMsg, CODE_INTERNAL_ERROR, logger, throwable, LogThrottleMarker.getInstance());
    }

    /**
     * Convenience method to log a warning with log throttling using LogThrottleMarker.
     * This ensures the warning is recorded in the statusHolder and logged with throttling.
     *
     * @param statusMsg The warning message
     * @param throwable The exception (can be null)
     * @param logger The logger to use
     */
    public void warnWithThrottle(String statusMsg, Throwable throwable, @Nonnull Logger logger) {
        addError(new StatusEntry(CODE_INTERNAL_ERROR, StatusEntryLevel.WARNING, statusMsg, throwable),
                logger, null, LogThrottleMarker.getInstance());
    }

    @Override
    public void warn(String statusMsg, Throwable throwable, @Nonnull Logger logger) {
        addError(new StatusEntry(CODE_INTERNAL_ERROR, StatusEntryLevel.WARNING, statusMsg, throwable), logger);
    }

    @Override
    public void warn(String statusMsg, int statusCode, Throwable throwable, @Nonnull Logger logger) {
        addError(new StatusEntry(statusCode, StatusEntryLevel.WARNING, statusMsg, throwable), logger);
    }

    @Override
    public void warn(String statusMsg, @Nonnull Logger logger) {
        addError(new StatusEntry(CODE_INTERNAL_ERROR, StatusEntryLevel.WARNING, statusMsg, null), logger);
    }

    @Override
    public void warn(String statusMsg, int statusCode, @Nonnull Logger logger) {
        addError(new StatusEntry(statusCode, StatusEntryLevel.WARNING, statusMsg, null), logger);
    }

    private void addError(@Nonnull StatusEntry errorEntry, Logger logger) {
        addError(errorEntry, logger, null);
    }

    private void addError(@Nonnull StatusEntryWithDifferentLogLevel errorEntry, Logger logger) {
        addError(errorEntry, logger, errorEntry.getLogLevel());
    }

    private void addError(@Nonnull StatusEntry errorEntry, Logger logger, Level level) {
        addError(errorEntry, logger, level, null);
    }

    private void addError(@Nonnull StatusEntry errorEntry, Logger logger, Level level, Marker marker) {
        if (isActivateLogging() && logger != null) {
            if (level != null) {
                logEntryWithLevel(errorEntry, logger, level);
            } else {
                logEntry(errorEntry, logger, marker);
            }
        }
        addStatusEntry(errorEntry);
        if (!errorEntry.isWarning() && isFastFail()) {
            Throwable throwable = errorEntry.getException();
            if (throwable != null) {
                if (throwable instanceof RuntimeException) {
                    throw (RuntimeException) throwable;
                } else if (throwable instanceof Error) {
                    throw (Error) throwable;
                } else {
                    throw new RuntimeException("Fast fail exception: " + errorEntry.getMessage(), throwable);
                }
            } else {
                throw new RuntimeException("Fast fail exception: " + errorEntry.getMessage());
            }
        }
    }

    /**
     * If an external logger is given, it shall be the active one; unless verbose output is requested, then we need
     * to use that status holder logger for the debug level
     */
    protected void logEntry(@Nonnull StatusEntry entry, @Nonnull Logger logger) {
        logEntry(entry, logger, null);
    }

    private void logEntry(@Nonnull StatusEntry entry, @Nonnull Logger logger, Marker marker) {
        if (isVerbose()) {
            doLogEntry(entry, log, marker);
        } else {
            doLogEntry(entry, logger, marker);
        }
    }

    protected void logEntryWithLevel(@Nonnull StatusEntry entry, @Nonnull Logger logger, Level level) {
        if (isVerbose()) {
            doLogEntryWithLevel(entry, log, level);
        } else {
            doLogEntryWithLevel(entry, logger, level);
        }
    }

    void doLogEntryWithLevel(@Nonnull StatusEntry entry, @Nonnull Logger logger, Level level) {
        String statusMessage = getStatusMsgForLog(entry);
        Throwable throwable = entry.getException();
        if(shouldUseVerboseForLogLevel(level) && isVerbose()) {
            logByLevelWithVerbose(logger, level, statusMessage, throwable);
        } else {
            logByLevel(logger, level, statusMessage);
        }
    }

    private boolean shouldUseVerboseForLogLevel(Level level) {
        return level.equals(Level.WARN) || level.equals(ERROR);
    }

    private void logByLevel(Logger logger, Level level, String statusMessage) {
        if (level.equals(Level.WARN) && logger.isWarnEnabled()) {
            logger.warn(statusMessage);
        } else if (level.equals(ERROR) && logger.isErrorEnabled()) {
            logger.error(statusMessage);
        } else if (level.equals(Level.DEBUG) && logger.isDebugEnabled()) {
            logger.debug(statusMessage);
        } else if (level.equals(Level.INFO) && logger.isInfoEnabled()) {
            logger.info(statusMessage);
        }
    }

    private void logByLevelWithVerbose(Logger logger, Level level, String statusMessage, Throwable throwable) {
        if (level.equals(Level.WARN) && logger.isWarnEnabled()) {
            logger.warn(statusMessage, throwable);
        } else if (level.equals(ERROR) && logger.isErrorEnabled()) {
            logger.error(statusMessage, throwable);
        }
    }

    private String getStatusMsgForLog(StatusEntry entry) {
        String statusMessage = entry.getMessage();
        Throwable throwable = entry.getException();
        if (!isVerbose() && throwable != null) {
            //Update the status message for when there's an exception message to append
            statusMessage += ": " + (StringUtils.isNotBlank(throwable.getMessage()) ? throwable.getMessage() :
                    throwable.getClass().getSimpleName());
        }
        return statusMessage;
    }

    void doLogEntry(@Nonnull StatusEntry entry, @Nonnull Logger logger) {
        doLogEntry(entry, logger, null);
    }

    void doLogEntry(@Nonnull StatusEntry entry, @Nonnull Logger logger, Marker marker) {
        String statusMessage = sanitizeMessage(entry.getMessage());
        Throwable throwable = entry.getException();
        if (!isVerbose() && throwable != null) {
            //Update the status message for when there's an exception message to append
            statusMessage += ": " + (StringUtils.isNotBlank(throwable.getMessage()) ? throwable.getMessage() :
                    throwable.getClass().getSimpleName());
        }
        if (entry.isWarning() && logger.isWarnEnabled()) {
            if (isVerbose()) {
                logger.warn(statusMessage, throwable);
            } else {
                logger.warn(statusMessage);
            }
        } else if (entry.isError() && logger.isErrorEnabled()) {
            logError(logger, marker, statusMessage, throwable);
        } else if (entry.isDebug() && logger.isDebugEnabled()) {
            logger.debug(statusMessage);
        } else if (entry.isInfo() && logger.isInfoEnabled()) {
            logger.info(statusMessage);
        }
    }

    private void logError(@Nonnull Logger logger, Marker marker, String statusMessage, Throwable throwable) {
        if (isVerbose() && throwable != null) {
            logger.error(statusMessage, throwable);
        }
        else {
            if (marker != null) {
                logger.error(marker, statusMessage);
            }
            else {
                logger.error(statusMessage);
            }
        }
    }


    @Override
    public String getStatusMsg() {
        StatusEntry lastError = getLastError();
        if (lastError != null) {
            return lastError.getMessage();
        }
        StatusEntry lastWarning = getLastWarning();
        if (lastWarning != null) {
            return lastWarning.getMessage();
        }
        StatusEntry statusEntry = getLastStatusEntry();
        return statusEntry != null ? statusEntry.getMessage() : null;
    }

    /**
     * Aggregate all statuses into a single message.
     * @return A message that summarizes the overall status of this holder.
     */
    public String getAggregatedStatusMsg() {
        StringBuilder stringBuilder = new StringBuilder();
        if (hasErrors()) {
            for (StatusEntry error : getErrors()) {
                stringBuilder.append(error.getMessage()).append(", ");
            }
        }
        if (hasWarnings()) {
            for (StatusEntry warning : getWarnings()) {
                stringBuilder.append(warning.getMessage()).append(", ");
            }
        }
        if (!stringBuilder.isEmpty()) {
            removeLastCommaAndSpace(stringBuilder);
        } else {
            stringBuilder.append(getLastStatusEntry() != null ? getLastStatusEntry().getMessage(): "");
        }
        return stringBuilder.toString();
    }

    private static void removeLastCommaAndSpace(StringBuilder stringBuilder) {
        stringBuilder.setLength(stringBuilder.length() - 2);
    }

    @Override
    public boolean isError() {
        return getLastError() != null;
    }

    @Override
    public boolean isWarning() {
        return getLastWarning() != null;
    }

    @Override
    public CancelException getCancelException() {
        return getCancelException(null);
    }

    public CancelException getCancelException(StatusEntry previousToLastError) {
        StatusEntry lastError = getLastError();
        if (lastError != null && !lastError.equals(previousToLastError)) {
            //We have a new error check if it is a cancellation one
            Throwable cause = lastError.getException();
            if (cause instanceof CancelException) {
                return (CancelException) cause;
            }
        }
        return null;
    }

    @Override
    public Throwable getException() {
        StatusEntry lastError = getLastError();
        if (lastError != null) {
            return lastError.getException();
        }
        StatusEntry lastWarning = getLastWarning();
        if (lastWarning != null) {
            return lastWarning.getException();
        }
        StatusEntry statusEntry = getLastStatusEntry();
        return statusEntry != null ? statusEntry.getException() : null;
    }

    @Override
    public int getStatusCode() {
        StatusEntry lastError = getLastError();
        if (lastError != null) {
            return lastError.getStatusCode();
        }
        StatusEntry lastWarning = getLastWarning();
        if (lastWarning != null) {
            return lastWarning.getStatusCode();
        }
        StatusEntry statusEntry = getLastStatusEntry();
        return statusEntry != null ? statusEntry.getStatusCode() : -1;
    }

    private void logEntryAndAddEntry(@Nonnull StatusEntry entry, @Nonnull Logger logger) {
        addStatusEntry(entry);
        logEntry(entry, logger);
    }

    protected void addStatusEntry(StatusEntry entry) {
        record(statusEntries, STATUS_ENTRIES_CAPACITY, entry);
        lastStatusEntry.set(entry);
        if (entry.isError()) {
            record(errorEntries, ERROR_ENTRIES_CAPACITY, entry);
            lastErrorStatusEntry.set(entry);
        } else if (entry.isWarning()) {
            record(warningEntries, WARNING_ENTRIES_CAPACITY, entry);
            lastWarningStatusEntry.set(entry);
        }
    }

    /**
     * @return True if the status holder prints the messages to the logger.
     */
    private boolean isActivateLogging() {
        return activateLogging;
    }

    /**
     * If set to false the status holder will not print the messages to the logger. It will only keep the statuses.
     *
     * @param activateLogging Set to false to disable logging
     */
    @Override
    public void setActivateLogging(boolean activateLogging) {
        this.activateLogging = activateLogging;
    }

    @Override
    public void reset() {
        clearQueue(statusEntries);
        clearQueue(errorEntries);
        clearQueue(warningEntries);
        activateLogging = true;
    }

    @Override
    public String toString() {
        return "StatusHolder{" +
                "activateLogging=" + activateLogging +
                ", statusMessage=" + statusEntries.get() + '}' +
                ", errorMessage=" + errorEntries.get() + '}' +
                ", warningMessage=" + warningEntries.get() + '}';
    }

    /**
     * Merge this and the input status. Will append entry from the input this one. If the status to merge has last error
     * it will be used. This method is not thread safe, the two statuses are assumed to be inactive in the time of
     * merging.
     *
     * @param toMerge The status to merge into this.
     */
    public void merge(MutableStatusHolder toMerge) {
        merge(toMerge.getEntries(), getOrCreate(statusEntries, STATUS_ENTRIES_CAPACITY), lastStatusEntry);
        merge(toMerge.getWarnings(), getOrCreate(warningEntries, WARNING_ENTRIES_CAPACITY), lastWarningStatusEntry);
        merge(toMerge.getErrors(), getOrCreate(errorEntries, ERROR_ENTRIES_CAPACITY), lastErrorStatusEntry);
    }

    private void merge(
            @Nonnull List<StatusEntry> statuses,
            @Nonnull ArrayBlockingQueue<StatusEntry> statusesReceiver,
            @Nonnull AtomicReference<StatusEntry> lastStatusReceiver) {
        if (!statuses.isEmpty()) {
            lastStatusReceiver.set(statuses.getFirst());
        }
        for (StatusEntry statusEntry : statuses) {
            record(statusesReceiver, statusEntry);
        }
    }

    public List<StatusEntry> getEntries() {
        ArrayBlockingQueue<StatusEntry> statuses = statusEntries.get();
        return statuses == null ? Lists.newArrayList() : Lists.newArrayList(statuses);
    }

    public List<StatusEntry> getEntries(StatusEntryLevel level) {
        List<StatusEntry> result = new ArrayList<>();
        if (level == StatusEntryLevel.ERROR) {
            ArrayBlockingQueue<StatusEntry> errors = errorEntries.get();
            if (errors != null) {
                result.addAll(errors);
            }
        } else {
            ArrayBlockingQueue<StatusEntry> statuses = statusEntries.get();
            if (statuses != null) {
                for (StatusEntry entry : statuses) {
                    if (level.equals(entry.getLevel())) {
                        result.add(entry);
                    }
                }
            }
        }
        return result;
    }

    public boolean hasWarnings() {
        ArrayBlockingQueue<StatusEntry> warnings = warningEntries.get();
        return warnings != null && !warnings.isEmpty();
    }

    public boolean hasErrors() {
        ArrayBlockingQueue<StatusEntry> queue = errorEntries.get();
        return queue != null && !queue.isEmpty();
    }

    public boolean hasErrorsOrWarnings() {
        return hasErrors() || hasWarnings();
    }

    public List<StatusEntry> getErrors() {
        ArrayBlockingQueue<StatusEntry> queue = errorEntries.get();
        return queue == null ? Lists.newArrayList() : Lists.newArrayList(queue);
    }

    public List<StatusEntry> getWarnings() {
        ArrayBlockingQueue<StatusEntry> queue = warningEntries.get();
        return queue == null ? Lists.newArrayList() : Lists.newArrayList(queue);
    }

    public StatusEntry getMostImportantErrorStatusCode() {
        List<StatusEntry> errorStatusEntries = getErrors();
        Optional<StatusEntry> max = errorStatusEntries.stream()
                .filter(entry -> isPreferredStatusCode(entry.getStatusCode()))
                .max(StatusEntryComparators.sortByStatusCodeImportance());

        return max.orElseGet(() -> errorStatusEntries.stream().
                max(StatusEntryComparators.sortByStatusCodeImportance())
                .orElse(null));

    }

    public StatusEntry getMostImportantWarningsStatusCode() {
        List<StatusEntry> warnings = getWarnings();
        Optional<StatusEntry> max = warnings.stream().filter(entry -> isPreferredStatusCode(entry.getStatusCode())).
                max(StatusEntryComparators.sortByStatusCodeImportance());

        return max.orElseGet(() -> warnings.stream().
                max(StatusEntryComparators.sortByStatusCodeImportance())
                .orElse(null));
    }

    private boolean isPreferredStatusCode(int code) {
        switch (code) {
            case 500:
            case 409:
            case 404:
            case 403:
            case 400:
                return true;
            default:
                return false;
        }
    }

    private String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll("[\n\r]", "_");
    }

    void clear() {
        clearQueue(statusEntries);
        clearQueue(errorEntries);
        clearQueue(warningEntries);
    }
}
