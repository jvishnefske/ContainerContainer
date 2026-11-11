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

package org.artifactory.rest.exception;

import lombok.Getter;
import org.apache.http.HttpStatus;

import java.util.Map;

/**
 * Represents a REST API error response other than 400 and 404.
 *
 * @author Shay Yaakov
 */
@Getter
public class RestException extends RuntimeException {

    private final int statusCode;
    private final Map<String, String> headers;

    public RestException(String message) {
        this(HttpStatus.SC_INTERNAL_SERVER_ERROR, message);
    }

    public RestException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        this.headers = null;
    }

    public RestException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.headers = null;
    }

    public RestException(int statusCode, String message, Map<String, String> headers) {
        super(message);
        this.statusCode = statusCode;
        this.headers = headers;
    }
}
