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
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.model.admin.configuration.proxy.Proxy;
import org.artifactory.util.AolUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.artifactory.util.ProxyUtils.validateProxyServices;

/**
 * @author Chen Keinan
 */
@Component
public class UpdateProxyService implements RestService {

    @Autowired
    private CentralConfigService centralConfigService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        AolUtils.assertNotAol("UpdateProxy");
        String proxyKey = request.getPathParamByKey("id");
        ProxyDescriptor proxyDescriptor = (ProxyDescriptor) request.getImodel();
        // update proxy
        updateProxy(proxyDescriptor, proxyKey);
        // update response feedback
        response.info("Successfully updated proxy '" + proxyKey + "'");
    }

    /**
     * add new proxy to descriptor to configuration descriptor
     *
     * @param newProxy - new proxy descriptor
     */
    private void updateProxy(ProxyDescriptor newProxy, String proxyKey) {
        MutableCentralConfigDescriptor configDescriptor = centralConfigService.getMutableDescriptor();
        ProxyDescriptor currentProxy = configDescriptor.getProxy(proxyKey);
        if (currentProxy == null) {
            throw new BadRequestException("Couldn't find proxy '" + proxyKey + "' in proxies list.");
        }
        populateProxyData(currentProxy, newProxy);
        validateProxyServices(currentProxy);
        configDescriptor.proxyChanged(currentProxy, ((Proxy) newProxy).isShouldUpdateRemotes());
        centralConfigService.saveEditedDescriptorAndReload(configDescriptor, newProxy);
    }

    /**
     * populate proxy changed data
     *
     * @param currentProxyDesc - proxy descriptor before update
     * @param newProxy         - update proxy descriptor
     */
    private void populateProxyData(ProxyDescriptor currentProxyDesc, ProxyDescriptor newProxy) {
        currentProxyDesc.setPlatformDefault(newProxy.isPlatformDefault());
        currentProxyDesc.setPort(newProxy.getPort());
        currentProxyDesc.setHost(newProxy.getHost());
        currentProxyDesc.setUsername(newProxy.getUsername());
        currentProxyDesc.setDomain(newProxy.getDomain());
        currentProxyDesc.setNtHost(newProxy.getNtHost());
        currentProxyDesc.setRedirectedToHosts(newProxy.getRedirectedToHosts());
        currentProxyDesc.setPassword(newProxy.getPassword());
        currentProxyDesc.setServices(newProxy.getServices());
    }
}
