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

package org.artifactory.rest.common.service.admin.reverse.proxies;

import com.google.common.collect.Sets;
import jakarta.servlet.http.HttpServletResponse;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.rest.common.model.reverseproxy.ReverseProxyDescriptorModel;
import org.artifactory.rest.common.model.reverseproxy.ReverseProxyRepoConfigs;
import org.artifactory.rest.common.model.reverseproxy.ReverseProxyRepositories;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.service.ConfigValidationService;
import org.artifactory.util.AolUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Shay Yaakov
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class CreateReverseProxyService extends ReverseProxyServiceBase implements RestService<Object> {

    public CreateReverseProxyService(CentralConfigService centralConfigService,
            ConfigValidationService configValidationService) {
        super(centralConfigService, configValidationService);
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        AolUtils.assertNotAol("CreateReverseProxy");
        ReverseProxyDescriptorModel descriptor = (ReverseProxyDescriptorModel) request.getImodel();
        ReverseProxyDescriptor reverseProxyDescriptor = modelToDescriptor(descriptor);
        if (reverseProxyDescriptor == null) {
            return;
        }
        addNewReverseProxy(reverseProxyDescriptor);
        updateResponse(response, reverseProxyDescriptor);
    }

    /**
     * add new reverse proxy descriptor
     *
     * @param descriptor - new reverse proxy descriptor to add
     */
    private void addNewReverseProxy(ReverseProxyDescriptor descriptor) {
        MutableCentralConfigDescriptor configDescriptor = getDescriptor();
        configDescriptor.updateReverseProxy(descriptor);
        saveAndReload(configDescriptor, descriptor);
    }


    /**
     * update response
     *
     * @param artifactoryResponse - reverse proxy response
     * @param descriptor          - descriptor
     */
    private void updateResponse(RestResponse artifactoryResponse, ReverseProxyDescriptor descriptor) {
        artifactoryResponse.info("Successfully updated reverse proxy '" + descriptor.getKey() + "'");
        artifactoryResponse.responseCode(HttpServletResponse.SC_CREATED);
    }

    /**
     * map reverse proxy model to descriptor
     *
     * @param reverseProxyDescriptorModel - reverse proxy model
     * @return reverse proxy descriptor
     */
    private ReverseProxyDescriptor modelToDescriptor(ReverseProxyDescriptorModel reverseProxyDescriptorModel) {
        MutableCentralConfigDescriptor configDescriptor = getDescriptor();
        ReverseProxyDescriptor reverseProxy = configDescriptor.getCurrentReverseProxy();
        if (reverseProxy == null) {
            reverseProxy = new ReverseProxyDescriptor();
            reverseProxy.setKey(reverseProxyDescriptorModel.getKey());
        }
        List<ReverseProxyRepoConfig> reverseProxyRepos = new ArrayList<>();
        ReverseProxyRepositories reverseProxyRepositories = reverseProxyDescriptorModel.getReverseProxyRepositories();
        if (reverseProxyRepositories != null) {
            List<ReverseProxyRepoConfigs> reverseProxyRepoConfigs =
                    reverseProxyRepositories.getReverseProxyRepoConfigs();
            updateRepoReverseProxies(reverseProxyRepoConfigs, reverseProxyRepos);
        }
        reverseProxy.setKey(reverseProxyDescriptorModel.getKey());
        reverseProxy.setArtifactoryPort(reverseProxyDescriptorModel.getArtifactoryPort());
        reverseProxy.setArtifactoryServerName(reverseProxyDescriptorModel.getArtifactoryServerName());
        reverseProxy.setDockerReverseProxyMethod(reverseProxyDescriptorModel.getDockerReverseProxyMethod());
        reverseProxy.setArtifactoryAppContext(reverseProxyDescriptorModel.getArtifactoryAppContext());
        reverseProxy.setHttpPort(reverseProxyDescriptorModel.getHttpPort());
        reverseProxy.setSslPort(reverseProxyDescriptorModel.getSslPort());
        reverseProxy.setPublicAppContext(reverseProxyDescriptorModel.getPublicAppContext());
        reverseProxy.setServerNameExpression(reverseProxyDescriptorModel.getServerNameExpression());
        reverseProxy.setUpStreamName(reverseProxyDescriptorModel.getUpStreamName());
        reverseProxy.setUseHttp(reverseProxyDescriptorModel.isUseHttp());
        reverseProxy.setUseHttps(reverseProxyDescriptorModel.isUseHttps());
        reverseProxy.setWebServerType(reverseProxyDescriptorModel.getWebServerType());
        reverseProxy.setSslKey(reverseProxyDescriptorModel.getSslKey());
        reverseProxy.setSslCertificate(reverseProxyDescriptorModel.getSslCertificate());
        reverseProxy.setServerName(reverseProxyDescriptorModel.getServerName());
        if (!reverseProxyRepos.isEmpty()) {
            reverseProxy.setReverseProxyRepoConfigs(reverseProxyRepos);
        }
        return reverseProxy;
    }

    /**
     * update repo reverse proxy
     *
     * @param reverseProxyRepoConfigs - reverse proxy reppo config
     * @param reverseProxyRepos       - reverse proxy repo
     */
    private void updateRepoReverseProxies(List<ReverseProxyRepoConfigs> reverseProxyRepoConfigs,
            List<ReverseProxyRepoConfig> reverseProxyRepos) {
        reverseProxyRepoConfigs.forEach(reverseProxyRepo -> {
            ReverseProxyRepoConfig proxyRepoConfig = new ReverseProxyRepoConfig();
            getAllRepos().forEach(repoKey -> {
                if (repoKey.equals(reverseProxyRepo.getRepoRef())) {
                    proxyRepoConfig.setRepoRef(repoKey);
                    proxyRepoConfig.setPort(reverseProxyRepo.getPort());
                    proxyRepoConfig.setServerName(reverseProxyRepo.getServerName());
                    reverseProxyRepos.add(proxyRepoConfig);
                }
            });
        });
    }

    /**
     * get all local , remote and virtual repositories
     *
     * @return set of repositories
     */
    private Set<String> getAllRepos() {
        RepositoryService repositoryService = ContextHelper.get().getRepositoryService();
        Set<RepoConfig> repoConfigs = Sets.newHashSet();
        List<LocalConfig> localRepoConfigs = repositoryService.getLocalFederatedAndCachedRepoConfigs();
        repoConfigs.addAll(localRepoConfigs);
        // add remote repo
        List<RemoteRepoConfig> remoteRepoConfigs = repositoryService.getRemoteRepoConfigs();
        repoConfigs.addAll(remoteRepoConfigs);
        // add virtual repo
        List<VirtualRepoConfig> virtualRepoConfigs = repositoryService.getVirtualRepoConfigs();
        repoConfigs.addAll(virtualRepoConfigs);
        return repoConfigs.stream().map(RepoConfig::getKey).collect(Collectors.toSet());
    }
}
