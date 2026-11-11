package org.artifactory.exception;

import lombok.Getter;
import org.apache.http.HttpStatus;

/**
 * Exception for unified event processing errors.
 * <p>
 * Usage guidelines:
 * <ul>
 *   <li>Use the constructor with the <code>shouldProceed</code> boolean and set it to <code>false</code>
 *       if a dispatcher wants Artifactory to stop further execution (e.g., critical failure).</li>
 *   <li>Use the basic constructors for general unified event errors that do not require stopping execution.</li>
 * </ul>
 * </p>
 */
@Getter
public class UnifiedEventException extends RuntimeException {

    public final int statusCode;
    public final boolean shouldProceed;

    public UnifiedEventException(String message) {
        super(message);
        this.statusCode = HttpStatus.SC_CONFLICT;
        this.shouldProceed = false;
    }

    public UnifiedEventException(int statusCode, boolean shouldProceed, String message) {
        super(message);
        this.statusCode = statusCode;
        this.shouldProceed = shouldProceed;
    }

}
