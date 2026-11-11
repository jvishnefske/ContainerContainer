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

package org.artifactory.rest.common.exception.mapper;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.rest.ErrorResponse;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

/**
 * Intercepts ${@link org.artifactory.exception.CancelException} and
 * {@link com.jfrog.ph.acl.exception.PackageCancelException} and returns JSON error response.
 * All the api's packages downloads that ran into a
 * CancelException or PackageCancelException through a user plugin will be mapped to a JSON error using this mapper,
 * See RTFACT-8257.
 */
@Slf4j
public abstract class CancelExceptionMapperBase<T extends Exception> implements ExceptionMapper<T> {
    HttpServletRequest request;

    @Context
    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public Response toResponse(T e) {
        String requestPath = new UrlPathHelper().getPathWithinApplication(request);
        String message = buildResponseMessage(requestPath, e);
        writeToLog(message, e);
        ErrorResponse errorResponse = new ErrorResponse(getErrorCode(e), message);
        return Response.status(getErrorCode(e)).type(MediaType.APPLICATION_JSON).entity(errorResponse).build();
    }

    private String buildResponseMessage(String requestPath, T e) {
        String message;
        //basically, expect api's downloads (nuget, npm etc..) and the plugin execute, the other paths are already
        //handles the exceptions,
        // but I've added these anyway to handle possible future change
        if (requestPath.startsWith("/api/plugins/execute/") || requestPath.startsWith("/api/plugins/build/promote/") ||
                requestPath.startsWith("/api/storage/") || requestPath.startsWith("/api/copy/") ||
                requestPath.startsWith("/api/move/")) {
            message = "Request has been canceled";
        } else {
            message = "Download request has been canceled";
        }
        return message + (e.getMessage() != null ? ": " + e.getMessage() : ".");
    }

    private void writeToLog(String message, T e) {
        log.info(message);
        if (log.isDebugEnabled()) {
            log.debug(message, e);
        }
    }

    abstract int getErrorCode(T exception);

}
