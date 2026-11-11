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

package org.artifactory.rest.resource.replication;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.replication.ReplicationConfigRequest;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.replication.ReplicationBaseDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.exception.ForbiddenWebAppException;
import org.quartz.CronExpression;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author mamo
 */
public abstract class ReplicationConfigRequestHelper {

    private ReplicationConfigRequestHelper() {
    }

    public static void fillBaseReplicationDescriptor(ReplicationConfigRequest replicationRequest,
            ReplicationBaseDescriptor currentReplication) {
        if (replicationRequest.getEnabled() != null) {
            currentReplication.setEnabled(replicationRequest.getEnabled());
        }
        if (replicationRequest.getCronExp() != null) {
            currentReplication.setCronExp(replicationRequest.getCronExp());
        }
        if (replicationRequest.getSyncDeletes() != null) {
            currentReplication.setSyncDeletes(replicationRequest.getSyncDeletes());
        }
        if (replicationRequest.getSyncProperties() != null) {
            currentReplication.setSyncProperties(replicationRequest.getSyncProperties());
        }
        if (replicationRequest.getCheckBinaryExistenceInFilestore() != null) {
            currentReplication.setCheckBinaryExistenceInFilestore(
                    replicationRequest.getCheckBinaryExistenceInFilestore());
        }
        if (replicationRequest.getIncludePathPrefixPattern() != null) {
            currentReplication.setIncludePathPrefixPattern(replicationRequest.getIncludePathPrefixPattern());
        }
        if (replicationRequest.getExcludePathPrefixPattern() != null) {
            currentReplication.setExcludePathPrefixPattern(replicationRequest.getExcludePathPrefixPattern());
        }
        if (replicationRequest.getEnableEventReplication() != null) {
            handleNonNullEventReplication(replicationRequest, currentReplication);
        }
    }

    private static void handleNonNullEventReplication(ReplicationConfigRequest replicationRequest,
            ReplicationBaseDescriptor currentReplication) {
        validateEnableEventReplicationIsEntitled(replicationRequest, currentReplication);
        currentReplication.setEnableEventReplication(replicationRequest.getEnableEventReplication());
    }

    private static void validateEnableEventReplicationIsEntitled(ReplicationConfigRequest replicationRequest,
            ReplicationBaseDescriptor currentReplication) {
        if (Boolean.TRUE.equals(replicationRequest.getEnableEventReplication()) &&
                !ContextHelper.get().beanForType(AddonsManager.class)
                        .isEntitled(EntitlementType.EVENT_BASED_PULL_REPLICATION) &&
                currentReplication instanceof RemoteReplicationDescriptor) {
            throw new ForbiddenWebAppException(
                    "Event based pull replication is only available with an Enterprise license.");
        }
    }

    public static void fillLocalReplicationDescriptor(ReplicationConfigRequest replicationRequest,
            LocalReplicationDescriptor newReplication) {
        fillBaseReplicationDescriptor(replicationRequest, newReplication);
        if (replicationRequest.getUrl() != null) {
            newReplication.setUrl(replicationRequest.getUrl());
        }
        if (replicationRequest.getEnableEventReplication() != null) {
            newReplication.setEnableEventReplication(replicationRequest.getEnableEventReplication());
        }
        if (replicationRequest.getUsername() != null) {
            newReplication.setUsername(replicationRequest.getUsername());
        }
        if (replicationRequest.getPassword() != null) {
            newReplication.setPassword(replicationRequest.getPassword());
        }
        if (replicationRequest.getSyncStatistics() != null) {
            newReplication.setSyncStatistics(replicationRequest.getSyncStatistics());
        }
        if (replicationRequest.getDisableProxy() != null) {
            newReplication.setDisableProxy(replicationRequest.getDisableProxy());
        }
        final String proxyKey = replicationRequest.getProxy();
        if (newReplication.isDisableProxy()) {
            newReplication.setProxy(null);
        } else {
            final CentralConfigDescriptor centralConfig = ContextHelper.get()
                    .beanForType(CentralConfigService.class)
                    .getDescriptor();
            if (StringUtils.isNotBlank(proxyKey)) {
                ProxyDescriptor proxyDescriptor = centralConfig.getProxy(proxyKey);
                if (proxyDescriptor == null) {
                    throw new BadRequestException("Could not find proxy");
                }
                newReplication.setProxy(proxyDescriptor);
            } else {
                newReplication.setProxy(centralConfig.getDefaultProxy());
            }
        }
        if (replicationRequest.getSocketTimeoutMillis() != null) {
            newReplication.setSocketTimeoutMillis(replicationRequest.getSocketTimeoutMillis());
        }
    }

    public static void fillRemoteReplicationDescriptor(ReplicationConfigRequest replicationRequest,
            RemoteReplicationDescriptor newReplication) {
        if (replicationRequest.getEnableEventReplication() != null) {
            newReplication.setEnableEventReplication(replicationRequest.getEnableEventReplication());
        }
    }

    public static void verifyBaseReplicationRequest(ReplicationBaseDescriptor replication) {
        if (StringUtils.isBlank(replication.getCronExp())) {
            throw new BadRequestException("cronExp is required");
        }
        if (!CronExpression.isValidExpression(replication.getCronExp())) {
            throw new BadRequestException("Invalid cronExp");
        }
    }

    public static void verifyLocalReplicationRequest(LocalReplicationDescriptor replication) {
        if (StringUtils.isBlank(replication.getUrl())) {
            throw new BadRequestException("url is required");
        }
        try {
            new URL(replication.getUrl());
        } catch (MalformedURLException e) {
            throw new BadRequestException("Invalid url [" + e.getMessage() + "]");
        }
        verifyBaseReplicationRequest(replication);
        if (StringUtils.isBlank(replication.getUsername())) {
            throw new BadRequestException("username is required");
        }
    }
}
