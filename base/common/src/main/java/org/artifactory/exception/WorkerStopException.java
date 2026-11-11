package org.artifactory.exception;

import com.google.protobuf.GeneratedMessageV3;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.jfrog.worker.proto.execution.Action;

import java.util.Collections;
import java.util.List;

/**
 * @author Tomer Slutsky
 */
public class WorkerStopException extends RuntimeException {

    private final List<? extends GeneratedMessageV3> responsesList;
    private static final String WORKER_EVENT_ERROR = " Worker Event Error: ";

    public WorkerStopException(Action action, String message) {
        super(action.name() + WORKER_EVENT_ERROR + message);
        responsesList = Collections.emptyList();
    }

    public WorkerStopException(String message) {
        super(message);
        responsesList = Collections.emptyList();
    }

    public WorkerStopException(SupportedUnifiedEvents action, String message) {
        super(action.name() + WORKER_EVENT_ERROR + message);
        responsesList = Collections.emptyList();
    }

    public WorkerStopException(Action action, String message, List<? extends GeneratedMessageV3> responsesList) {
        super(action.name() + WORKER_EVENT_ERROR + message);
        this.responsesList = responsesList;
    }

    @SuppressWarnings("unchecked")
    public List<GeneratedMessageV3> getResponsesList() {
        return (List<GeneratedMessageV3>) responsesList;
    }
}
