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

package org.artifactory.addon.replication;

import lombok.Data;
import org.artifactory.addon.replication.event.ReplicationEventType;
import org.artifactory.repo.RepoPath;
import org.jfrog.client.util.PathUtils;

import java.util.Collection;

/**
 * @author Noam Y. Tenne
 */
@Data
public abstract class ReplicationBaseSettings {

    private RepoPath repoPath;
    private Collection<String> includePathPrefixPattern;
    private Collection<String> excludePathPrefixPattern;
    private final boolean deleteExisting;
    private final boolean includeProperties;
    private final boolean checkBinaryExistenceInFilestore;
    private final String url;
    private final int socketTimeoutMillis;
    private Long replicationJobID;
    private ReplicationStrategy replicationStrategy;
    private ReplicationEventType eventType;
    private boolean isFile;

    protected ReplicationBaseSettings(RepoPath repoPath, String includePathPrefixPattern,
            String excludePathPrefixPattern, boolean deleteExisting, boolean includeProperties,
            boolean checkBinaryExistenceInFilestore, String url, int socketTimeoutMillis) {
        this.repoPath = repoPath;
        this.includePathPrefixPattern = PathUtils.includesExcludesPatternToStringList(includePathPrefixPattern);
        this.excludePathPrefixPattern = PathUtils.includesExcludesPatternToStringList(excludePathPrefixPattern);
        this.deleteExisting = deleteExisting;
        this.includeProperties = includeProperties;
        this.checkBinaryExistenceInFilestore = checkBinaryExistenceInFilestore;
        this.url = url;
        this.socketTimeoutMillis = socketTimeoutMillis;
    }

    public void setIsFile(Boolean isFile) {
        // null could be because of old version is replicating, and in old versions (before 7.10.2 in 7.x.x and
        // before 6.23.1 in 6.x.x) we send only file property change events
        this.isFile = isFile == null || isFile;
    }
}
