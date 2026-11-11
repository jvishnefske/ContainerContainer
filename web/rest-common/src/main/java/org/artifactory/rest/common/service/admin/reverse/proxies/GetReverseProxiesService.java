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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyMethod;
import org.artifactory.descriptor.repo.ReverseProxyRepoConfig;
import org.artifactory.descriptor.repo.WebServerType;
import org.artifactory.rest.common.model.reverseproxy.ReverseProxyDescriptorModel;
import org.artifactory.rest.common.model.reverseproxy.ReverseProxyRepoConfigs;
import org.artifactory.rest.common.model.reverseproxy.ReverseProxyRepositories;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.util.AolUtils;
import org.artifactory.util.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Shay Yaakov
 */
@Slf4j
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetReverseProxiesService implements RestService {

    public static final String ARTIFACTORY_SERVER_NAME_PLACEHOLDER = "artifactory_host_placeholder";

    private CentralConfigService centralConfigService;
    private AuthorizationService authorizationService;
    private SecurityService securityService;

    @Autowired
    public GetReverseProxiesService(CentralConfigService centralConfigService,
            AuthorizationService authorizationService, SecurityService securityService) {
        this.centralConfigService = centralConfigService;
        this.authorizationService = authorizationService;
        this.securityService = securityService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        AolUtils.assertNotAol("GetReverseProxies");
        fetchSingleOrMultiReverseProxy(response, request);
    }

    private void fetchSingleOrMultiReverseProxy(RestResponse artifactoryResponse,
            ArtifactoryRestRequest artifactoryRequest) {
        String proxyKey = artifactoryRequest.getPathParamByKey("id");
        if (StringUtils.isEmpty(proxyKey)) {
            proxyKey = "dummy";
        }
        if (StringUtils.isEmpty(proxyKey)) {
            buildBadRequestResponse(artifactoryResponse);
            return;
        }
        if (!(proxyKey.equals(WebServerType.NGINX.toString()) ||
                proxyKey.equals(WebServerType.APACHE.toString()) || proxyKey.equals("dummy"))) {
            artifactoryResponse.responseCode(HttpServletResponse.SC_NOT_FOUND);
            artifactoryResponse.error("Reverse proxy id is missing");
            return;
        }

        URI baseUri = null;
        try {
            baseUri = new URI(HttpUtils.getServletContextUrl(artifactoryRequest.getServletRequest()));
        } catch (URISyntaxException e) {
            // can't set defaults.
            log.error("Can't have base url", e);
        }
        updateResponseWithSingleProxyInfo(artifactoryResponse, proxyKey, baseUri);
    }

    /**
     * build bad request response
     *
     * @param artifactoryResponse - artifactory response
     */
    private void buildBadRequestResponse(RestResponse artifactoryResponse) {
        artifactoryResponse.responseCode(HttpServletResponse.SC_BAD_REQUEST);
        artifactoryResponse.error("Reverse proxy id is missing");
    }

    /**
     * update response with single proxy response
     *
     * @param artifactoryResponse - artifactory response
     */
    private void updateResponseWithSingleProxyInfo(RestResponse artifactoryResponse, String proxyKey, URI defaultBase) {
        ReverseProxyDescriptor reverseProxy = centralConfigService.getMutableDescriptor().getCurrentReverseProxy();
        if (reverseProxy != null) {
            if (!((reverseProxy.getKey().equals(proxyKey)) || (proxyKey.equals("dummy")))) {
                artifactoryResponse.responseCode(HttpServletResponse.SC_NOT_FOUND);
                artifactoryResponse.error("Reverse proxy with id:" + proxyKey + " not found");
                return;
            }
        }
        ReverseProxyDescriptorModel reverseProxyDescriptorModel = descriptorToModel(reverseProxy, defaultBase);
        artifactoryResponse.iModel(reverseProxyDescriptorModel);
    }

    private static void updateReverseProxyWithURI(ReverseProxyDescriptorModel reverseProxy, URI defaultBase) {
        if (defaultBase != null) {
            if (StringUtils.isBlank(reverseProxy.getServerName())) {
                reverseProxy.setServerName(defaultBase.getHost());
            }
            // Looking for "artifactory_host_placeholder" String which is the default place holder for server name in
            //the default config
            if (StringUtils.isBlank(reverseProxy.getArtifactoryServerName()) ||
                    StringUtils.equals(reverseProxy.getArtifactoryServerName(), ARTIFACTORY_SERVER_NAME_PLACEHOLDER)) {
                reverseProxy.setArtifactoryServerName(defaultBase.getHost());
            }

            String path = defaultBase.getPath();
            // remove / at front
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (StringUtils.isBlank(reverseProxy.getArtifactoryAppContext())) {
                reverseProxy.setArtifactoryAppContext(path);
            }

            if (StringUtils.isBlank(reverseProxy.getPublicAppContext())) {
                reverseProxy.setPublicAppContext(path);
            }

            handleDirect(reverseProxy, defaultBase, path);
        }
        if (reverseProxy.getDockerReverseProxyMethod() == null) {
            reverseProxy.setDockerReverseProxyMethod(ReverseProxyMethod.defaultValue());
        }
    }

    private static void handleDirect(ReverseProxyDescriptorModel reverseProxy, URI defaultBase, String path) {
        if (WebServerType.DIRECT.equals(reverseProxy.getWebServerType())) {
            reverseProxy.setServerName(defaultBase.getHost());
            reverseProxy.setPublicAppContext(path);
            if (defaultBase.getScheme().equals("http")) {
                reverseProxy.setUseHttp(true);
                reverseProxy.setUseHttps(false);
                reverseProxy.setHttpPort(defaultBase.getPort());
            } else {
                reverseProxy.setUseHttp(false);
                reverseProxy.setUseHttps(true);
                reverseProxy.setHttpPort(defaultBase.getPort());
            }
        }
    }

    /**
     * convert descriptor to ui model
     *
     * @param descriptor - reverse proxy descriptor
     * @return - reverse proxy model
     */
    private ReverseProxyDescriptorModel descriptorToModel(ReverseProxyDescriptor descriptor, URI defaultBase) {
        if (descriptor == null) {
            return new ReverseProxyDescriptorModel();
        }
        ReverseProxyDescriptorModel reverseProxyDescriptorModel = new ReverseProxyDescriptorModel();

        populateReverseProxyDescriptorModelBasicResult(reverseProxyDescriptorModel, descriptor);

        if (securityService.isAdmin()) {
            reverseProxyDescriptorModel.setKey(descriptor.getKey());
            reverseProxyDescriptorModel.setArtifactoryServerName(descriptor.getArtifactoryServerName());
            reverseProxyDescriptorModel.setArtifactoryAppContext(descriptor.getArtifactoryAppContext());
            reverseProxyDescriptorModel.setPublicAppContext(descriptor.getPublicAppContext());
            reverseProxyDescriptorModel.setServerNameExpression(descriptor.getServerNameExpression());
            reverseProxyDescriptorModel.setUpStreamName(descriptor.getUpStreamName());
            reverseProxyDescriptorModel.setWebServerType(descriptor.getWebServerType());
            reverseProxyDescriptorModel.setSslKey(descriptor.getSslKey());
            reverseProxyDescriptorModel.setSslCertificate(descriptor.getSslCertificate());
            reverseProxyDescriptorModel.setServerName(descriptor.getServerName());

            updateReverseProxyWithURI(reverseProxyDescriptorModel, defaultBase);

        }
        return reverseProxyDescriptorModel;
    }

    /**
     * Populate basic descriptor result for admin and non admin user
     */
    private void populateReverseProxyDescriptorModelBasicResult(ReverseProxyDescriptorModel reverseProxyDescriptorModel,
            ReverseProxyDescriptor descriptor) {

        List<ReverseProxyRepoConfig> reverseProxyRepoConfigs = descriptor.getReverseProxyRepoConfigs();
        ReverseProxyRepositories reverseProxyRepositories = new ReverseProxyRepositories();

        // filter out reverse proxies according to user permissions
        List<ReverseProxyRepoConfigs> finalReverseProxyRepositoriesList = reverseProxyRepoConfigs.stream()
                .filter(reverseProxyRepoConfig -> reverseProxyRepoConfig.getRepoRef() != null
                        && authorizationService.userHasPermissionsOnRepositoryRoot
                        (reverseProxyRepoConfig.getRepoRef()))
                .map(ReverseProxyRepoConfigs::new)
                .collect(Collectors.toList());

        // set reverse proxy repositories result
        if (!finalReverseProxyRepositoriesList.isEmpty()) {
            reverseProxyRepositories.setReverseProxyRepoConfigs(finalReverseProxyRepositoriesList);
            reverseProxyDescriptorModel.setReverseProxyRepositories(reverseProxyRepositories);
        }

        reverseProxyDescriptorModel.setDockerReverseProxyMethod(descriptor.getDockerReverseProxyMethod());
        reverseProxyDescriptorModel.setHttpPort(descriptor.getHttpPort());
        reverseProxyDescriptorModel.setSslPort(descriptor.getSslPort());
        reverseProxyDescriptorModel.setUseHttp(descriptor.isUseHttp());
        reverseProxyDescriptorModel.setUseHttps(descriptor.isUseHttps());
    }
}
