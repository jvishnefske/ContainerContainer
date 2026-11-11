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

package org.artifactory.config;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.async.Async;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.spring.ReloadableBean;
import org.jfrog.common.platform.StringBasedEnum;

/**
 * @author freds
 */
public interface InternalCentralConfigService extends CentralConfigService, ReloadableBean {

    void markCacheAsDirty();

    /**
     * Calls context.reload() after changes were made to the descriptor either by saving it (current node) or
     * receiving an HA message that it was changed.
     */
    @Async
    void callReload(CentralConfigDescriptor oldDescriptor);

    /**
     * The current properties (taken from artifactory.system.properties) are loaded by {@link ConstantValues} and
     * will be migrated to reloadable properties.
     * Currently, if this property is not presented in config descriptor, we do a fallback to check in the
     * {@link ConstantValues}.
     *
     * @param enumType      - The enum type that need to be returned
     * @param constantValue - The constant value name to search
     * @return the Enum itself if found.
     */
    <T extends Enum<T> & StringBasedEnum> T getReloadablePropertyByEnum(Class<T> enumType,
            ConstantValues constantValue);
}
