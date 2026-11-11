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

package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteNetworkRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.util.ClientCertificateUtils;
import org.artifactory.util.HttpClientConfigurator;
import org.artifactory.util.bearer.TestRemoteRepositoryUrlBearerScheme;
import org.jfrog.client.http.auth.PreemptiveBearerTokenAuthInterceptor;
import org.jfrog.client.util.KeyStoreProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * @author Aviad Shikloshi
 */
public class RemoteRepositoryProvider {
    private static final Logger log = LoggerFactory.getLogger(RemoteRepositoryProvider.class);

    protected static final int RETRY_COUNT = 1;
    protected static final int DEFAULT_TIMEOUT = 15000;

    public static CloseableHttpClient getRemoteRepoHttpClient(String remoteUrl,
            RemoteNetworkRepositoryConfigModel networkConfig, boolean useClientCertificate) {
        return getHttpClientConfigurator(remoteUrl, networkConfig, useClientCertificate, null, null).build();
    }

    public static CloseableHttpClient getRemoteRepoHttpClient(String remoteUrl,
            RemoteRepositoryConfigModel repositoryModel, boolean useClientCertificate) {
        return getHttpClientConfigurator(remoteUrl, repositoryModel, useClientCertificate).build();
    }

    @VisibleForTesting
    static HttpClientConfigurator getHttpClientConfigurator(String remoteUrl,
            RemoteRepositoryConfigModel repositoryModel, boolean useClientCertificate) {
        HttpRequestInterceptor interceptor = null;
        String repoKey = repositoryModel.getGeneral().getRepoKey();
        TypeSpecificConfigModel typeSpecific = repositoryModel.getTypeSpecific();
        RepoType repoType = typeSpecific.getRepoType();
        RemoteNetworkRepositoryConfigModel networkModel =
                repositoryModel.getAdvanced() != null ? repositoryModel.getAdvanced().getNetwork() : null;
        if (!repoType.isDockerGroup() && Boolean.TRUE.equals(typeSpecific.isEnableTokenAuthentication())) {
            String token = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), networkModel == null ?
                    "" :
                    networkModel.getPassword());
            TestRemoteRepositoryUrlBearerScheme scheme = new TestRemoteRepositoryUrlBearerScheme(token);
            interceptor = new PreemptiveBearerTokenAuthInterceptor(scheme);
        }
        return getHttpClientConfigurator(remoteUrl, networkModel, useClientCertificate, repoKey, interceptor);
    }

    private static HttpClientConfigurator getHttpClientConfigurator(String remoteUrl,
            RemoteNetworkRepositoryConfigModel networkConfig, boolean useClientCertificate, String repoKey,
            HttpRequestInterceptor interceptor) {
        // In case network model was not sent in the request we are using the default values
        if (networkConfig == null) {
            networkConfig = new RemoteNetworkRepositoryConfigModel();
        }
        int socketTimeout =
                networkConfig.getSocketTimeout() == null ? DEFAULT_TIMEOUT : networkConfig.getSocketTimeout();
        HttpClientConfigurator configurator = new HttpClientConfigurator();
        configurator
                .hostFromUrl(remoteUrl)
                .connectionTimeout(socketTimeout)
                .socketTimeout(socketTimeout)
                .staleCheckingEnabled(true)
                .retry(RETRY_COUNT, false)
                .localAddress(networkConfig.getLocalAddress())
                .enableCookieManagement(networkConfig.getCookieManagement() != null);

        if (interceptor == null) {
            configurator.authentication(networkConfig.getUsername(),
                    CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), networkConfig.getPassword()),
                    networkConfig.getLenientHostAuth() != null);
        } else {
            configurator.enableTokenAuthentication(true, repoKey, interceptor, networkConfig.getPassword(),
                    Collections.emptyList());
        }

        if (useClientCertificate) {
            String certificateToUse = networkConfig.getSelectedInstalledCertificate();
            if (StringUtils.isNotBlank(certificateToUse)) {
                KeyStoreProvider keyStoreProvider =
                        ClientCertificateUtils.getKeyStoreProvider();
                if (keyStoreProvider != null) {
                    configurator.clientCertKeyStoreProvider(keyStoreProvider);
                    configurator.clientCertAlias(
                            ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX
                                    + certificateToUse);
                }
            }
        }
        CentralConfigService configService = ContextHelper.get().getCentralConfig();
        ProxyDescriptor proxyDescriptor = configService.getDescriptor().getProxy(networkConfig.getProxy());
        if (networkConfig.getProxy() != null && proxyDescriptor == null) {
            log.debug("Proxy '{}' does not exist in the config descriptor", networkConfig.getProxy());
        } else {
            configurator.proxy(proxyDescriptor);
        }
        return configurator;
    }

}
