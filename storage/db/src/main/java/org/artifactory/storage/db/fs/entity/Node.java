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

package org.artifactory.storage.db.fs.entity;

import lombok.Builder;
import org.artifactory.threadlocal.cache.RepositoryNameProvider;

import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.artifactory.storage.db.security.service.access.UserMapper.MAX_ALLOWED_USERNAME_LENGTH;

/**
 * Represents a node in the nodes table.
 *
 * @author Yossi Shaul
 */
@Builder
public class Node implements RepositoryNameProvider {

    private final long nodeId;
    private final boolean file;
    private final String repo;
    private final String path;
    private final String name;
    private final short depth;
    private final long created;
    private final String createdBy;
    private final long modified;
    private final String modifiedBy;
    private final long updated;

    // file only attributes
    private final long length;
    private final String sha1Actual;
    private final String sha1Original;
    private final String sha2;
    private final String md5Actual;
    private final String md5Original;

    public Node(long nodeId, boolean file, String repo, String path, String name, short depth, long created,
            String createdBy, long modified, String modifiedBy, long updated, long length, String sha1Actual,
            String sha1Original, String md5Actual, String md5Original, String sha2) {
        this.nodeId = nodeId;
        this.file = file;
        this.repo = repo;
        this.path = path;
        this.name = name;
        this.depth = depth;
        this.created = created;
        this.createdBy = abbreviate(createdBy, MAX_ALLOWED_USERNAME_LENGTH);
        this.modified = modified;
        this.modifiedBy = abbreviate(modifiedBy, MAX_ALLOWED_USERNAME_LENGTH);
        this.updated = updated;
        this.length = length;
        this.sha1Actual = sha1Actual;
        this.sha1Original = sha1Original;
        this.sha2 = sha2;
        this.md5Actual = md5Actual;
        this.md5Original = md5Original;
    }

    public long getNodeId() {
        return nodeId;
    }

    public boolean isFile() {
        return file;
    }

    public String getRepo() {
        return repo;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public short getDepth() {
        return depth;
    }

    public long getCreated() {
        return created;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public long getModified() {
        return modified;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public long getUpdated() {
        return updated;
    }

    public long getLength() {
        return length;
    }

    public String getSha1Actual() {
        return sha1Actual;
    }

    public String getSha1Original() {
        return sha1Original;
    }

    public String getSha2() {
        return sha2;
    }

    public String getMd5Actual() {
        return md5Actual;
    }

    public String getMd5Original() {
        return md5Original;
    }

    public NodePath getNodePath() {
        return new NodePath(repo, path, name, file);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Node node = (Node) o;

        if (!name.equals(node.name)) {
            return false;
        }
        if (!path.equals(node.path)) {
            return false;
        }
        return repo.equals(node.repo);
    }

    @Override
    public int hashCode() {
        int result = repo.hashCode();
        result = 31 * result + path.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return  "Node" + "{" + asString() + '}';
    }

    public String toStringWithChecksums() {
        return "Node" + "{" + asString() +
                ", sha1Actual='" + sha1Actual + '\'' +
                ", sha2='" + sha2 + '\'' +
                ", md5Actual='" + md5Actual + '\'' + '}';
    }
    private String asString() {
        return "nodeId=" + nodeId +
                ", file=" + file +
                ", repo='" + repo + '\'' +
                ", path='" + path + '\'' +
                ", name='" + name + '\'';
    }
}
