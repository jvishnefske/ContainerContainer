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

import jakarta.annotation.Nonnull;
import org.jfrog.common.config.diff.DiffUtils;

import java.util.Objects;

/**
 * @author Noam Shemesh
 */
public enum CentralConfigKey {
    none("-"), // Listen to none to prevent reloads
    proxies("proxies"),
    security("security"),
    securityPasswordSettings("security.passwordSettings"),
    remoteReplications("remoteReplications"),
    localReplications("localReplications"),
    replicationsConfig("replicationsConfig"),
    localRepositoriesMap("localRepositoriesMap"),
    virtualRepositoriesMap("virtualRepositoriesMap"),
    federatedRepositoriesMap("federatedRepositoriesMap"),
    remoteRepositoriesMap("remoteRepositoriesMap"),
    releaseBundlesRepositoriesMap("releaseBundlesRepositoriesMap"),
    singleReplicationPerRepoMap("singleReplicationPerRepoMap"),
    folderDownloadConfig("folderDownloadConfig"),
    propertySets("propertySets"),
    systemProperties("systemProperties"),
    backups("backups"),
    keyPairs("keyPairs"),
    indexer("indexer"),
    sumoLogicConfig("sumoLogicConfig"),
    urlBase("urlBase"),
    serverName("serverName"),
    cleanupConfig("cleanupConfig"),
    virtualCacheCleanupConfig("virtualCacheCleanupConfig"),
    gcConfig("gcConfig"),
    offlineMode("offlineMode"),
    xrayConfig("xrayConfig"),
    retentionSettings("retentionSettings"),
    repoLayouts("repoLayouts"),
    authentication("authentication");

    private final String key;

    CentralConfigKey(@Nonnull String key) {
        this.key = Objects.requireNonNull(key, "Key must not be null").replaceAll("[.]", DiffUtils.DELIMITER);
    }

    public String getKey() {
        return key;
    }
}
