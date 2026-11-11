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

package org.artifactory.rest.common.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.UriInfo;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.RepoPath;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.UiRequestUtils;

import java.util.Collections;
import java.util.List;

/**
 * @author Chen Keinan
 */
@Getter
@Setter
public class ArtifactoryRestRequest<T> {

    private HttpServletRequest servletRequest;
    private Request request;
    private UriInfo uriInfo;
    private HttpHeaders httpHeaders;
    private T imodel;
    private List<T> models;
    private boolean uiRestCall;

    public ArtifactoryRestRequest(RequestBuilder<T> requestBuilder) {
        this.servletRequest = requestBuilder.servletRequest;
        this.request = requestBuilder.request;
        this.uriInfo = requestBuilder.uriInfo;
        this.httpHeaders = requestBuilder.httpHeaders;
        imodel = requestBuilder.imodel;
        models = requestBuilder.imodels;
        this.uiRestCall = UiRequestUtils.isUiRestRequest(servletRequest);
    }

    /**
     * return path param by key
     *
     * @param key - path param key
     * @return path param value
     */
    public String getPathParamByKey(String key) {
        return getPathParamByKeyInternal(uriInfo.getPathParameters().get(key));
    }

    String getPathParamByKeyInternal(List<String> valueList) {
        if (valueList != null && !valueList.isEmpty()) {
            return StringUtils.removeStart(valueList.getFirst(), "/");
        }
        return "";
    }

    /**
     * return query param by key
     *
     * @param key - query param key
     * @return query param value
     */
    public String getQueryParamByKey(String key) {
        return getQueryParamByKeyInternal(uriInfo.getQueryParameters().get(key));
    }

    String getQueryParamByKeyInternal(List<String> valueList) {
        String value = "";
        if (valueList != null && !valueList.isEmpty()) {
            value = valueList.getFirst();
        }
        return value;
    }

    public List<String> getQueryParamListByKey(String key) {
        return getQueryParamListByKeyInternal(uriInfo.getQueryParameters().get(key));
    }

    List<String> getQueryParamListByKeyInternal(List<String> valueList) {
        if (CollectionUtils.isNullOrEmpty(valueList)) {
            return Collections.emptyList();
        }
        return valueList;
    }

    public String getDownloadLink(RepoPath repoPath) {
        if (repoPath != null) {
            return HttpUtils.getServletContextUrl(servletRequest) + "/" + repoPath.getRepoKey() + "/" +
                    repoPath.getPath();
        } else {
            return "";
        }
    }

    public static class RequestBuilder<T> {
        HttpServletRequest servletRequest;
        Request request;
        UriInfo uriInfo;
        HttpHeaders httpHeaders;
        private T imodel;
        private String id;
        private List<T> imodels;


        public RequestBuilder(HttpServletRequest servletRequest, Request request, UriInfo uriInfo,
                HttpHeaders httpHeaders) {
            this.servletRequest = servletRequest;
            this.request = request;
            this.uriInfo = uriInfo;
            this.httpHeaders = httpHeaders;
        }

        public RequestBuilder model(T imodel) {
            this.imodel = imodel;
            return this;
        }

        public RequestBuilder models(List<T> iModels) {
            this.imodels = iModels;
            return this;
        }
    }
}
