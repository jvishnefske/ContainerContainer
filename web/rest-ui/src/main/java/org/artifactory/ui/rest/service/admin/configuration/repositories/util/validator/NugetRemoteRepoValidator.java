package org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.LocalRepositoryConfigurationImpl;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteNetworkRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.RemoteRepositoryProvider;
import org.artifactory.util.HttpUtils;
import org.jfrog.client.util.PathUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_MOVED_TEMPORARILY;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.repo.service.NugetRepoConfigValidator.isNugetSmartRepoNormalized;

/**
 * @author arkojd
 */
@Component
public class NugetRemoteRepoValidator implements RemoteRepoPackageSpecificValidator {

    public static final String NUGET_NORMALISED_SMART_ERR_MSG =
            "Registry for this Smart Normalised repo should be normalised";

    @Override
    public void validate(RemoteRepoConfig remoteRepoConfig, MutableCentralConfigDescriptor configDescriptor,
            RemoteRepositoryConfigModel model) throws RepoConfigException {
        validateNugetSmartRemoteRepo(remoteRepoConfig, model);
    }

    private static void validateNugetSmartRemoteRepo(RemoteRepoConfig repoConfig, RemoteRepositoryConfigModel model)
            throws RepoConfigException {
        if (!isNugetSmartRepoNormalized(repoConfig)) {
            return;
        }
        RemoteNetworkRepositoryConfigModel networkModel =
                model.getAdvanced() != null ? model.getAdvanced().getNetwork() : null;
        CloseableHttpResponse response = null;
        try (CloseableHttpClient client = RemoteRepositoryProvider.getRemoteRepoHttpClient(
                getFetchRepoDetailsUrl(repoConfig),
                model, true)) {
            HttpGet httpGet = new HttpGet(HttpUtils.encodeUrl(getFetchRepoDetailsUrl(repoConfig)));
            response = client.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            if (!isSuccessStatusCode(statusCode)) {
                throw new RepoConfigException("Connection failed: Target remote URL returned error " + statusCode + ": "
                        + response.getStatusLine().getReasonPhrase(), HttpStatus.SC_BAD_REQUEST);
            }
            String responseBody = EntityUtils.toString(response.getEntity(),
                    ContentType.getOrDefault(response.getEntity()).getCharset());
            LocalRepositoryConfigurationImpl config = new ObjectMapper().readValue(responseBody,
                    LocalRepositoryConfigurationImpl.class);
            if (!config.isEnableNormalizedVersion()) {
                throw new RepoConfigException(NUGET_NORMALISED_SMART_ERR_MSG, HttpStatus.SC_BAD_REQUEST);
            }
        } catch (IOException | URISyntaxException e) {
            throw new RepoConfigException(e.getMessage(), HttpStatus.SC_BAD_REQUEST);
        } finally {
            IOUtils.closeQuietly(response);
        }
    }

    private static boolean isSuccessStatusCode(int statusCode) {
        return (statusCode == SC_OK || statusCode == SC_CREATED || statusCode == SC_NO_CONTENT ||
                statusCode == SC_MOVED_TEMPORARILY);
    }

    @Override
    public RepoType getRepoType() {
        return RepoType.NuGet;
    }

    @Nonnull
    public static String getFetchRepoDetailsUrl(RemoteRepoConfig repoConfig) throws URISyntaxException {
        String remoteUrl = StringUtils.removeEnd(repoConfig.getRepoTypeConfig().getUrl(), "/");
        String remoteRepoKey = PathUtils.getLastPathElement(remoteUrl);
        return buildFetchRepoDetailsUrl(remoteUrl, remoteRepoKey);
    }

    private static String buildFetchRepoDetailsUrl(String remoteUrl, String remoteRepoKey) throws URISyntaxException {
        return getUrlUpToPort(new URI(remoteUrl)) + "/artifactory/api/repositories/" + remoteRepoKey;
    }

    /**
     * This method is return a string which is url till port (omitting the api path)
     * @param uri       -  given a complete uri.
     * @return string which is url till port (omitting the api path)
     */
    private static String getUrlUpToPort(URI uri) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(uri.getScheme()).append("://");
        urlBuilder.append(uri.getHost());
        int port = uri.getPort();
        if (port != -1) {
            urlBuilder.append(":").append(port);
        }
        return urlBuilder.toString();
    }
}
