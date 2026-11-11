package org.artifactory.webapp.servlet;

import com.google.common.annotations.VisibleForTesting;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Set;

import static org.artifactory.common.ConstantValues.requestLogsExcludedQueryParamsPaths;
import static org.artifactory.common.ConstantValues.requestLogsExcludedQueryParams;

@Slf4j
@UtilityClass
public class RequestLogSanitizer {
    private static final String KEY_VALUE_SEPARATOR = "=";
    private static final String QUERY_PARAM_SEPARATOR = "&";
    private static final String QUERY_START_SEPARATOR = "?";
    private static final String MASK_VALUE = "****";

    public static String getSanitizedUriWithQueryParams(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        if (StringUtils.isBlank(queryString)) {
            return uri;
        }
        if (getExcludedPaths().stream().anyMatch(uri::contains)) {
            return uri;
        }
        StringBuilder sanitizedUriWithQueryParams = buildSanitizedQueryString(uri, queryString);
        return sanitizedUriWithQueryParams.toString();
    }

    private static StringBuilder buildSanitizedQueryString(String uri, String queryString) {
        String[] pairs = queryString.split(QUERY_PARAM_SEPARATOR);
        StringBuilder sanitizedQueryString = new StringBuilder(uri);

        sanitizedQueryString.append(QUERY_START_SEPARATOR);

        for (int i = 0; i < pairs.length; i++) {
            String[] keyValue = pairs[i].split(KEY_VALUE_SEPARATOR);
            String key = keyValue[0];
            String value = keyValue.length > 1 ? keyValue[1] : "";
            if (getExcludedParams().contains(key)) {
                value = MASK_VALUE;
            }
            if (i > 0) {
                sanitizedQueryString.append(QUERY_PARAM_SEPARATOR);
            }
            sanitizedQueryString.append(key).append(KEY_VALUE_SEPARATOR).append(value);
        }
        return sanitizedQueryString;
    }

    public static Set<String> getExcludedParams() {
        return ExcludedParamsHelper.excludedParams;
    }

    public static Set<String> getExcludedPaths() {
        return ExcludedPathsHelper.excludedPaths;
    }

    @VisibleForTesting
    static void refreshExcludedParams() {
        ExcludedParamsHelper.refreshParams();
    }

    @VisibleForTesting
    static void refreshExcludedPaths() {
        ExcludedPathsHelper.refreshPaths();
    }

    private static class ExcludedParamsHelper {
        private static final Set<String> excludedParams = new HashSet<>();

        static {
            refreshParams();
        }

        private static void refreshParams() {
            try {
                excludedParams.clear();
                excludedParams.add("token");
                excludedParams.add("access_token");
                excludedParams.add("password");
                excludedParams.add("api_key");
                excludedParams.add("secret");
                excludedParams.add("scope");
                excludedParams.add("sig");
                excludedParams.addAll(requestLogsExcludedQueryParams.getList());
            } catch (Exception e) {
                log.debug("Failed to get params to exclude", e);
            }
        }
    }

    private static class ExcludedPathsHelper {
        private static final Set<String> excludedPaths = new HashSet<>();

        static {
            refreshPaths();
        }

        private static void refreshPaths() {
            try {
                excludedPaths.clear();
                excludedPaths.add("/auth");
                excludedPaths.add("/login");
                excludedPaths.addAll(requestLogsExcludedQueryParamsPaths.getList());
            } catch (Exception e) {
                log.debug("Failed to get params to exclude", e);
            }
        }
    }
}