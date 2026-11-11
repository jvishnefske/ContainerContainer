package org.artifactory.logging.throttle;

import org.slf4j.Marker;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class LogThrottleMarker implements Marker {

    public static final String NAME = "LOG_THROTTLE";

    private static final LogThrottleMarker instance = new LogThrottleMarker(NAME);

    private final String markerName;

    private volatile List<Marker> referencedMarkersList;

    LogThrottleMarker(String name) {
        this.markerName = Objects.requireNonNull(name);
    }

    public static LogThrottleMarker getInstance() {
        return instance;
    }

    @Override
    public String getName() {
        return this.markerName;
    }

    @Override
    public void add(Marker reference) {
        Objects.requireNonNull(reference);

        // 1.no need to add the same marker multiple times
        // 2.a reference should not contain the parent marker as a "future" reference
        if (this.contains(reference) || reference.contains(this)) {
            return;
        }

        if (referencedMarkersList == null) {
            synchronized (this) {
                if (referencedMarkersList == null) {
                    referencedMarkersList = new CopyOnWriteArrayList<>();
                }
            }
        }
        referencedMarkersList.add(reference);
    }

    @Override
    public boolean remove(Marker reference) {
        if (referencedMarkersList != null) {
            return referencedMarkersList.remove(reference);
        } else {
            return false;
        }
    }

    @Override
    public boolean hasChildren() {
        return hasReferences();
    }

    @Override
    public boolean hasReferences() {
        return referencedMarkersList != null && !referencedMarkersList.isEmpty();
    }

    @Override
    public Iterator<Marker> iterator() {
        if (referencedMarkersList == null) {
            return Collections.emptyIterator();
        } else {
            return referencedMarkersList.iterator();
        }
    }

    @Override
    public boolean contains(Marker other) {
        if (this.equals(other)) {
            return true;
        }

        if (hasReferences()) {
            for (Marker ref : referencedMarkersList) {
                if (ref.contains(other)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean contains(String name) {
        if (this.markerName.equals(name)) {
            return true;
        }

        if (hasReferences()) {
            for (Marker ref : referencedMarkersList) {
                if (ref.contains(name)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        return markerName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Marker)) {
            return false;
        }

        final Marker other = (Marker) obj;
        return markerName.equals(other.getName());
    }
}
