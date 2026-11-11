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

package org.artifactory.ui.rest.service.admin.configuration.proxies;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.common.ConfigModelPopulator;
import org.artifactory.ui.rest.model.admin.configuration.proxy.Proxy;
import org.artifactory.ui.rest.model.empty.EmptyModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Chen Keinan
 */
@Component
public class GetProxiesService implements RestService {

    private CentralConfigService centralConfigService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        fetchSingleOrMultiProxy(response, request, Boolean.TRUE == request.getImodel());
    }

    /**
     * fetch single or multi group depend on query and path param
     *
     * @param artifactoryResponse - encapsulate all data related to response
     *                            artifactoryRequest - encapsulate data related to request
     * @param withPassword - indicates if password should be present in a proxy/proxies
     */
    private void fetchSingleOrMultiProxy(RestResponse artifactoryResponse,
            ArtifactoryRestRequest artifactoryRequest, boolean withPassword) {
        String proxyKey = artifactoryRequest.getPathParamByKey("id");
        if (isMultiProxy(proxyKey)) {
            updateResponseWithMultiProxyInfo(artifactoryResponse, withPassword);
        } else {
            updateResponseWithSingleProxyInfo(artifactoryResponse, proxyKey, withPassword);
        }
    }

    /**
     * get Single Group info and update response
     *
     * @param artifactoryResponse - encapsulate Data require for response
     * @param proxyKey            - proxy Key from path param
     * @param withPassword - indicates if password should be present in a proxy
     */
    private void updateResponseWithSingleProxyInfo(RestResponse artifactoryResponse, String proxyKey,
            boolean withPassword) {
        RestModel proxy = getProxy(proxyKey, withPassword);
        if (proxy == null) {
            proxy = new EmptyModel();
        }
        artifactoryResponse.iModel(proxy);
    }

    /**
     * get Multi Group info and update response
     *
     * @param artifactoryResponse - encapsulate Data require for response
     * @param withPassword - indicates if password should be present in proxies
     */
    private void updateResponseWithMultiProxyInfo(RestResponse artifactoryResponse, boolean withPassword) {
        List<ProxyDescriptor> proxyDescriptors = getProxyInfos(withPassword);
        // add groups to List
        List<RestModel> proxyList = new ArrayList<>();
        proxyDescriptors.forEach(
                proxyInfo -> proxyList.add(ConfigModelPopulator.populateProxyConfiguration(proxyInfo)));
        artifactoryResponse.iModelList(proxyList);
    }

    /**
     * check id require to get single / multi group
     *
     * @param proxyKey - single group name
     * @return if true require multi group
     */
    private boolean isMultiProxy(String proxyKey) {
        return proxyKey == null || proxyKey.length() == 0;
    }

    /**
     * return group by name
     *
     * @param proxyKey - proxy key
     * @param withPassword - indicates if password should be present in a proxy
     */
    private Proxy getProxy(String proxyKey, boolean withPassword) {
        ProxyDescriptor proxyDescriptor = centralConfigService.getMutableDescriptor().getProxy(proxyKey);
        if (!withPassword && proxyDescriptor != null) {
            proxyDescriptor.setPassword(null);
        }
        return ConfigModelPopulator.populateProxyConfiguration(proxyDescriptor);
    }

    /**
     * return all groups / default groups
     *
     * @return - list of groups info
     * @param withPassword - indicates if password should be present in proxies
     */
    private List<ProxyDescriptor> getProxyInfos(boolean withPassword) {
        final List<ProxyDescriptor> proxyDescriptors = centralConfigService.getMutableDescriptor().getProxies();
        for (ProxyDescriptor proxyDescriptor : proxyDescriptors) {
            if (!withPassword) {
                proxyDescriptor.setPassword(null);
            }
        }
        return proxyDescriptors;
    }

    @Autowired
    public void setCentralConfigService(CentralConfigService centralConfigService) {
        this.centralConfigService = centralConfigService;
    }
}
