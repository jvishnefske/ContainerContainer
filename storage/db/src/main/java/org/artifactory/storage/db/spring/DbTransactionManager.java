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

package org.artifactory.storage.db.spring;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.fs.session.StorageSession;
import org.artifactory.storage.fs.session.StorageSessionFactory;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

/**
 * @author Yossi Shaul
 */
@Slf4j
@Component("artifactoryTransactionManager")
public class DbTransactionManager extends DataSourceTransactionManager {

    private final StorageSessionFactory sessionFactory;

    @Autowired
    public DbTransactionManager(@Qualifier("dataSource") DataSource dataSource, StorageSessionFactory sessionFactory) {
        super(dataSource);
        this.sessionFactory = sessionFactory;
    }

    @Override
    protected void prepareSynchronization(DefaultTransactionStatus status, TransactionDefinition definition) {
        super.prepareSynchronization(status, definition);
        if (status.isNewSynchronization() &&
                definition.getPropagationBehavior() != TransactionDefinition.PROPAGATION_NOT_SUPPORTED) {
            StorageSession session = sessionFactory.create();
            TransactionSynchronizationManager.registerSynchronization(
                    new SessionSynchronization(session, definition.getName()));
        }
    }

    @Override
    protected void doCleanupAfterCompletion(@NonNull Object transaction) {
        StorageSession session = StorageSessionHolder.getSession();
        // Swallow throws below: Spring's cleanupAfterCompletion does not guard the call to resume() of a
        // suspended outer TX against doCleanupAfterCompletion throwing — escaping here leaks the connection
        // and corrupts StorageSessionHolder. Restores the swallow-and-log behavior of the pre-PR path
        // through TransactionSynchronizationUtils.invokeAfterCompletion.
        if (session != null) {
            try {
                session.releaseResources();
            } catch (RuntimeException e) {
                log.error("Error releasing StorageSession resources during cleanup; continuing", e);
            }
        }
        try {
            superDoCleanupAfterCompletion(transaction);
            if (session != null) {
                try {
                    session.afterConnectionReleased();
                } catch (RuntimeException e) {
                    log.error("Error in StorageSession.afterConnectionReleased during cleanup; continuing", e);
                }
            }
        } finally {
            if (session == null) {
                log.warn("No StorageSession in StorageSessionHolder for this transaction cleanup; transaction={}",
                        transaction);
            }
            StorageSessionHolder.removeSession();
        }
    }

    protected void superDoCleanupAfterCompletion(Object transaction) {
        super.doCleanupAfterCompletion(transaction);
    }
}
