package org.artifactory.common.config;

import java.util.Collection;
import java.util.Map;

/**
 *
 * @author Alexei Vainshtein
 */
@Deprecated(since = "7.130.1")
public class NegotiationException extends RuntimeException {

    private Map<String, Collection<Conflict>> conflicts;

    public NegotiationException(String message) {
        super(message);
    }

    public NegotiationException(String message, Map<String, Collection<Conflict>> conflicts) {
        super(message);
        this.conflicts = conflicts;
    }
    public Map<String, Collection<Conflict>> getConflicts() {
        return conflicts;
    }
}

