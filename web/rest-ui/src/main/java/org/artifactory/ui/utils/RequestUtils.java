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

package org.artifactory.ui.utils;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.search.SavedSearchResults;
import org.artifactory.common.ConstantValues;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Chen keinan
 */
public class RequestUtils {

    public static final String PROJECT_KEY_PARAM = ProjectsService.PROJECT_KEY;
    public static final String PROJECT_VALUE_ALL_PARAM = "all";
    public static final String FED_ATTR_OPT_IN_PARAM = "isFedAttrSupported";
    private static final String REPO_KEY_PARAM = "repoKey";
    private static final String PATH_PARAM = "path";
    public static final String PASSWORD_PLACEHOLDER = "********************";

    /**
     * return request headers map
     *
     * @param request - http servlet request
     * @return - map of request headers
     */
    public static Map<String, String> getHeadersMap(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        if (request != null) {
            Enumeration headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = (String) headerNames.nextElement();
                map.put(headerName.toUpperCase(), request.getHeader(headerName));
            }
        }
        return map;
    }

    public static RepoPath getPathFromRequest(ArtifactoryRestRequest request) {
        return InternalRepoPathFactory.create(request.getQueryParamByKey(REPO_KEY_PARAM),
                request.getQueryParamByKey(PATH_PARAM));
    }

    public static String getRepoKeyFromRequest(ArtifactoryRestRequest request) {
        return request.getQueryParamByKey(REPO_KEY_PARAM);
    }

    @NonNull
    public static String getProjectKeyFromRequest(ArtifactoryRestRequest request) {
        return Optional.ofNullable(request.getQueryParamByKey(PROJECT_KEY_PARAM))
                .filter(StringUtils::isNotEmpty)
                .orElse(ProjectsService.DEFAULT_PROJECT);
    }

    public static boolean isFedAttrOptIn(ArtifactoryRestRequest<?> request) {
        return ConstantValues.repositoriesFederationsAttributeEnabled.getBoolean() &&
                "true".equalsIgnoreCase(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM));
    }

    public static void setResultsToRequest(SavedSearchResults savedSearchResults, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            session = request.getSession(true);
        }
        session.setAttribute(savedSearchResults.getName(), savedSearchResults);
    }

    /**
     * @param name    - save search result
     * @param request - http servlet request
     * @return - search result
     */
    public static SavedSearchResults getResultsFromRequest(String name, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return (SavedSearchResults) session.getAttribute(name);
    }

    /**
     * @param name    - save search result
     * @param request - http servlet request
     * @return - search result
     */
    public static void removeResultsToRequest(String name, HttpServletRequest request) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new Exception("no search result to remove");
        }
        session.removeAttribute(name);
    }
}
