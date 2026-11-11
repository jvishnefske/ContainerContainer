package org.artifactory.repo;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.jackson.JacksonReader;
import org.artifactory.api.repo.ResearchService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.monitor.OnlineMonitorService;
import org.artifactory.security.props.auth.model.OAuth2TokenModel;
import org.artifactory.throtlling.generic.rrl.service.IRequestRateLimiterService;
import org.artifactory.util.BasicHttpUtils;
import org.artifactory.util.StreamUtils;
import org.jfrog.client.http.CloseableHttpClientDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.jfrog.common.ExceptionLogUtils.debugOrWarn;

/**
 * HttpRepo implementation for Ansible remote repositories. Provides a dedicated token client
 * (via HttpRepo#createHttpClient) for OAuth2 token fetches
 */
public class AnsibleHttpRepo extends HttpRepo {
    private static final Logger log = LoggerFactory.getLogger(AnsibleHttpRepo.class);

    private volatile CloseableHttpClient tokenClient;

    AnsibleHttpRepo(HttpRepoDescriptor descriptor, RemoteRepoConfig repoConfig,
            InternalRepositoryService repositoryService, AddonsManager addonsManager,
            ResearchService researchService, RemoteRepo oldRemoteRepo,
            OnlineMonitorService onlineMonitorService, CentralConfigService configService,
            IRequestRateLimiterService rateLimiterService) {
        super(descriptor, repoConfig, repositoryService, addonsManager, researchService,
                oldRemoteRepo, onlineMonitorService, configService, rateLimiterService);
    }

    @Override
    public void close() {
        super.close();
        if (tokenClient instanceof CloseableHttpClientDecorator) {
            ((CloseableHttpClientDecorator) tokenClient).onClose();
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (tokenClient != null) {
            StreamUtils.close(tokenClient);
        }
    }

    @VisibleForTesting
    CloseableHttpClient getTokenClient() {
        if (isOffline()) {
            throw new IllegalStateException("Repo is offline. Cannot use the token fetching HTTP client.");
        }
        if (tokenClient == null) {
            synchronized (this) {
                if (tokenClient == null) {
                    this.tokenClient = createHttpClient(
                            ConstantValues.HttpClientMaxAnsibleTokenConnections.getInt(), false, isNeedToNormalize(),
                            "token-client");
                }
            }
        }
        return tokenClient;
    }

    @Nullable
    public OAuth2TokenModel fetchAnsibleAuthToken(HttpPost request) {
        log.trace("Requesting Ansible token from url '{}'", request.getURI());
        try (CloseableHttpResponse response = interceptResponse(
                doExecuteMethod(request, null, null, getTokenClient()))) {
            var entity = response.getEntity();
            try {
                StatusLine statusLine = response.getStatusLine();
                if (statusLine == null || !BasicHttpUtils.isSuccessfulResponseCode(statusLine.getStatusCode())) {
                    int statusCode = statusLine != null ? statusLine.getStatusCode() : -1;
                    log.warn("Ansible token request failed for repo: {}, status: {}", getKey(), statusCode);
                    return null;
                }
                if (entity == null) {
                    log.warn("Ansible token request returned empty response for repo: {}", getKey());
                    return null;
                }
                return JacksonReader.streamAsClass(entity.getContent(), OAuth2TokenModel.class);
            } finally {
                EntityUtils.consumeQuietly(entity);
            }
        } catch (IOException e) {
            debugOrWarn(log, "Ansible token request failed for repo: " + getKey(), e);
            return null;
        }
    }
}
