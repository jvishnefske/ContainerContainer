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

package org.artifactory.common.config.db;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.storage.DbProperties;
import org.jfrog.storage.DbType;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author Gidi Shabat
 * @author Dan Feldman
 */
public class ArtifactoryDbProperties extends DbProperties {
    private static final String SERVICE_NAME = "artifactory";
    private static final String ALLOW_NON_PG_FLAG_SHORT = "AllowNonPostgresql";
    public static final String LOCKS_MAX_ACTIVE_CONNECTIONS = "locksMaxActiveConnections";

    static final int DEFAULT_MAX_ACTIVE_CONNECTIONS = 100;
    static final int DEFAULT_MAX_IDLE_CONNECTIONS = 10;

    private final DbType lockingSpecificDbType;
    private ArtifactoryHome home;

    public ArtifactoryDbProperties(ArtifactoryHome home) {
        super(home.getSysConfig(), home.getDefaultDbHome(), home.getDerbyLogFilePath(), SERVICE_NAME);
        this.home = home;
        this.lockingSpecificDbType = populateLockingDbParams();
    }

    @Override
    public int getDefaultMaxActiveConnections() {
        return DEFAULT_MAX_ACTIVE_CONNECTIONS;
    }

    @Override
    public int getDefaultMaxIdleConnections() {
        return DEFAULT_MAX_IDLE_CONNECTIONS;
    }

    public String getLockingDbUsername() {
        return props.get(Key.LOCKING_DB_USERNAME.key());
    }


    public String getLockingDbConnectionUrl() {
        return props.get(Key.LOCKING_DB_URL.key());
    }

    @Override
    public String getPassword() {
        return decryptPassInternal(Key.PASSWORD);
    }

    public String getLockingDbPassword() {
        return decryptPassInternal(Key.LOCKING_DB_PASSWORD);
    }

    public Optional<DbType> getLockingDbSpecificType() {
        return Optional.ofNullable(lockingSpecificDbType);
    }

    public String getLockingDbDriverClass() {
        return getProperty(Key.LOCKING_DB_DRIVER.key());
    }

    public boolean isPostgres() {
        return dbType == DbType.POSTGRESQL;
    }

    //TORE [by dan]: this is going to be the config lib's responsibility keeping it here until we verify.
    private String decryptPassInternal(Key password) {
        String originalPass = getProperty(password.key());
        if (StringUtils.isBlank(originalPass)) {
            return originalPass;
        }
        // decrypt with master
        String decryptedPass = CryptoHelper.decryptWithMasterKeyIfNeeded(home, originalPass);
        if (decryptedPass.equals(originalPass)) {
            // if no decryption occurred, fallback and try with the Artifactory key just in case
            decryptedPass = CryptoHelper.decryptIfNeeded(home, decryptedPass);
        }
        return decryptedPass;
    }

    private DbType populateLockingDbParams() {
        assertLockingParamsIfNeeded();
        if (StringUtils.isNotBlank(getProperty(Key.LOCKING_DB_TYPE))) {
            return DbType.parse(getProperty(Key.LOCKING_DB_TYPE));
        } else {
            return null;
        }
    }

    private void assertLockingParamsIfNeeded() {
        if (isNotBlank(getProperty(Key.LOCKING_DB_TYPE)) ||
                isNotBlank(getProperty(Key.LOCKING_DB_URL)) ||
                isNotBlank(getProperty(Key.LOCKING_DB_DRIVER))) {
            assertMandatoryProperties(ImmutableList.of(Key.LOCKING_DB_TYPE.key(), Key.LOCKING_DB_URL.key(),
                    Key.LOCKING_DB_DRIVER.key()));
        }
    }

    public Optional<Boolean> allowNonPgFlag() {
        //in case if AllowNonPostgresql passed by env variable JF_SHARED_DATABASE_ALLOWNONPOSTGRESQL
        final Optional<String> firstTry = Optional.ofNullable(this.getProperty(ALLOW_NON_PG_FLAG_SHORT));
        return firstTry
                .map(Boolean::parseBoolean)
                .or(
                        () -> Optional.of(Boolean.parseBoolean(this.getProperty(Key.ALLOW_NON_POSTGRESQL)))
                );
    }
}

