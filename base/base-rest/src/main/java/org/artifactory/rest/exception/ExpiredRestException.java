package org.artifactory.rest.exception;

import java.util.Map;

public class ExpiredRestException extends RestException {
    public ExpiredRestException(String message) {
        super(message);
    }

    public ExpiredRestException(int statusCode, String message) {
        super(statusCode, message);
    }

    public ExpiredRestException(int statusCode, String message, Map<String, String> headers) {
        super(statusCode, message, headers);
    }
}
