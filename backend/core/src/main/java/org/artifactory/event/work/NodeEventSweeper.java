/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.event.work;

import com.google.common.util.concurrent.MoreExecutors;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.event.priority.service.NodeEventPriorityService;
import org.artifactory.event.priority.service.model.PrioritizedNodeEvent;
import org.jfrog.common.ExecutionUtils;
import org.jfrog.common.RetryException;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.artifactory.event.priority.service.NodeEventPriorityServiceImpl.MAX_RETRIES;

/**
 * @author Uriah Levy
 * An event worker. Invokes events via the {@link NodeEventOperator}
 */
class NodeEventSweeper implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(NodeEventSweeper.class);
    private final NodeEventsErrorService nodeEventsErrorService;
    private final NodeEventPriorityService nodeEventPriorityService;
    private final EventOperatorDispatcher dispatcher;
    private final NodeEventOperator operator;
    private final List<PrioritizedNodeEvent> eventList;
    private final ExecutionUtils.RetryOptions retryOptions;
    private final ConflictsGuard<String> conflictsGuard;

    NodeEventSweeper(NodeEventPriorityService priorityService, NodeEventOperator operator,
            EventOperatorDispatcher dispatcher, List<PrioritizedNodeEvent> eventList,
            ExecutionUtils.RetryOptions retryOptions, NodeEventsErrorService nodeEventsErrorService,
            ConflictsGuard<String> conflictsGuard) {
        this.nodeEventPriorityService = priorityService;
        this.operator = operator;
        this.dispatcher = dispatcher;
        this.eventList = eventList;
        this.retryOptions = retryOptions;
        this.nodeEventsErrorService = nodeEventsErrorService;
        this.conflictsGuard = conflictsGuard;
    }

    @Override
    public void run() {
        try {
            if (!eventList.isEmpty()) {
                eventList.stream()
                        .filter(PrioritizedNodeEvent::isPending)
                        .forEach(this::tryToProcessEvent);
            }
        } finally {
            notifyDoneAndReleaseSemaphore();
        }
    }

    void tryToProcessEvent(PrioritizedNodeEvent prioritizedEvent) {
        if (operator.isOperatorEnabled() &&
                prioritizedEvent.isPending()) { // Minor optimization to spare a few locking related queries
            try {
                dispatcher.runExclusive(conflictsGuard, prioritizedEvent.getPath(), () -> {
                    if (shouldExecute(prioritizedEvent)) { // Double check, the status may have changed
                        executeWithRetry(() -> doProcessEvent(prioritizedEvent), prioritizedEvent);
                    }
                });
            }
            catch (Exception e) {
                logEventExecutionFailure(prioritizedEvent, e);
                handleEventRetry(prioritizedEvent, e);
            }
        }
    }

    private boolean shouldExecute(PrioritizedNodeEvent prioritizedEvent) {
        if (ArtifactoryHome.get().isHaConfigured() &&
                !nodeEventPriorityService.priorityExists(prioritizedEvent)) {
            // Check for priority existence, don't handle leftover events handled by a cluster peer
            prioritizedEvent.processed();
            return false;
        }
        return operator.isOperatorEnabled() && prioritizedEvent.isPending();
    }

    void doProcessEvent(PrioritizedNodeEvent prioritizedEvent) {
        operator.operateOnEvent(prioritizedEvent);
        nodeProcessed(prioritizedEvent);
    }

    void executeWithRetry(Runnable execution, PrioritizedNodeEvent event) {
        ExecutionUtils.retry(() -> {
            try {
                execution.run();
                return null;
            }
            catch (Exception e) {
                logEventExecutionFailure(event, e);
                handleRetryableError(event, retryOptions, e);
                return null;
            }
        }, retryOptions, MoreExecutors.newDirectExecutorService());
    }

    private void handleRetryableError(PrioritizedNodeEvent event, ExecutionUtils.RetryOptions retryOptions,
            Exception e) throws RetryException {
        handleEventRetry(event, e);
        throw new RetryException(
                "Unable to execute event: " + event + " in event category: " + operator.getEventCategory(), e);
    }

    private void nodeProcessed(PrioritizedNodeEvent nodeEvent) {
        log.debug("Node processing done on event with path '{}', and type '{}'", nodeEvent.getPath(),
                nodeEvent.getType());
        nodeEventPriorityService.deleteEventPriority(nodeEvent);
        nodeEvent.processed();
        nodeEventsErrorService.removeError(operator.getEventCategory(), nodeEvent);
    }

    private void notifyDoneAndReleaseSemaphore() {
        dispatcher.sweeperDone();
    }

    private void logEventExecutionFailure(PrioritizedNodeEvent event, Exception e) {
        String message = "Unable to execute event on path " + event.getPath();
        if (e instanceof IllegalArgumentException) {
            log.warn(message, e);
        } else {
            log.error(message, e);
        }
    }

    private void handleEventRetry(PrioritizedNodeEvent event, Exception e) {
        dispatcher.batchError();
        event.setRetryCount(event.getRetryCount() + 1);
        if (event.getRetryCount() >= retryOptions.getNumberOfRetries()) {
            nodeEventPriorityService.saveRetryCount(event);
        }
        if (event.getRetryCount() >= MAX_RETRIES) {
            event.processed();
            log.warn("Short-term retry attempts exhausted at '{}' retries for '{}'", MAX_RETRIES, event.getPath());
            nodeEventPriorityService.deleteEventPriority(event);
            nodeEventsErrorService.process(operator.getEventCategory(), event, e);
        }
    }

}
