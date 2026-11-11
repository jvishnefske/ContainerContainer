package org.artifactory.workqueue;

import org.artifactory.storage.tx.SessionResourceAdapter;
import org.artifactory.workqueue.WorkQueueSessionService.WorkQueueSessionCompletionCallback;

import java.util.ArrayList;
import java.util.List;

public class WorkQueueSessionResource extends SessionResourceAdapter {
    private List<WorkQueueSessionCompletionCallback> callbacks = new ArrayList<>();

    @Override
    public void afterConnectionReleased(boolean commit) {
        if (commit) {
            callbacks.forEach(callback -> callback.afterCompletion());
        }
    }

    protected void addCallback(WorkQueueSessionCompletionCallback callback) {
        callbacks.add(callback);
    }
}
