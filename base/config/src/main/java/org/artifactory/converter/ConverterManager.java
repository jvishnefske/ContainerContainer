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

package org.artifactory.converter;


import org.artifactory.common.property.ArtifactoryConverter;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.JdbcHelper;

/**
 * @author Gidi Shabat
 */
public interface ConverterManager {

    void convertHomeSync();

    void convertHome();

    void convertPreInit();

    void convertMarkers();

    void serviceConvert(ArtifactoryConverter artifactoryConverter);

    void convertPostInit();

    void convertDatabase();

    /**
     * Triggers async database conversion.
     */
    default void convertAsync() {
    }

    void afterContextReady();

    boolean isConverting();

    void assertPreInitNoConversionBlock();

    void assertPostInitNoConversionBlock();

    void setJdbcHelper(JdbcHelper jdbcHelper);

    /**
     * Create and set LockingDBConverterAdapter if needed (when external locking DB)
     * to convert external locking DB
     */
    void setLockingDBConverters(JFrogDataSource jFrogDataSource);
}