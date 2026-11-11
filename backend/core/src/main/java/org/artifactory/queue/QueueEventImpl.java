package org.artifactory.queue;

import lombok.Getter;
import org.artifactory.event.EventType;
import org.artifactory.event.queue.QueueEvent;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.event.EventInfo;

import jakarta.annotation.Nullable;
import java.util.Objects;
import java.util.Set;

public class QueueEventImpl implements QueueEvent {

    @Getter
    private final EventInfo event;
    @Getter
    private QueueEvent originalEvent;
    private final RepoPath repoPath;

    public QueueEventImpl(EventInfo event) {
        this.event = event;
        repoPath = RepoPathFactory.create(getPath());
    }

    public QueueEventImpl(QueueEventImpl event, QueueEventImpl originalEvent) {
        this(event.getEvent());
        this.originalEvent = originalEvent;
    }

    @Override
    public long getEventId() {
        return event.getEventId();
    }

    @Override
    @Nullable
    public Set<String> getHints() {
        return event.getHints();
    }

    @Override
    public String getRepoKey() {
        return repoPath.getRepoKey();
    }

    @Override
    public String getArtifactPath() {
        return repoPath.getPath();
    }

    @Override
    public String getPath() {
        return event.getPath();
    }

    @Override
    public EventType getType() {
        return event.getType();
    }

    @Override
    public long getTimestamp() {
        return event.getTimestamp();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        QueueEventImpl event = (QueueEventImpl) o;
        return getTimestamp() == event.getTimestamp()
                && getEventId() == event.getEventId()
                && getType() == event.getType()
                && Objects.equals(getPath(), event.getPath())
                && Objects.equals(getHints(), event.getHints());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTimestamp(), getEventId(), getPath(), getPath(), getHints());
    }

    @Override
    public String toString() {
        return getTimestamp() + "|" + getEventId() + "|" + getType() + "|" + getPath();
    }
}
