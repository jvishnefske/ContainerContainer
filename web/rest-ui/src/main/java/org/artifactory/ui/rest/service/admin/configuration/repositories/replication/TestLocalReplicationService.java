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

package org.artifactory.ui.rest.service.admin.configuration.repositories.replication;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.model.LocalConfig;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.CommonLocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteNetworkRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.replication.local.LocalReplicationConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.TestMethodFactory;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.ReplicationConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.ReplicationConfigValidator;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.artifactory.util.StreamUtils;
import org.jfrog.common.BiOptional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_MOVED_TEMPORARILY;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.ui.rest.service.admin.configuration.repositories.util.RemoteRepositoryProvider.getRemoteRepoHttpClient;

/**
 * @author Dan Feldman
 */
@Component
public class TestLocalReplicationService implements RestService<LocalRepositoryConfigModel> {
    private static final Logger log = LoggerFactory.getLogger(TestLocalReplicationService.class);

    private static final String REPLICATION_URL_PARAM = "replicationUrl";
    private static final String ERR = "Error testing push replication config: ";
    static final String FAKE_CRON = "0 0 12 1/1 * ? *";

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private ReplicationConfigDescriptorBuilder replicationDescriptorBuilder;

    @Autowired
    private RepoConfigValidator repoValidator;

    @Autowired
    private RepoConfigDescriptorBuilder repoDescriptorBuilder;

    @Autowired
    private ReplicationConfigValidator replicationValidator;

    @Override
    public void execute(ArtifactoryRestRequest<LocalRepositoryConfigModel> request, RestResponse response) {
        String replicationUrl = request.getQueryParamByKey(REPLICATION_URL_PARAM);
        CommonLocalRepositoryConfigModel model = request.getImodel();
        if (model == null) {
            response.error("No repository configuration given to test replication with.").responseCode(SC_BAD_REQUEST);
            return;
        } else if (StringUtils.isBlank(replicationUrl)) {
            response.error("No url given to identify which replication target to test").responseCode(SC_BAD_REQUEST);
            return;
        }
        try {
            setFakeCronExpForTestIfNeeded(model);
            LocalConfig repo = model.toRepoConfig(repoValidator, repoDescriptorBuilder, null);
            Set<LocalReplicationDescriptor> replications = ((LocalRepositoryConfigModel) model)
                    .getReplicationDescriptors(replicationValidator, replicationDescriptorBuilder);
            int activeReplications = (int) replications.stream()
                    .filter(LocalReplicationDescriptor::isEnabled)
                    .count();
            BiOptional.of(replications.stream()
                    .filter(replication -> replicationUrl.equals(replication.getUrl()))
                    .findFirst())
                    .ifNotPresent(() -> response.error("No replication configuration exists for this repo  and url '"
                            + replicationUrl + "'"))
                    .ifPresent(replication -> testReplicationTarget(repo, replication, response, activeReplications));
        } catch (RepoConfigException rce) {
            log.debug("Error testing local replication: ", rce);
            response.error(rce.getMessage()).responseCode(SC_BAD_REQUEST);
        }
    }

    private void testReplicationTarget(LocalConfig repoConfig, LocalReplicationDescriptor replication,
            RestResponse response, int totalActiveReplications) {
        if (StringUtils.isBlank(replication.getRepoKey())) {
            replication.setRepoKey(repoConfig.getKey());
        }
        try {
            if (!testValidTargetRepo(replication, response)) {
                return;
            }
            if (!testValidReplicationLocation(replication, response)) {
                return;
            }
            addonsManager.addonByType(ReplicationAddon.class).validateTargetLicense(replication, repoConfig,
                    totalActiveReplications);
            response.info("Push replication target url '" + replication.getUrl() + "' tested successfully")
                    .responseCode(HttpStatus.SC_OK);
        } catch (UnknownHostException unh) {
            String error = ERR;
            error += "unknown host '";
            if (unh.getMessage().equalsIgnoreCase("api")) {
                error += replication.getUrl();
            } else {
                error += unh.getMessage();
            }
            log.debug(error, unh);
            response.error(error).responseCode(SC_BAD_REQUEST);
        } catch (Exception e) {
            log.debug(ERR, e);
            response.error(ERR + e.getMessage()).responseCode(SC_BAD_REQUEST);
        }
    }

    private boolean testValidTargetRepo(LocalReplicationDescriptor replication, RestResponse response) {
        if(replication.getUrl().endsWith("-cache")){
            response.error("Replication to remote cache repositories is not allowed.").responseCode(SC_BAD_REQUEST);
            return false;
        }
        return true;
    }

    /**
     * Sets fake cron expressions in the replication descriptors just to allow for the test to run
     * if non were set by the ui
     */
    private void setFakeCronExpForTestIfNeeded(CommonLocalRepositoryConfigModel model) {
        List<LocalReplicationConfigModel> replications =
                Optional.ofNullable(model.getReplications()).orElse(Lists.newArrayList());
        replications.stream()
                .filter(replication -> StringUtils.isBlank(replication.getCronExp()))
                .forEach(emptyReplication -> emptyReplication.setCronExp(FAKE_CRON));
    }

    @VisibleForTesting
    boolean testValidReplicationLocation(LocalReplicationDescriptor replication,
            RestResponse artifactoryResponse) throws IOException {
        CloseableHttpResponse response = null;
        CloseableHttpClient client = null;
        try {
            HttpHead test = new HttpHead(replication.getUrl());
            TestMethodFactory.addOriginatedHeader(test);
            client = getClientForReplicationUrl(replication, replication.getUrl());
            response = client.execute(test);
            int status = response.getStatusLine().getStatusCode();
            if (status != SC_OK && status != SC_MOVED_TEMPORARILY) {
                artifactoryResponse.error("Connection failed: Target replication URL returned error " + status + ": "
                        + response.getStatusLine().getReasonPhrase()).responseCode(SC_BAD_REQUEST);
                return false;
            }
        } finally {
            StreamUtils.close(response);
            StreamUtils.close(client);
        }
        return true;
    }

    private CloseableHttpClient getClientForReplicationUrl(LocalReplicationDescriptor replication, String url) {
        RemoteNetworkRepositoryConfigModel network = new RemoteNetworkRepositoryConfigModel();
        network.setUrl(url);
        network.setUsername(replication.getUsername());
        network.setPassword(replication.getPassword());
        if (replication.getProxy() != null) {
            network.setProxy(replication.getProxy().getKey());
        }
        network.setSocketTimeout(replication.getSocketTimeoutMillis());
        network.setSyncProperties(replication.isSyncProperties());
        return getRemoteRepoHttpClient(url, network, false);
    }
}