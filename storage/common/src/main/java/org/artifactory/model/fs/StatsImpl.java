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

package org.artifactory.model.fs;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.artifactory.fs.MutableStatsInfo;
import org.artifactory.fs.StatsInfo;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * @author Yoav Landman
 */
@Getter
@Setter
@XmlRootElement(name = StatsImpl.ROOT)
@XmlAccessorType(XmlAccessType.FIELD)
@NoArgsConstructor
public class StatsImpl implements MutableStatsInfo {

    private long downloadCount;
    private long lastDownloaded;
    private String lastDownloadedBy;

    private long remoteDownloadCount;
    private long remoteLastDownloaded;
    private String remoteLastDownloadedBy;

    private String repoPath;
    private String path;
    private String origin;

    public StatsImpl(StatsInfo statsInfo) {
        if (statsInfo == null) {return;}

        this.downloadCount = statsInfo.getDownloadCount();
        this.lastDownloaded = statsInfo.getLastDownloaded();
        this.lastDownloadedBy = statsInfo.getLastDownloadedBy();

        this.remoteDownloadCount = statsInfo.getRemoteDownloadCount();
        this.remoteLastDownloaded = statsInfo.getRemoteLastDownloaded();
        this.remoteLastDownloadedBy = statsInfo.getRemoteLastDownloadedBy();

        this.path = statsInfo.getPath();
        this.origin = statsInfo.getOrigin();
    }

    public void merge(StatsInfo other) {
        if (other == null) {
            return;
        }
        this.downloadCount += other.getDownloadCount();
        if (other.getLastDownloaded() > this.lastDownloaded) {
            this.lastDownloaded = other.getLastDownloaded();
            this.lastDownloadedBy = other.getLastDownloadedBy();
        }

        this.remoteDownloadCount += other.getRemoteDownloadCount();
        if (other.getRemoteLastDownloaded() > this.remoteLastDownloaded) {
            this.remoteLastDownloaded = other.getRemoteLastDownloaded();
            this.remoteLastDownloadedBy = other.getRemoteLastDownloadedBy();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        StatsImpl stats = (StatsImpl) o;

        if (downloadCount != stats.downloadCount) {
            return false;
        }
        if (lastDownloaded != stats.lastDownloaded) {
            return false;
        }
        if (lastDownloadedBy != null ? !lastDownloadedBy.equals(stats.lastDownloadedBy) :
                stats.lastDownloadedBy != null) {
            return false;
        }
        if (remoteDownloadCount != stats.remoteDownloadCount) {
            return false;
        }
        if (remoteLastDownloaded != stats.remoteLastDownloaded) {
            return false;
        }
        if (remoteLastDownloadedBy != null ? !remoteLastDownloadedBy.equals(stats.remoteLastDownloadedBy) :
                stats.remoteLastDownloadedBy != null) {
            return false;
        }
        if (repoPath != null ? !repoPath.equals(stats.repoPath) :
                stats.repoPath != null) {
            return false;
        }
        return path != null ? path.equals(stats.path) : stats.path == null;
    }

    @Override
    public int hashCode() {
        int result = (int) (downloadCount ^ (downloadCount >>> 32));
        result = 31 * result + (int) (lastDownloaded ^ (lastDownloaded >>> 32));
        result = 31 * result + (lastDownloadedBy != null ? lastDownloadedBy.hashCode() : 0);
        result = 31 * result + (int) (remoteDownloadCount ^ (remoteDownloadCount >>> 32));
        result = 31 * result + (int) (remoteLastDownloaded ^ (remoteLastDownloaded >>> 32));
        result = 31 * result + (remoteLastDownloadedBy != null ? remoteLastDownloadedBy.hashCode() : 0);
        result = 31 * result + (repoPath != null ? repoPath.hashCode() : 0);
        result = 31 * result + (path != null ? path.hashCode() : 0);
        return result;
    }
}