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

package org.artifactory.federation;

import java.sql.SQLException;
import java.util.List;

/**
 * DAO interface for dangling-ref cleanup work that outlived JFBus retry
 * budget. Rows are inserted when the JFBus consumer exhausts its retry
 * counter or when JFBus publish fails; removed when cleanup succeeds or
 * the ref is already gone.
 */
public interface DeferredDanglingRefCleanupDao {

    void upsert(String virtualRepoKey, String childRepoKey)
            throws SQLException;

    void delete(String virtualRepoKey, String childRepoKey)
            throws SQLException;

    List<DeferredDanglingRefEntry> findAll() throws SQLException;

    record DeferredDanglingRefEntry(
            String virtualRepoKey, String childRepoKey) {
    }
}
