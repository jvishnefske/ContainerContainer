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

package org.artifactory.ui.rest.service.admin.advanced.maintenance;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.cleanup.CleanupConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.gc.GcConfigDescriptor;
import org.artifactory.descriptor.quota.QuotaConfigDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.advanced.maintenance.Maintenance;
import org.artifactory.ui.rest.service.state.GlobalStateProviderService;
import org.artifactory.util.AolUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Chen Keinan
 */
@Component
public class SaveMaintenanceService implements RestService {

    @Autowired
    CentralConfigService centralConfigService;

    @Autowired
    GlobalStateProviderService globalStateProviderService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        AolUtils.assertNotAol("SaveMaintenance");
        Maintenance maintenance = (Maintenance) request.getImodel();
        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        List<Descriptor> descriptorList = new ArrayList<>();

        descriptorList.add(updateGarbageCollection(maintenance, mutableDescriptor));
        // update quota
        descriptorList.add(updateQuotaConfig(maintenance, mutableDescriptor));
        // update cleanup
        descriptorList.add(updateCleanUpConfig(maintenance, mutableDescriptor));
        // update virtual repo
        descriptorList.add(updateVirtualRepoCleanUp(maintenance, mutableDescriptor));
        centralConfigService.saveEditedDescriptorAndReload(mutableDescriptor, descriptorList);
        // Invalidate the cache as the quota settings may have changed
        globalStateProviderService.invalidateCache();
        response.info("Maintenance settings were successfully saved.");
    }

    /**
     * update virtual repo clean up
     *
     * @param maintenance       -- maintenance model
     * @param mutableDescriptor config descriptor
     */
    private CleanupConfigDescriptor updateVirtualRepoCleanUp(Maintenance maintenance,
            MutableCentralConfigDescriptor mutableDescriptor) {
        CleanupConfigDescriptor virtualCacheCleanupConfig = mutableDescriptor.getVirtualCacheCleanupConfig();
        if (virtualCacheCleanupConfig == null) {
            virtualCacheCleanupConfig = new CleanupConfigDescriptor();
        }
        virtualCacheCleanupConfig.setCronExp(maintenance.getCleanVirtualRepoCron());
        mutableDescriptor.setVirtualCacheCleanupConfig(virtualCacheCleanupConfig);
        return virtualCacheCleanupConfig;
    }

    /**
     * update clean up config
     *
     * @param maintenance       - maintenance model
     * @param mutableDescriptor - config descriptor
     */
    private CleanupConfigDescriptor updateCleanUpConfig(Maintenance maintenance,
            MutableCentralConfigDescriptor mutableDescriptor) {
        CleanupConfigDescriptor cleanupConfig = mutableDescriptor.getCleanupConfig();
        if (cleanupConfig == null) {
            cleanupConfig = new CleanupConfigDescriptor();
        }
        cleanupConfig.setCronExp(maintenance.getCleanUnusedCachedCron());
        mutableDescriptor.setCleanupConfig(cleanupConfig);
        return cleanupConfig;
    }

    /**
     * update quota config data
     *
     * @param maintenance       - maintenance model
     * @param mutableDescriptor - config descriptor
     */
    private QuotaConfigDescriptor updateQuotaConfig(Maintenance maintenance,
            MutableCentralConfigDescriptor mutableDescriptor) {
        QuotaConfigDescriptor quotaConfig = mutableDescriptor.getQuotaConfig();
        if (quotaConfig == null) {
            quotaConfig = new QuotaConfigDescriptor();
        }
        quotaConfig.setEnabled(maintenance.isQuotaControl());
        quotaConfig.setDiskSpaceLimitPercentage(maintenance.getStorageLimit());
        quotaConfig.setDiskSpaceWarningPercentage(maintenance.getStorageWarning());
        mutableDescriptor.setQuotaConfig(quotaConfig);
        return quotaConfig;
    }

    /**
     * update garbage collection config
     *
     * @param maintenance       -- maintenance model
     * @param mutableDescriptor - config descriptor
     */
    private GcConfigDescriptor updateGarbageCollection(Maintenance maintenance,
            MutableCentralConfigDescriptor mutableDescriptor) {
        GcConfigDescriptor gcConfig = mutableDescriptor.getGcConfig();
        if (gcConfig == null) {
            gcConfig = new GcConfigDescriptor();
        }
        gcConfig.setCronExp(maintenance.getGarbageCollectorCron());
        return gcConfig;
    }
}
