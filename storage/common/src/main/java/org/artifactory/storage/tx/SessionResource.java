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

package org.artifactory.storage.tx;

import org.springframework.transaction.support.TransactionSynchronization;

/**
 * @author freds
 */
public interface SessionResource {

    /**
     * Called after the storage session saved the mutable items and before the transaction has been committed.
     * The database transaction is still active at this point. Any change to the database here will participate in the
     * active transaction.
     *
     * @see TransactionSynchronization#beforeCommit(boolean)
     */
    void beforeCommit();

    /**
     * Called after the physical JDBC connection was returned to the pool (after JDBC transaction manager cleanup).
     *
     * @param committed whether the transaction committed ({@code false} for rollback)
     */
    default void afterConnectionReleased(boolean committed) {
        // default no-op
    }
}
