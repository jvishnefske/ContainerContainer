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

package org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder;

import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.ui.rest.model.admin.configuration.repository.replication.local.LocalReplicationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.replication.remote.RemoteReplicationConfigModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Utility class for converting model to descriptor
 *
 * @author Dan Feldman
 */
@Component
public class ReplicationConfigDescriptorBuilder {

    @Autowired
    private CentralConfigService centralConfigService;

    public Set<LocalReplicationDescriptor> buildLocalReplications(List<LocalReplicationConfigModel> models,
            String repoKey) {
        Set<LocalReplicationDescriptor> descriptors = Sets.newHashSet();
        for (LocalReplicationConfigModel model : models) {
            LocalReplicationDescriptor descriptor = buildLocalReplication(model, repoKey);
            descriptors.add(descriptor);
        }
        return descriptors;
    }

    public LocalReplicationDescriptor buildLocalReplication(LocalReplicationConfigModel model, String repoKey) {
        LocalReplicationDescriptor descriptor = new LocalReplicationDescriptor();
        descriptor.setEnableEventReplication(model.isEnableEventReplication());
        descriptor.setUrl(model.getUrl());
        descriptor.setUsername(model.getUsername());
        descriptor.setPassword(CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), model.getPassword()));
        if (model.isDisableProxy() != null) {
            descriptor.setDisableProxy(model.isDisableProxy());
        }
        final String proxyKey = model.getProxy();
        if (descriptor.isDisableProxy()) {
            descriptor.setProxy(null);
        } else {
            final CentralConfigDescriptor centralConfig = centralConfigService.getDescriptor();
            if (StringUtils.isNotBlank(proxyKey)) {
                descriptor.setProxy(centralConfig.getProxy(proxyKey));
            } else {
                descriptor.setProxy(centralConfig.getDefaultProxy());
            }
        }
        descriptor.setSocketTimeoutMillis(model.getSocketTimeout());
        descriptor.setCronExp(model.getCronExp());
        descriptor.setEnabled(model.isEnabled());
        descriptor.setRepoKey(repoKey);
        descriptor.setSyncDeletes(model.isSyncDeletes());
        descriptor.setSyncProperties(model.isSyncProperties());
        descriptor.setSyncStatistics(model.isSyncStatistics());
        descriptor.setIncludePathPrefixPattern(model.getIncludePathPrefixPattern());
        descriptor.setExcludePathPrefixPattern(model.getExcludePathPrefixPattern());
        return descriptor;
    }

    public RemoteReplicationDescriptor buildRemoteReplication(RemoteReplicationConfigModel model, String repoKey) {
        RemoteReplicationDescriptor descriptor = new RemoteReplicationDescriptor();
        descriptor.setEnabled(model.isEnabled());
        descriptor.setCronExp(model.getCronExp());
        descriptor.setSyncDeletes(model.isSyncDeletes());
        descriptor.setSyncProperties(model.isSyncProperties());
        descriptor.setEnableEventReplication(model.isEnableEventReplication());
        descriptor.setRepoKey(repoKey);
        descriptor.setIncludePathPrefixPattern(model.getIncludePathPrefixPattern());
        descriptor.setExcludePathPrefixPattern(model.getExcludePathPrefixPattern());
        return descriptor;
    }
}
