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

package org.artifactory.storage.db.dao.locks;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jfrog.common.ArgUtils;

import java.util.Objects;

/**
 * @author gidis
 */
@Getter
public class LockInfo {
    private final String category;
    private final String key;
    private final String owner;
    private final long threadId;
    @Setter
    private long startedTime;
    private final String threadName;

    public LockInfo(String category, String key, String owner, long threadId, String threadName, long startedTime) {
        this.category = ArgUtils.requireMaxLength(category, 128,
                "Category " + category + " exceeds maximum allowed length of 128.");
        this.key = key;
        this.owner = ArgUtils.requireMaxLength(owner, 64,
                "Owner " + owner + " exceeds maximum allowed length of 64.");
        this.threadId = threadId;
        //the threadName value will be mapped into the db field: owner_thread_name (max of 64)
        this.threadName = StringUtils.right(threadName, 64);
        this.startedTime = startedTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LockInfo lockInfo = (LockInfo) o;
        if (threadId != lockInfo.threadId) {
            return false;
        }
        if (startedTime != lockInfo.startedTime) {
            return false;
        }
        if (!Objects.equals(category, lockInfo.category)) {
            return false;
        }
        if (!Objects.equals(key, lockInfo.key)) {
            return false;
        }
        if (!Objects.equals(owner, lockInfo.owner)) {
            return false;
        }
        return Objects.equals(threadName, lockInfo.threadName);
    }

    @Override
    public int hashCode() {
        int result = category != null ? category.hashCode() : 0;
        result = 31 * result + (key != null ? key.hashCode() : 0);
        result = 31 * result + (owner != null ? owner.hashCode() : 0);
        result = 31 * result + Long.hashCode(threadId);
        result = 31 * result + Long.hashCode(startedTime);
        result = 31 * result + (threadName != null ? threadName.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "LockInfo{" +
                "category='" + category + '\'' +
                ", key='" + key + '\'' +
                ", owner='" + owner + '\'' +
                ", threadId=" + threadId +
                ", startedTime=" + startedTime +
                ", threadName='" + threadName + '\'' +
                '}';
    }
}
