package org.artifactory.aql;

/**
 * @author Dvir Twina
 */
public class AqlResultRuntimeException extends RuntimeException {

    public AqlResultRuntimeException(String s, Exception e) {
        super(s, e);
    }
}
