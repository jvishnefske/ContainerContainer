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

package org.artifactory.webapp.servlet.authentication.interceptor.anonymous;

import org.artifactory.util.HttpUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * Allows anonymous to login if anonymous access is disabled (RTFACT-7073)
 *
 * @author Dan Feldman
 */
public class AnonymousLoginInterceptor implements AnonymousAuthenticationInterceptor {

    private Pattern pattern;

    @Override
    public boolean accept(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        return getPattern().matcher(requestURI).matches();
    }

    private Pattern getPattern() {
        if (pattern == null) {
            String context = HttpUtils.getOverrideContextPath();
            if (!"/artifactory".equals(context)) {
                context = "(" + context + "|/artifactory)";
            }
            pattern = Pattern.compile("^" + context + "/(api|ui)/(auth|oauth2)/.+");
        }

        return pattern;
    }
}