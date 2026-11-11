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

package org.artifactory.sapi.security;

/**
 * Date: 7/27/11
 * Time: 5:04 PM
 *
 * @author Fred Simon
 */
public interface SecurityConstants {
    String DEFAULT_REALM = "internal";
    String DEFAULT_REALM_DB = "internal-db";

    /**
     * Single source of truth for Artifactory's remember-me cookie name - do not confuse with Spring's default
     * {@code remember-me} (RTDEV-94514).
     */
    String REMEMBER_ME_COOKIE_NAME = "SPRING_SECURITY_REMEMBER_ME_COOKIE";
}
