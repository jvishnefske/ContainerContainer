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

package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpRequest;
import org.apache.http.RequestLine;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.artifactory.addon.common.DefaultRemoteTokenProvider;
import org.artifactory.addon.docker.rest.DockerRemoteTokenProvider;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.repo.http.GuaveCacheConnectionManagersHolder;
import org.artifactory.repo.http.IdleConnectionMonitorService;
import org.artifactory.repo.http.IdleConnectionMonitorServiceImpl;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.test.TestUtils;
import org.artifactory.util.bearer.ArtifactoryBearerScheme;
import org.artifactory.util.bearer.RepoSpecificBearerSchemeFactory;
import org.artifactory.util.http.PreemptiveUtf8AuthInterceptor;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.jfrog.client.http.auth.PreemptiveAuthInterceptor;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;


/**
 * Tests the HTTP client configurator behaviors and conditions.
 * Implementation is package protected so most test must be done using reflection.
 *
 * @author Yossi Shaul
 */
@Test
public class HttpClientConfiguratorTest extends ArtifactoryHomeBoundTest {
    ArtifactoryContext contextMock;

    @BeforeMethod
    public void setUp() throws Exception {
        bindArtifactoryHome();
        getBound().setProperty(ConstantValues.artifactoryProductVersion,
                ArtifactoryServiceVersion.getCurrent().getVersion());
        getBound().setProperty(ConstantValues.idleConnectionMonitorInterval, "10");
        getBound().setProperty(ConstantValues.disableIdleConnectionMonitoring, "false");
        IdleConnectionMonitorService idleConnectionMonitorService =
                new IdleConnectionMonitorServiceImpl(new GuaveCacheConnectionManagersHolder());
        contextMock = mock(InternalArtifactoryContext.class);
        when(contextMock.beanForType(IdleConnectionMonitorService.class))
                .thenReturn(idleConnectionMonitorService);
        when(contextMock.beanForType(DenyListCacheHolderService.class))
                .thenReturn(mock(DenyListCacheHolderService.class));
        ArtifactoryContextThreadBinder.bind(contextMock);
        //EasyMock.replay(contextMock);

        //Version might not have been initialized
        if (StringUtils.isBlank(ConstantValues.artifactoryServiceVersion.getString())) {
            ArtifactoryHome.get().getArtifactoryProperties().
                    setProperty(ConstantValues.artifactoryServiceVersion.getPropertyName(), "momo");
        }
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testHostFromInvalidUrl() {
        new HttpClientConfigurator().hostFromUrl("sttp:/.com");
    }

    public void testTokenAuthentication() throws IOException {
        try (CloseableHttpClient client = new HttpClientConfigurator()
                .host("bob").enableTokenAuthentication(true, null, null, "token",
                        List.of("a", "b")).build()) {
            Registry<AuthSchemeProvider> registry = getAuthSchemeRegistry(client);
            assertThat(registry.lookup("bearer")).isInstanceOf(RepoSpecificBearerSchemeFactory.class);
            RequestConfig defaultConfig = getDefaultConfig(client);
            CredentialsProvider credentialsProvider = getCredentialsProvider(client);
            assertNotNull(credentialsProvider.getCredentials(
                    new AuthScope("a", AuthScope.ANY_PORT, AuthScope.ANY_REALM)));
            assertNotNull(credentialsProvider.getCredentials(
                    new AuthScope("b", AuthScope.ANY_PORT, AuthScope.ANY_REALM)));
            assertThat(defaultConfig.getTargetPreferredAuthSchemes().size()).isEqualTo(1);
            assertThat(defaultConfig.getTargetPreferredAuthSchemes().iterator().next()).isEqualTo("Bearer");
        }
    }

    @Test(dataProvider = "testPkgSupportsBearerTokenProviderDataProvider")
    public void testPkgsTypeSupportsBearerTokenProvider(RepoType pkgType, String password, String expectedToken)
            throws IOException {
        String repoKey = "hf-remote-private-repo-key";
        RepositoryService repoService = Mockito.mock(RepositoryService.class);
        when(ContextHelper.get().beanForType(RepositoryService.class)).thenReturn(repoService);
        when(ContextHelper.get().beanForType(DefaultRemoteTokenProvider.class))
                .thenReturn(new DefaultRemoteTokenProvider(repoService));
        when(ContextHelper.get().beanForType(DockerRemoteTokenProvider.class))
                .thenReturn(new DockerRemoteTokenProvider());
        RemoteRepoConfig remoteRepoConfig = Mockito.mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig remoteRepoTypeConfig = Mockito.mock(RemoteRepositoryRepoTypeConfig.class);
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(remoteRepoTypeConfig);
        when(remoteRepoTypeConfig.getPassword()).thenReturn(password);
        when(repoService.getRemoteRepoConfigByKey(repoKey)).thenReturn(remoteRepoConfig);
        HttpRepoDescriptor repoDescriptor = Mockito.mock(HttpRepoDescriptor.class);
        when(repoDescriptor.getType()).thenReturn(pkgType);
        when(repoService.repoDescriptorByKey(repoKey)).thenReturn(repoDescriptor);
        HttpRequest httpRequest = Mockito.mock(HttpRequest.class);
        RequestLine requestLine = Mockito.mock(RequestLine.class);
        when(httpRequest.getRequestLine()).thenReturn(requestLine);
        try (CloseableHttpClient client = new HttpClientConfigurator().host("bob")
                .enableTokenAuthentication(true, repoKey, null, "token", Collections.emptyList()).build()) {
            Registry<AuthSchemeProvider> registry = getAuthSchemeRegistry(client);
            assertThat(registry.lookup("bearer")).isInstanceOf(RepoSpecificBearerSchemeFactory.class);
            ArtifactoryBearerScheme bearerScheme = (ArtifactoryBearerScheme) registry.lookup("bearer").create(null);
            assertEquals(bearerScheme.getToken(null, httpRequest, null), expectedToken);
            verify(ContextHelper.get(), times(0)).beanForType(DockerRemoteTokenProvider.class);
            verify(ContextHelper.get(), times(1)).beanForType(DefaultRemoteTokenProvider.class);
        }
    }

    public void HttpClientConfigurator_shouldCallInjectedInitializers() {
        HttpClientConfiguratorInitializer mockInitializer = mock();
        when(contextMock.beansForType(HttpClientConfiguratorInitializer.class))
                .thenReturn(Map.of("test", mockInitializer));

        HttpClientConfigurator configurator = new HttpClientConfigurator();
        assertNotNull(configurator);
        verify(mockInitializer).configure(configurator);
    }

    public void testUtf8ForBasicAuthByDefault() {
        HttpClientConfigurator configurator = new HttpClientConfigurator();
        HttpClientBuilder builderMock = Mockito.mock(HttpClientBuilder.class);
        ReflectionTestUtils.setField(configurator, "builder", builderMock);
        configurator.additionalConfigByAuthScheme();
        verify(builderMock).addInterceptorFirst(ArgumentMatchers.any(PreemptiveUtf8AuthInterceptor.class));
    }

    public void testUtf8ForBasicAuthControlledByProperty() {
        try {
            HttpClientConfigurator configurator = new HttpClientConfigurator();
            HttpClientBuilder builderMock = Mockito.mock(HttpClientBuilder.class);
            ReflectionTestUtils.setField(configurator, "builder", builderMock);

            homeStub.setProperty(ConstantValues.enableUtf8ForBasicAuth.getPropertyName(), "false");
            configurator.additionalConfigByAuthScheme();
            verify(builderMock).addInterceptorFirst(
                    // exclude the classes that extend the PreemptiveAuthInterceptor
                    argThat((ArgumentMatcher<PreemptiveAuthInterceptor>) argument ->
                            argument.getClass().isAssignableFrom(PreemptiveAuthInterceptor.class)));

            homeStub.setProperty(ConstantValues.enableUtf8ForBasicAuth.getPropertyName(), "true");
            configurator.additionalConfigByAuthScheme();
            verify(builderMock).addInterceptorFirst(ArgumentMatchers.any(PreemptiveUtf8AuthInterceptor.class));
        } finally {
            homeStub.setProperty(ConstantValues.enableUtf8ForBasicAuth.getPropertyName(), "true");
        }
    }

    private Registry<AuthSchemeProvider> getAuthSchemeRegistry(HttpClient client) {
        return TestUtils.getField(getCloseableHttpClient(client), "authSchemeRegistry", Registry.class);
    }

    private RequestConfig getDefaultConfig(HttpClient client) {
        return TestUtils.getField(getCloseableHttpClient(client), "defaultConfig", RequestConfig.class);
    }

    private CredentialsProvider getCredentialsProvider(HttpClient client) {
        return TestUtils.getField(getCloseableHttpClient(client), "credentialsProvider", CredentialsProvider.class);
    }

    private CloseableHttpClient getCloseableHttpClient(HttpClient client) {
        return TestUtils.getField(client, "closeableHttpClient", CloseableHttpClient.class);
    }

    @DataProvider
    public static Object[][] testPkgSupportsBearerTokenProviderDataProvider() {
        return new Object[][] {
                {RepoType.HuggingFaceML, null, null},
                {RepoType.HuggingFaceML, "", null},
                {RepoType.HuggingFaceML, "passw", "passw"},
                {RepoType.NimModel, null, null},
                {RepoType.NimModel, "", null},
                {RepoType.NimModel, "passw", "passw"},
        };
    }
}
