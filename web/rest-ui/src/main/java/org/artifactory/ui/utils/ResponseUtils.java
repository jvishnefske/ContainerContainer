package org.artifactory.ui.utils;

import lombok.experimental.UtilityClass;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.rest.common.service.RestResponse;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * @author Eli Fishman
 */
@UtilityClass
public class ResponseUtils {

    public static void errorResponse(RestResponse<?> artifactoryResponse, BasicStatusHolder status) {
        if (status.hasErrors()) {
            int statusCode = status.getErrors().stream()
                    .map(StatusEntry::getStatusCode)
                    .findFirst()
                    .orElse(HttpServletResponse.SC_CONFLICT);
            artifactoryResponse.responseCode(statusCode);
            List<String> errors = status.getErrors()
                    .stream()
                    .map(StatusEntry::getMessage)
                    .toList();
            artifactoryResponse.errors(errors);
        }
    }
}
