/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.support.manifest;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ha.HaNodeProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.access.common.ServiceId;
import org.jfrog.support.rest.model.SupportBundleConfig;
import org.jfrog.support.rest.model.manifest.NodeManifest;
import org.jfrog.support.rest.model.manifest.NodeManifestBundleInfo;

import jakarta.annotation.Nullable;
import java.util.Objects;

import static org.artifactory.addon.support.ArtifactorySupportBundleConfig.fromSupportBundleConfig;

/**
 * @author Tamir Hadad
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NodeManifestFactory {

    private static final String SERVICE_TYPE = "jfrt";
    private static final String MICRO_SERVICE_NAME = "artifactory";
    private static final String DEFAULT_NODE_ID = "sa";

    public static NodeManifest newNodeManifest(SupportBundleConfig bundleConfig,
            @Nullable ServiceId artifactoryServiceId) {
        return newNodeManifest(fromSupportBundleConfig(bundleConfig), artifactoryServiceId);
    }

    public static NodeManifest newNodeManifest(NodeManifestBundleInfo bundleInfo,
            @Nullable ServiceId artifactoryServiceId) {
        ArtifactoryHome artifactoryHome = ContextHelper.get().getArtifactoryHome();
        String version = artifactoryHome.getRunningArtifactoryProductVersion().getVersionName();

        String nodeId = DEFAULT_NODE_ID;
        HaNodeProperties haNodeProperties = artifactoryHome.getHaNodeProperties();
        if (!Objects.isNull(haNodeProperties)) {
            nodeId = haNodeProperties.getServerId();
        }
        String serviceId = "";
        if (!Objects.isNull(artifactoryServiceId)) {
            serviceId = artifactoryServiceId.getFormattedName();
        }
        return new NodeManifest(SERVICE_TYPE, MICRO_SERVICE_NAME, version, serviceId, nodeId, bundleInfo);
    }
}
