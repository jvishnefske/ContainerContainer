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


import com.google.common.base.Charsets;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteNetworkRepositoryConfigModel;
import org.artifactory.util.BasicHttpUtils;
import org.artifactory.util.HttpUtils;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.ExceptionLogUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.artifactory.request.ArtifactoryRequest.ARTIFACTORY_ORIGINATED;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;

/**
 * Create Http request for test replication button
 *
 * @author Aviad Shikloshi
 */
@Slf4j
public class TestMethodFactory {

    private static final Set<String> DOCKER_REGISTRIES_TEST_ROOT_ONLY = Set.of("dhi.io");

    private TestMethodFactory() {
    }

    public static HttpRequestBase createTestMethod(String repoUrl, RepoType repoType, @Nullable String queryParams,
            @Nullable RemoteNetworkRepositoryConfigModel networkModel) {

        if (repoType == null) {
            throw new RuntimeException("Missing repository type");
        }
        HttpRequestBase request;
        // Some endpoints will block HEAD, so we want to avoid sending a HEAD request to them
        // For example, VCS can be a Stash/Bitbucket endpoint and these don't allow HEAD
        boolean shouldSendGet = (repoType.equals(repoType.VCS) || repoType.equals(repoType.CocoaPods));
        if (shouldSendGet) {
            request = createGenericGetTestMethod(repoUrl);
            return request;
        }
        // Other special cases (NuGet, Docker...) + default case (HEAD)
        request = switch (repoType) {
            case NuGet -> createNuGetTestMethod(repoUrl, queryParams);
            case HelmOCI, OCI, Docker -> createDockerTestMethod(repoUrl);
            case Npm -> createNpmTestMethod(repoUrl, networkModel);
            case NimModel -> createNimTestMethod(repoUrl, networkModel);
            case AIEditorExtensions -> createAIEditorExtensionsTestMethod(repoUrl);
            default -> new HttpHead(HttpUtils.encodeUrl(repoUrl));
            //Add the current requester host id
        };
        addOriginatedHeader(request);
        return request;
    }

    private static HttpRequestBase createAIEditorExtensionsTestMethod(String repoUrl) {
        HttpRequestBase request = new HttpOptions(HttpUtils.encodeUrl(repoUrl));
        request.addHeader(ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(ORIGIN, "vscode-file://vscode-app");
        return request;
    }

    private static HttpRequestBase createNimTestMethod(String repoUrl,
            RemoteNetworkRepositoryConfigModel networkModel) {
        //standard http HEAD request (fallback)
        HttpRequestBase request = new HttpHead(HttpUtils.encodeUrl(repoUrl));
        if (networkModel == null) {
            return request;
        }
        String password = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), networkModel.getPassword());
        HttpPost postRequest = new HttpPost(HttpUtils.encodeUrl(PathUtils.trimTrailingSlashes(repoUrl) +
                "/v3/keys/get-caller-info"));

        // Create the list of form parameters
        List<BasicNameValuePair> formParams = new ArrayList<>();
        formParams.add(new BasicNameValuePair("credentials", password));
        postRequest.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + password);
        // Set the form parameters to the post request
        UrlEncodedFormEntity entity = null;
        try {
            entity = new UrlEncodedFormEntity(formParams);
        } catch (UnsupportedEncodingException e) {
            // ignore
        }
        postRequest.setEntity(entity);
        return postRequest;
    }

    // Generic GET to the root of the repo URL

    /**
     * add originated header to request
     *
     * @param request - http servlet request
     */
    public static void addOriginatedHeader(HttpRequestBase request) {
        String hostId = ContextHelper.get().beanForType(AddonsManager.class).addonByType(HaCommonAddon.class)
                .getHostId();
        request.addHeader(ARTIFACTORY_ORIGINATED, hostId);
    }

    private static HttpRequestBase createGenericGetTestMethod(String repoUrl) {
        if (repoUrl.endsWith("/")) {
            repoUrl = PathUtils.trimTrailingSlashes(repoUrl);
        }
        return new HttpGet(repoUrl);
    }

    private static HttpRequestBase createNuGetTestMethod(String repoUrl, String queryParams) {
        try {
            URIBuilder uriBuilder = new URIBuilder(repoUrl);
            HttpRequestBase request = new HttpGet();
            if (StringUtils.isNotBlank(queryParams)) {
                List<NameValuePair> queryParamsMap = URLEncodedUtils.parse(queryParams, Charsets.UTF_8);
                uriBuilder.setParameters(queryParamsMap);
            }
            request.setURI(uriBuilder.build());
            return request;
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to build test URI", e);
        }
    }

    /**
     * Docker/OCI/Helm remote test: standard registries are probed with GET url/v2/.
     * Some registries (e.g. dhi.io – Docker Hardened Images) don't expose /v2/ in a way
     * that succeeds for that probe; for those we test GET root and accept 200/302.
     */
    private static HttpRequestBase createDockerTestMethod(String repoUrl) {
        String pathSuffix = getDockerTestPathSuffix(repoUrl);
        String base = PathUtils.trimTrailingSlashes(repoUrl);
        String path = base + pathSuffix;
        return new HttpGet(path);
    }

    private static String getDockerTestPathSuffix(String repoUrl) {
        try {
            String host = new URI(repoUrl).getHost();
            if (host != null && DOCKER_REGISTRIES_TEST_ROOT_ONLY.contains(host.toLowerCase())) {
                return "/";
            }
        } catch (URISyntaxException e) {
            ExceptionLogUtils.debugOrWarn(log,
                    "Malformed URL encountered for docker connection test: " + repoUrl, e);
        }

        return "/v2/";
    }

    private static HttpRequestBase createNpmTestMethod(String repoUrl,
            @Nullable RemoteNetworkRepositoryConfigModel networkModel) {
        //standard http HEAD request (fallback)
        HttpRequestBase request = new HttpHead(BasicHttpUtils.encodeQuery(repoUrl));
        if (networkModel == null) {
            return request;
        }

        String userName = networkModel.getUsername();
        String password = networkModel.getPassword();
        if (StringUtils.isEmpty(userName) || StringUtils.isEmpty(password)) {
            return request;
        }
        HttpGet getRequest =
                new HttpGet(BasicHttpUtils.encodeQuery(PathUtils.trimTrailingSlashes(repoUrl) + "/-/whoami"));
        String basicCreds = userName + ":" + CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), password);
        getRequest.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.encodeBase64String(basicCreds.getBytes()));
        return getRequest;
    }

}
