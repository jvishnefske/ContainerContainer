/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.request;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.request.ArtifactoryResponse;

import java.io.IOException;
import java.util.IllegalFormatException;
import java.util.Set;

/**
 * @author Noam Y. Tenne
 */
@Slf4j
public abstract class RepoRequests {

    private static ThreadLocal<RepoRequestContext> context = new ThreadLocal<>();

    private RepoRequests() {
    }

    public static void set(String methodName, String username, ArtifactoryRequest artifactoryRequest,
            ArtifactoryResponse artifactoryResponse) {
        context.set(RepoRequestContext.create(methodName, username, artifactoryRequest, artifactoryResponse));
    }

    public static void destroy() throws IOException {
        try {
            RepoRequestContext repoRequestContext = context.get();
            if (repoRequestContext != null) {
                repoRequestContext.destroy();
            }
        } finally {
            context.remove();
        }
    }

    /**
     * Logs a message to the current request context. Accepts a {@link String#format(String, Object...)}-style
     * {@code format} and {@code params}.
     * <p>
     * The {@code format} argument is treated as a literal string when no {@code params} are
     * supplied. This lets callers safely pass a pre-built message (which may transitively include
     * user-controlled input with literal {@code %} characters, e.g. a URL-decoded path like {@code %2F})
     * without risking an {@link IllegalFormatException} - {@code logToContext(userMessage)} is always safe.
     * <p>
     * For backwards compatibility, when {@code params} are supplied the call still delegates to
     * {@code String.format}; that path is additionally guarded so that a malformed {@code format}/{@code
     * params} pair (e.g. {@code "%d"} with a {@code String} arg) cannot bubble up as HTTP 500 - the raw
     * {@code format} string is logged instead.
     */
    public static void logToContext(String format, Object... params) {
        RepoRequestContext repoRequestContext = context.get();
        if (repoRequestContext == null) {
            return;
        }
        if (params == null || params.length == 0) {
            repoRequestContext.log(format);
            return;
        }
        String formattedMessage;
        try {
            formattedMessage = String.format(format, params);
        } catch (IllegalFormatException e) {
            log.debug("Falling back to raw format - format/params mismatch in logToContext: {}", e.getMessage());
            formattedMessage = format;
        }
        repoRequestContext.log(formattedMessage);
    }

    public static Set<String> getOriginatedHeaders() {
        Set<String> originatedHeaders = Sets.newHashSet();
        RepoRequestContext repoRequestContext = context.get();
        if (repoRequestContext != null) {
            repoRequestContext.appendOriginatedHeaders(originatedHeaders);
        }
        return originatedHeaders;
    }

    /**
     * Use with caution, This might return an empty string when calling internally from a different thread!
     *
     * @return The servlet context url if the thread local is bounded, otherwise returns an empty string
     */
    public static String getServletContextUrl() {
        RepoRequestContext repoRequestContext = context.get();
        if (repoRequestContext != null) {
            return repoRequestContext.getArtifactoryRequest().getServletContextUrl();
        }

        return "";
    }
}
