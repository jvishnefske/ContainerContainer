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

package org.artifactory.repo.reverse.proxy;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyRepoConfig;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.util.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author Chen Keinan
 */
@Component
public class ApacheTemplateProvider extends ReverseProxyTemplateProvider {

    @Autowired
    public ApacheTemplateProvider(CentralConfigService centralConfigService) {
        super();
        templatePath =
                centralConfigService.getSystemConfiguration().isRouterEnabled() ? "/templates/apache_router.ftl" :
                        "/templates/apache.ftl";
    }

    /**
     * return reverse proxy snippet
     *
     * @param repoDescriptor         - repo descriptor
     * @param reverseProxyRepoConfig - reverse proxy descriptor
     * @param generalOnly            - is general data
     * @return snippet
     */
    protected String getReverseProxySnippet(ReverseProxyDescriptor reverseProxy, RepoDescriptor repoDescriptor,
            ReverseProxyRepoConfig reverseProxyRepoConfig,
            boolean generalOnly) {
        if (isGeneralSslAndDockerPortAreTheSame(reverseProxy, reverseProxyRepoConfig)) {
            return "";
        }
        return buildDockerTemplate(reverseProxy, repoDescriptor, reverseProxyRepoConfig, generalOnly, templatePath);
    }

    /**
     * generate reverse proxy snippet
     *
     * @param reverseProxyDescriptor - reverse proxy descriptor
     * @return reverse proxy snippet
     */
    protected String getGeneralReverseProxySnippet(ReverseProxyDescriptor reverseProxyDescriptor,
            List<String> repoKeys) {
        StringBuilder generalBuilder = new StringBuilder();
        if (reverseProxyDescriptor.isUseHttp()) {
            generalBuilder.append(buildGeneralTemplate(reverseProxyDescriptor, templatePath, repoKeys,
                    new ReverseProxyPorts(true, false, false), true));
        }
        if (reverseProxyDescriptor.isUseHttps()) {
            generalBuilder.append(buildGeneralTemplate(reverseProxyDescriptor, templatePath, repoKeys,
                    new ReverseProxyPorts(false, true, false), false));
        }
        return generalBuilder.toString();
    }

    protected void updateGeneralHttpPorts(ReverseProxyDescriptor reverseProxyDescriptor, Map<Object, Object> params,
            int sslPort, ReverseProxyPorts reverseProxyPorts) {
        updateGeneralPortsUsage(params, reverseProxyPorts);
        params.put("sslPort", sslPort);
        params.put("addSsl", reverseProxyDescriptor.isUseHttps());
        params.put("useHttps", reverseProxyDescriptor.isUseHttps());
        params.put("useHttp", reverseProxyDescriptor.isUseHttp());
        params.put("httpPort", reverseProxyDescriptor.getHttpPort());
    }

    private void updateGeneralPortsUsage(Map<Object, Object> params, ReverseProxyPorts reverseProxyPorts) {
        if (reverseProxyPorts.isHttp()) {
            params.put("httpOnly", true);
        } else {
            params.put("httpOnly", false);
        }
        if (reverseProxyPorts.isHttps()) {
            params.put("httpsOnly", true);
        } else {
            params.put("httpsOnly", false);
        }
    }

    @Override
    protected String getServerBalancerKey(ArtifactoryServer server, int artifactoryPort) {
        return "BalancerMember " +
                HttpUtils.getApacheServerAndPortFromContext(server.getContextUrl(), artifactoryPort) + " route=" +
                server.getServerId();
    }

    @Override
    public void addHaApacheForDocker(Map<Object, Object> params) {
        boolean haConfigure = isHaConfigure();
        params.put("addHa", haConfigure);
    }
}
