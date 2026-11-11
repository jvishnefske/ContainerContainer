package org.artifactory.storage.db.devopsagent.requestlog.service;

import java.util.Locale;
import java.util.Set;

class RequestLogValidation {

    static final Set<String> ALLOWED_HTTP_METHODS =
            Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE", "CONNECT");

    static boolean isValidMethod(String method) {
        return method != null && ALLOWED_HTTP_METHODS.contains(method.toUpperCase(Locale.ROOT));
    }

    static boolean isValidStatus(int statusCode) {
        return statusCode >= 100 && statusCode <= 599;
    }

    private RequestLogValidation() {
    }
}
