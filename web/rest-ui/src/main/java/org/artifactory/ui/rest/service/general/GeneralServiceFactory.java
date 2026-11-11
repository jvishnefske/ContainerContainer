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

package org.artifactory.ui.rest.service.general;

import org.artifactory.ui.rest.service.admin.configuration.general.GetUploadLogoService;
import org.artifactory.ui.rest.service.subscriptions.GetBlockedUsages;
import org.artifactory.ui.rest.service.subscriptions.GetUpgradeInfoService;
import org.springframework.beans.factory.annotation.Lookup;

/**
 * @author Chen Keinan
 */
public abstract class GeneralServiceFactory {

    @Lookup
    public abstract GetFooterService getFooterService();

    @Lookup
    public abstract GetGlobalStateService getGlobalStateService();

    @Lookup
    public abstract GetBasicConfigService getBasicConfigService();

    @Lookup
    public abstract GetUpgradeInfoService getUpgradeInfoService();

    @Lookup
    public abstract GetBlockedUsages getBlockedUsages();

    @Lookup
    public abstract GetUploadLogoService getUploadLogo();

    @Lookup
    public abstract GetMetadataMigrationStatusService getMetadataMigrationStatus();
}