package org.artifactory.queue.execution;

import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.workqueue.WorkItem;

import jakarta.annotation.Nonnull;
import java.util.Objects;

public class TriggerWorkQueueWorkItem extends WorkItem {
    private final String repoKey;
    private final NodeEventCursorType cursorType;

    public TriggerWorkQueueWorkItem(String repoKey, NodeEventCursorType cursorType) {
        this.repoKey = repoKey;
        this.cursorType = cursorType;
    }

    public String getRepoKey() {
        return repoKey;
    }

    public NodeEventCursorType getCursorType() {
        return cursorType;
    }

    @Nonnull
    @Override
    public String getUniqueKey() {
        return repoKey + "----" + cursorType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TriggerWorkQueueWorkItem that = (TriggerWorkQueueWorkItem) o;
        return Objects.equals(repoKey, that.repoKey) &&
                cursorType == that.cursorType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(repoKey, cursorType);
    }
}
