package org.artifactory.rest.common.util;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;

import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author haims
 */
public class RequestUtils {

    private RequestUtils() {
    }

    public static Map<String, List<String>> getFormParameters(ArtifactoryRestRequest<?> request) {
        Map<String, String[]> parameterMap = request.getServletRequest().getParameterMap();
        HashMap<String, List<String>> formParameters = new HashMap<>();
        for (Map.Entry<String, String[]> x : parameterMap.entrySet()) {
            formParameters.put(x.getKey(), Arrays.asList(x.getValue()));
        }
        return formParameters;
    }

    @NonNull
    public static String getNormalizedProjectKey(@Nullable String projectKey) {
        return Optional.ofNullable(projectKey)
                .filter(StringUtils::isNotEmpty)
                .orElse(ProjectsService.DEFAULT_PROJECT);
    }

    @NonNull
    public static String getNormalizedProjectKey(@NonNull ArtifactoryRestRequest<?> request) {
        String requestProjectKey = request.getQueryParamByKey(ProjectsService.PROJECT_KEY);
        if (StringUtils.isEmpty(requestProjectKey)) {
            requestProjectKey = request.getQueryParamByKey(ProjectsService.PROJECT);
        }
        return getNormalizedProjectKey(requestProjectKey);
    }
}
