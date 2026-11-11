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

package org.artifactory.addon.opkg;


import org.artifactory.addon.dpkgcommon.DpkgCalculationEvent;

import jakarta.annotation.Nullable;

/**
 * @author Noam Y. Tenne
 * @author Dan Feldman
 */
public class OpkgCalculationEvent extends DpkgCalculationEvent implements Comparable<OpkgCalculationEvent> {

    private final String path;
    protected long timestamp;

    OpkgCalculationEvent(String repoKey, String path, @Nullable String passphrase) {
        super(repoKey, passphrase);
        this.timestamp = System.currentTimeMillis();
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public int compareTo(OpkgCalculationEvent o) {
        int i = repoKey.compareTo(o.repoKey);
        if (i != 0) {
            return i;
        }
        if (path != null) {
            i = path.compareTo(o.path);
            if (i != 0) {
                return i;
            }
        }
        return Long.compare(timestamp, o.timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OpkgCalculationEvent)) {
            return false;
        }
        OpkgCalculationEvent that = (OpkgCalculationEvent) o;
        if (!super.equals(o)) {
            return false;
        }
        return !(path != null ? !path.equals(that.getPath()) : that.getPath() != null);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (path != null ? path.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "OpkgCalculationEvent{" +
                "repoKey='" + repoKey + '\'' +
                "path='" + path + '\'' +
                "timestamp='" + timestamp + '\'' +
                '}';
    }
}
