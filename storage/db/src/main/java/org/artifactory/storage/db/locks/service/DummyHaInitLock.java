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

package org.artifactory.storage.db.locks.service;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

/**
 * No-op HA init lock implementation for non-HA setups.
 *
 * @author Mor Merhav
 */
@Slf4j
public class DummyHaInitLock extends HaInitLock {

    public DummyHaInitLock() {
        super(new DummyDbLock(), "serverID", () -> false);
    }

    @Override
    public void release() {
        // No-op
    }

    @Override
    public boolean acquire() {
        return true;
    }

    @Override
    public boolean isLocked() {
        return false;
    }

    @VisibleForTesting
    static class DummyDbLock implements DbLock {
        @Override
        public String getLockKey() {
            return "dummy_lock";
        }

        @Override
        public boolean isLockedByMe() {
            return true;
        }

        @Override
        public boolean acquire() {
            return true;
        }

        @Override
        public void release() {
            // No-op
        }

        @Override
        public void releaseStale() {
            // No-op
        }
    }
}