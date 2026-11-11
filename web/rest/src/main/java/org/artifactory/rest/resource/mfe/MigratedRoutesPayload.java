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

package org.artifactory.rest.resource.mfe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Deserializes target MFE {@code migrated-routes.json} payloads (ownership and optional rollout-phase paths).
 *
 * @author michaelku
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MigratedRoutesPayload {

    private List<String> routesList = List.of();
    private List<String> rolloutPhaseList = List.of();

    public List<String> getRoutesList() {
        return routesList;
    }

    @JsonProperty("routesList")
    public void setRoutesList(List<String> routesList) {
        this.routesList = routesList != null ? List.copyOf(routesList) : List.of();
    }

    /**
     * Routes still served by core when controlled rollout for this MFE is disabled.
     */
    public List<String> getRolloutPhaseList() {
        return rolloutPhaseList;
    }

    @JsonProperty("rolloutPhaseList")
    public void setRolloutPhaseList(List<String> rolloutPhaseList) {
        this.rolloutPhaseList = rolloutPhaseList != null ? List.copyOf(rolloutPhaseList) : List.of();
    }

    /**
     * When rollout is enabled, core unloads the full ownership list. When disabled, rollout-phase routes stay on core.
     * Entries in {@code rolloutPhaseList} that are not in {@code routesList} are ignored.
     */
    public static List<String> computeUnloadFromCore(
            List<String> routesList,
            List<String> rolloutPhaseList,
            boolean rolloutEnabled) {
        LinkedHashSet<String> unload = new LinkedHashSet<>(routesList);
        if (!rolloutEnabled) {
            for (String path : rolloutPhaseList) {
                unload.remove(path);
            }
        }
        return List.copyOf(unload);
    }
}
