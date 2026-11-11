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

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteNetworkRepositoryConfigModel;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.result.DecryptionStringResult;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_NIMMODEL_REGISTRY_URL;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Maxim Yurkovsky
 */
public class TestMethodFactoryTest {

    private final String npmRepoUrl = "https://registry.npmjs.org/";

    @Mock
    private AddonsManager addonsManager;

    @Mock
    private HaCommonAddon haCommonAddon;

    @Mock
    private ArtifactoryContext context;

    @Mock
    private ArtifactoryHome artifactoryHomeMock;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(haCommonAddon.getHostId()).thenReturn("4");
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryHome.bind(artifactoryHomeMock);
        EncryptionWrapper encryptionWrapperMock = mock(EncryptionWrapper.class);
        when(artifactoryHomeMock.getArtifactoryEncryptionWrapper()).thenReturn(encryptionWrapperMock);
        when(encryptionWrapperMock.decryptIfNeeded("fakePassword"))
                .thenReturn(new DecryptionStringResult("fakePassword"));
        when(encryptionWrapperMock.decryptIfNeeded("")).thenReturn(new DecryptionStringResult(""));
    }

    @AfterMethod
    public void cleanup() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test(dataProvider = "getFakeCredentialsAndExpectedValues")
    public void testCreateTestMethodForNpmWithOrWithoutCredentials(String fakeUsername, String fakePassword,
            String expectedMethod, String expectedUrl) throws Exception {
        //arrange
        RemoteNetworkRepositoryConfigModel mockedNetworkModel = mock(RemoteNetworkRepositoryConfigModel.class);
        when(mockedNetworkModel.getUsername()).thenReturn(fakeUsername);
        when(mockedNetworkModel.getPassword()).thenReturn(fakePassword);

        //act
        final HttpRequestBase request = TestMethodFactory
                .createTestMethod(npmRepoUrl, RepoType.Npm, null, mockedNetworkModel);
        //assert
        assertEquals(request.getMethod(), expectedMethod);
        assertEquals(request.getURI().toString(), expectedUrl);
    }

    @Test
    public void testCreateTestMethodForNpmWithNetworkModelNull() throws Exception {
        //arrange

        //act
        final HttpRequestBase request = TestMethodFactory
                .createTestMethod(npmRepoUrl, RepoType.Npm, null, null);
        //assert
        assertEquals(request.getMethod(), "HEAD");
        assertEquals(request.getURI().toString(), npmRepoUrl);
    }

    @Test
    public void testCreateTestMethodForNimWithOrWithoutCredentials() throws IOException {
        //arrange
        RemoteNetworkRepositoryConfigModel mockedNetworkModel = mock(RemoteNetworkRepositoryConfigModel.class);
        when(mockedNetworkModel.getPassword()).thenReturn("fakePassword");

        //act
        final HttpRequestBase request = TestMethodFactory
                .createTestMethod(DEFAULT_NIMMODEL_REGISTRY_URL, RepoType.NimModel, null, mockedNetworkModel);
        //assert
        assertEquals(request.getMethod(), "POST");
        assertEquals(request.getURI().toString(), "https://api.ngc.nvidia.com/v3/keys/get-caller-info");
        assertEquals(request.getHeaders("Authorization")[0].getValue(), "Bearer fakePassword");
        HttpEntity entity = ((HttpPost) request).getEntity();
        String body = EntityUtils.toString(entity);
        assertEquals(entity.getContentType().getValue(), "application/x-www-form-urlencoded");
        assertEquals(body, "credentials=fakePassword");
    }

    @Test
    public void testCreateTestMethodForNimDecryptsPassword() {
        // arrange
        final String storedPassword = "storedRawPassword";
        final String decryptedPassword = "decryptedPasswordValue";
        RemoteNetworkRepositoryConfigModel nimNetworkModel = mock(RemoteNetworkRepositoryConfigModel.class);
        when(nimNetworkModel.getPassword()).thenReturn(storedPassword);

        try (MockedStatic<CryptoHelper> cryptoHelperStatic = Mockito.mockStatic(CryptoHelper.class)) {
            cryptoHelperStatic.when(() -> CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), storedPassword))
                    .thenReturn(decryptedPassword);

            // act
            HttpRequestBase nimRequest = TestMethodFactory
                    .createTestMethod(DEFAULT_NIMMODEL_REGISTRY_URL, RepoType.NimModel, null, nimNetworkModel);

            // assert
            assertEquals(nimRequest.getFirstHeader("Authorization").getValue(), "Bearer " + decryptedPassword,
                    "Authorization header should use decrypted password");
        }
    }

    @Test
    public void testCreateTestMethodForAIEditorExtensions() {
        String aiEditorRepoUrl = "https://ai-editor-extensions.example.com/";

        final HttpRequestBase request = TestMethodFactory
                .createTestMethod(aiEditorRepoUrl, RepoType.AIEditorExtensions, null, null);

        assertEquals(request.getMethod(), "OPTIONS");
        assertEquals(request.getURI().toString(), aiEditorRepoUrl);
        assertEquals(request.getFirstHeader("Access-Control-Request-Method").getValue(), "POST");
        assertEquals(request.getFirstHeader("Origin").getValue(), "vscode-file://vscode-app");
    }

    @Test
    public void testCreateTestMethodForDockerDhiIoUsesRootUrl() {
        String dhiIoUrl = "https://dhi.io/";
        final HttpRequestBase request = TestMethodFactory
                .createTestMethod(dhiIoUrl, RepoType.Docker, null, null);
        assertEquals(request.getMethod(), "GET");
        assertEquals(request.getURI().toString(), dhiIoUrl,
                "dhi.io (Docker Hardened Images) should be tested with GET root, not /v2/");
    }

    @Test
    public void testCreateTestMethodForDockerStandardUsesV2() {
        String dockerRepoUrl = "https://registry-1.docker.io/";
        final HttpRequestBase request = TestMethodFactory
                .createTestMethod(dockerRepoUrl, RepoType.Docker, null, null);
        assertEquals(request.getMethod(), "GET");
        assertEquals(request.getURI().toString(), "https://registry-1.docker.io/v2/");
    }

    @DataProvider
    public Object[][] getFakeCredentialsAndExpectedValues() {
        return new Object[][] {
                {"fakeUsername", "fakePassword", "GET", npmRepoUrl + "-/whoami"},
                {"fakeUsername", "", "HEAD", npmRepoUrl},
                {"fakeUsername", null, "HEAD", npmRepoUrl},
                {"", "fakePassword", "HEAD", npmRepoUrl},
                {null, "fakePassword", "HEAD", npmRepoUrl},
                {null, null, "HEAD", npmRepoUrl},
                {"", "", "HEAD", npmRepoUrl}
        };
    }
}