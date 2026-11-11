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

package org.artifactory.state;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.model.ArtifactoryServerRole;
import org.artifactory.version.CompoundVersionDetails;

/**
 * @author gidis
 * @author Mor Merhav
 */
public final class ArtifactoryServerHelper {

    private ArtifactoryServerHelper() {

    }

    public static ArtifactoryServer createArtifactoryServer(String serverId, String serverContextUrl,
            int clusterPort, CompoundVersionDetails versionDetails, ArtifactoryServerState serverState,
            ArtifactoryServerRole serverRole,
            ArtifactoryRunningMode artifactoryRunningMode) {

        long startTime = System.currentTimeMillis(); //todo pass real start time before bootstrapping started

        return new ArtifactoryServer(
                serverId,
                startTime,
                serverContextUrl,
                clusterPort,
                serverState,
                serverRole,
                System.currentTimeMillis(),
                versionDetails.getVersionName(),
                versionDetails.getRevision(),
                versionDetails.getTimestamp(),
                artifactoryRunningMode, getLicenseKeyHash(ContextHelper.get())
        );
    }

    private static String getLicenseKeyHash(ArtifactoryContext artifactoryContext) {
        return artifactoryContext == null ? AddonsManager.NO_LICENSE_HASH_WITH_OSS_PADDING :
                artifactoryContext.beanForType(AddonsManager.class).getLicenseKeyHash(false);
    }
}
