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

package org.artifactory.addon;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.addon.Addon;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.entitlement.EntitlementType;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.addon.ArtifactoryRunningMode.HA;
import static org.artifactory.addon.ArtifactoryRunningMode.PRO;

/**
 * Provides Base class between AddonsManagerImpl and LicenseManagerImpl
 *
 * @author Asaf Zalcman
 */
@Component
public abstract class AddonsManagerBase implements AddonsManager {

    private ImmutableMap<String, AddonInfo> addonPathsByName;
    protected ArtifactoryContext context;
    protected ArtifactoryHome artifactoryHome;

    @Autowired
    protected void setApplicationContext(ApplicationContext context) throws BeansException {
        this.context = (ArtifactoryContext) context;
        artifactoryHome = ((ArtifactoryContext) context).getArtifactoryHome();
        addonPathsByName = this.context.getConfigPaths().getInstalledAddonPaths();
    }

    @Override
    public List<AddonInfo> getInstalledAddons(Set<String> excludedAddonKeys) {
        List<AddonInfo> addonsToReturn = Lists.newArrayList();

        // RTFACT-6380 - we check that HA is licensed
        if (addonPathsByName.containsKey("ha") && !isEntitled(EntitlementType.HA)) {
            addonPathsByName.get("ha").setAddonState(AddonState.NOT_LICENSED);
        }
        if ((excludedAddonKeys == null) || excludedAddonKeys.isEmpty()) {
            addonsToReturn.addAll(addonPathsByName.values());
        } else {
            for (Map.Entry<String, AddonInfo> addon : addonPathsByName.entrySet()) {
                if (!excludedAddonKeys.contains(addon.getKey())) {
                    addonsToReturn.add(addon.getValue());
                }
            }
        }

        Collections.sort(addonsToReturn);
        return addonsToReturn;
    }


    @Override
    public List<String> getEnabledAddonNames() {
        List<String> enabledAddonNames = Lists.newArrayList();
        if (!isAddonsAvailable()) {
            return enabledAddonNames;
        }

        for (AddonInfo addonInfo : addonPathsByName.values()) {
            if (addonInfo.getAddonState().equals(AddonState.ACTIVATED)) {
                enabledAddonNames.add(addonInfo.getAddonName());
            }
        }
        return enabledAddonNames;
    }

    protected abstract boolean isAddonsAvailable();

    @Override
    public boolean isAddonSupported(AddonType addonType) {
        AddonInfo addonInfo = addonPathsByName.get(addonType.getAddonName());
        return addonInfo != null && addonInfo.getAddonState().equals(AddonState.ACTIVATED);
    }

    protected void activateAddons(boolean initializing) {
        // set the state of all the inactive addons to active (disabled are not loaded and cannot be active)
        for (AddonInfo addonInfo : addonPathsByName.values()) {
            if (!AddonState.DISABLED.equals(addonInfo.getAddonState())) {
                if (!"ha".equals(addonInfo.getAddonName())) {
                    addonInfo.setAddonState(AddonState.ACTIVATED);
                } else {
                    updateHaAddonState(addonInfo);
                }
            }
        }
        // This is our workaround to load the addons and configurations (especially, the configuration manager's
        //propagation channel and the db channel)
        // Only if we are not in the initializing process (ArtifactoryStateManager#init) we reload the configuration
        if (!initializing) {
            ContextHelper.get().getCentralConfig().reloadConfiguration();
        }
    }

    private void updateHaAddonState(AddonInfo ha) {
        if (AddonState.DISABLED.equals(ha.getAddonState())) {
            return;
        }
        if (isEntitled(EntitlementType.HA) && getArtifactoryRunningMode().isHa()) {
            ha.setAddonState(AddonState.ACTIVATED);
        } else if (isEntitled(EntitlementType.HA)) {
            ha.setAddonState(AddonState.NOT_CONFIGURED);
        } else {
            ha.setAddonState(AddonState.NOT_LICENSED);
        }
    }

    /**
     * Locates addons by type
     *
     * @param type Class of requested addon type
     * @param <T>  Requested addon factory type
     * @return Map of located addons
     */
    protected <T extends Addon> Map<String, T> locateAddonByType(Class<T> type) {
        Map<String, T> found = context.beansForType(type);

        if (found == null || found.isEmpty()) {
            throw new IllegalStateException("No addon found for type " + type);
        }

        if (found.size() > 2) {
            throw new IllegalStateException(
                    "Found more than two add-on factories for " + type + " : " + found.keySet());
        }

        return found;
    }

    @Override
    public ArtifactoryRunningMode getArtifactoryRunningMode() {
        return artifactoryHome.isHaConfigured() ? HA : PRO;
    }
}
