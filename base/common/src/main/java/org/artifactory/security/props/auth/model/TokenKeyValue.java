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

package org.artifactory.security.props.auth.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * @author Chen Keinan
 */
@Getter
@Setter
public class TokenKeyValue implements Serializable {

    private String token;
    private String key;
    private int expiresIn;
    private long expiresTimeStamp;

    public TokenKeyValue(String token) {
        this(null, token);
    }

    public TokenKeyValue(String key, String token) {
        this(key, token, 3600);
    }

    public TokenKeyValue(String key, String token, int expiresIn) {
        this.token = token;
        this.key = key;
        this.expiresIn = expiresIn;
        expiresTimeStamp = System.currentTimeMillis() + TimeUnit.SECONDS
                .toMillis(expiresIn - 60); // we giving 1 min grace to avoid an expiration in the middle of a flow
    }

    @Override
    public String toString() {
        return String.format("%s=%s", key, token);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        TokenKeyValue that = (TokenKeyValue) o;

        return this.toString().equals(that.toString());
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }
}
