/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2026 JFrog Ltd.
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

package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.SecurityConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.VirtualRepositoryRepoTypeConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.admin.setmeup.NixTrustedPublicKeyIModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetNixTrustedPublicKeyPerRepoService}.
 *
 * @author Natalie Berni
 */
class GetNixTrustedPublicKeyPerRepoServiceTest {

    private static final String REPO_KEY = "nix-local";
    private static final String KEY_REF = "my-ed25519-key";
    private static final String ALIAS = "test-ed-alias";
    private static final String ED25519_PEM = """
            -----BEGIN PUBLIC KEY-----
            MCowBQYDK2VwAyEAcNpfNfHQEZ+rzOkvB985IKlWFT4nxl+G3HwxV6t92EY=
            -----END PUBLIC KEY-----
            """;
    private static final String EXPECTED_RAW_BASE64 = "cNpfNfHQEZ+rzOkvB985IKlWFT4nxl+G3HwxV6t92EY=";

    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private CentralConfigDescriptor centralConfigDescriptor;
    @Mock
    private ArtifactoryRestRequest<String> request;
    @Mock
    private LocalRepoConfig localConfig;
    @Mock
    private RemoteRepoConfig remoteConfig;

    private RestResponse<NixTrustedPublicKeyIModel> response;
    private GetNixTrustedPublicKeyPerRepoService service;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        response = new ArtifactoryRestResponse<>();
        service = new GetNixTrustedPublicKeyPerRepoService(
                repositoryConfigService, authorizationService, centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void execute_nullRepoKey_returnsErrorAboutMissingParam() {
        when(request.getPathParamByKey(any())).thenReturn(null);

        service.execute(request, response);

        assertErrorContains(response, "No repo param found");
    }

    @Test
    void execute_repoNotFound_returnsErrorAboutMissingRepo() {
        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(null);

        service.execute(request, response);

        assertErrorContains(response, "No such repo '" + REPO_KEY + "'");
    }

    @Test
    void execute_repoIsNotNix_returnsErrorAboutWrongType() {
        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(localConfig);
        when(localConfig.getPackageType()).thenReturn(RepoType.Debian);

        service.execute(request, response);

        assertErrorContains(response, "Repo '" + REPO_KEY + "' is not a Nix repository");
    }

    @Test
    void execute_nixRepoWithoutKeyPair_returnsNullTrustedPublicKey() {
        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(localConfig);
        when(localConfig.getPackageType()).thenReturn(RepoType.Nix);
        when(localConfig.getSecurityConfig()).thenReturn(new SecurityConfig());

        service.execute(request, response);

        NixTrustedPublicKeyIModel iModel = response.getIModel();
        assertNotNull(iModel);
        assertNull(iModel.getTrustedPublicKey());
    }

    @Test
    void execute_nixRepoWithEd25519KeyPair_returnsAliasAndRawBase64() {
        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(localConfig);
        when(localConfig.getPackageType()).thenReturn(RepoType.Nix);
        when(localConfig.getSecurityConfig()).thenReturn(secConfig(KEY_REF));
        when(centralConfigDescriptor.getKeyPair(KEY_REF)).thenReturn(ed25519KeyPair(ALIAS));

        service.execute(request, response);

        NixTrustedPublicKeyIModel iModel = response.getIModel();
        assertNotNull(iModel);
        assertEquals(ALIAS + ":" + EXPECTED_RAW_BASE64, iModel.getTrustedPublicKey());
    }

    @Test
    void execute_nixRepoWithNonEd25519KeyPair_returnsNullTrustedPublicKey() {
        KeyPairDescriptor rsaKp = new KeyPairDescriptor();
        rsaKp.setKey("rsa-key");
        rsaKp.setAlias("rsa-alias");
        rsaKp.setKeyType(KeyPairDescriptor.RSA_TYPE);
        rsaKp.setPublicKey(ED25519_PEM);

        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(localConfig);
        when(localConfig.getPackageType()).thenReturn(RepoType.Nix);
        when(localConfig.getSecurityConfig()).thenReturn(secConfig(KEY_REF));
        when(centralConfigDescriptor.getKeyPair(KEY_REF)).thenReturn(rsaKp);

        service.execute(request, response);

        NixTrustedPublicKeyIModel iModel = response.getIModel();
        assertNotNull(iModel);
        assertNull(iModel.getTrustedPublicKey());
    }

    // ---- Virtual repo cases ----

    @Test
    void execute_virtualNixRepo_aggregatesReadableMembersKeys() {
        String refA = "ref-A";
        String refB = "ref-B";
        String aliasA = "alias-A";
        String aliasB = "alias-B";
        LocalRepoConfig memberA = mockLocalConfig("local-A", refA);
        LocalRepoConfig memberB = mockLocalConfig("local-B", refB);
        VirtualRepoConfig virtual = mockVirtualNix(List.of("local-A", "local-B"));

        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(virtual);
        when(repositoryConfigService.getRepoConfigByKey("local-A")).thenReturn(memberA);
        when(repositoryConfigService.getRepoConfigByKey("local-B")).thenReturn(memberB);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        when(centralConfigDescriptor.getKeyPair(refA)).thenReturn(ed25519KeyPair(aliasA));
        when(centralConfigDescriptor.getKeyPair(refB)).thenReturn(ed25519KeyPair(aliasB));

        service.execute(request, response);

        NixTrustedPublicKeyIModel iModel = response.getIModel();
        assertNotNull(iModel);
        String entryA = aliasA + ":" + EXPECTED_RAW_BASE64;
        String entryB = aliasB + ":" + EXPECTED_RAW_BASE64;
        assertEquals(entryA + " " + entryB, iModel.getTrustedPublicKey());
    }

    @Test
    void execute_virtualNixRepo_filtersOutMembersWithoutReadPermission() {
        String refA = "ref-A";
        String aliasA = "alias-A";
        LocalRepoConfig memberA = mockLocalConfig("local-A", refA);
        LocalRepoConfig memberB = mockLocalConfig("local-B", "ref-B");
        VirtualRepoConfig virtual = mockVirtualNix(List.of("local-A", "local-B"));

        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(virtual);
        when(repositoryConfigService.getRepoConfigByKey("local-A")).thenReturn(memberA);
        when(repositoryConfigService.getRepoConfigByKey("local-B")).thenReturn(memberB);
        when(authorizationService.canRead(argThat(p -> p != null && "local-A".equals(p.getRepoKey()))))
                .thenReturn(true);
        when(authorizationService.canRead(argThat(p -> p != null && "local-B".equals(p.getRepoKey()))))
                .thenReturn(false);
        when(centralConfigDescriptor.getKeyPair(refA)).thenReturn(ed25519KeyPair(aliasA));

        service.execute(request, response);

        NixTrustedPublicKeyIModel iModel = response.getIModel();
        assertNotNull(iModel);
        assertEquals(aliasA + ":" + EXPECTED_RAW_BASE64, iModel.getTrustedPublicKey());
    }

    @Test
    void execute_virtualNixRepo_noReadableMembers_returnsNull() {
        LocalRepoConfig memberA = mockLocalConfig("local-A", "ref-A");
        VirtualRepoConfig virtual = mockVirtualNix(List.of("local-A"));

        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(virtual);
        when(repositoryConfigService.getRepoConfigByKey("local-A")).thenReturn(memberA);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(false);

        service.execute(request, response);

        NixTrustedPublicKeyIModel iModel = response.getIModel();
        assertNotNull(iModel);
        assertNull(iModel.getTrustedPublicKey(),
                "no readable member => null (response must not leak member existence)");
    }

    @Test
    void execute_virtualNixRepo_memberNotFound_skipsIt() {
        // Member key exists in the refs list but getRepoConfigByKey returns null (deleted/misconfigured).
        VirtualRepoConfig virtual = mockVirtualNix(List.of("missing-member"));

        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(virtual);
        when(repositoryConfigService.getRepoConfigByKey("missing-member")).thenReturn(null);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);

        service.execute(request, response);

        NixTrustedPublicKeyIModel iModel = response.getIModel();
        assertNotNull(iModel);
        assertNull(iModel.getTrustedPublicKey());
    }

    @Test
    void execute_virtualNixRepo_skipsReadableMembersWithoutKey() {
        LocalRepoConfig signedMember = mockLocalConfig("local-signed", "signed-ref");
        LocalRepoConfig unsignedMember = mockLocalConfig("local-unsigned", null);
        VirtualRepoConfig virtual = mockVirtualNix(List.of("local-signed", "local-unsigned"));

        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(virtual);
        when(repositoryConfigService.getRepoConfigByKey("local-signed")).thenReturn(signedMember);
        when(repositoryConfigService.getRepoConfigByKey("local-unsigned")).thenReturn(unsignedMember);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        when(centralConfigDescriptor.getKeyPair("signed-ref")).thenReturn(ed25519KeyPair("signed-alias"));

        service.execute(request, response);

        NixTrustedPublicKeyIModel iModel = response.getIModel();
        assertNotNull(iModel);
        assertEquals("signed-alias:" + EXPECTED_RAW_BASE64, iModel.getTrustedPublicKey());
    }

    // ---- Remote repo cases ----

    @Test
    void execute_smartRemoteNixRepo_resolvesKeyFromUpstreamLocalRepo() {
        String upstreamKey = "nix-upstream";
        LocalRepoConfig upstreamConfig = mockLocalConfig(upstreamKey, KEY_REF);

        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(remoteConfig);
        when(remoteConfig.getPackageType()).thenReturn(RepoType.Nix);
        when(remoteConfig.isSmart()).thenReturn(true);
        when(remoteConfig.getUrl()).thenReturn("https://upstream-art/artifactory/" + upstreamKey);
        when(repositoryConfigService.getRepoConfigByKey(upstreamKey)).thenReturn(upstreamConfig);
        when(centralConfigDescriptor.getKeyPair(KEY_REF)).thenReturn(ed25519KeyPair(ALIAS));

        service.execute(request, response);

        NixTrustedPublicKeyIModel iModel = response.getIModel();
        assertNotNull(iModel);
        assertEquals(ALIAS + ":" + EXPECTED_RAW_BASE64, iModel.getTrustedPublicKey());
    }

    @Test
    void execute_smartRemoteNixRepo_upstreamIsNotLocalRepo_returnsNull() {
        // Upstream key resolves to a repo that exists but is not a local repo (e.g. virtual).
        VirtualRepoConfig nonLocalUpstream = org.mockito.Mockito.mock(VirtualRepoConfig.class);

        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(remoteConfig);
        when(remoteConfig.getPackageType()).thenReturn(RepoType.Nix);
        when(remoteConfig.isSmart()).thenReturn(true);
        when(remoteConfig.getUrl()).thenReturn("https://upstream-art/artifactory/nix-virtual");
        when(repositoryConfigService.getRepoConfigByKey("nix-virtual")).thenReturn(nonLocalUpstream);

        service.execute(request, response);

        NixTrustedPublicKeyIModel iModel = response.getIModel();
        assertNotNull(iModel);
        assertNull(iModel.getTrustedPublicKey());
    }

    @Test
    void execute_smartRemoteNixRepo_upstreamNotOnThisInstance_returnsNull() {
        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(remoteConfig);
        when(remoteConfig.getPackageType()).thenReturn(RepoType.Nix);
        when(remoteConfig.isSmart()).thenReturn(true);
        when(remoteConfig.getUrl()).thenReturn("https://other-instance/artifactory/nix-upstream");
        when(repositoryConfigService.getRepoConfigByKey("nix-upstream")).thenReturn(null);

        service.execute(request, response);

        NixTrustedPublicKeyIModel iModel = response.getIModel();
        assertNotNull(iModel);
        assertNull(iModel.getTrustedPublicKey());
    }

    @Test
    void execute_plainRemoteNixRepo_returnsNull() {
        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(remoteConfig);
        when(remoteConfig.getPackageType()).thenReturn(RepoType.Nix);
        when(remoteConfig.isSmart()).thenReturn(false);

        service.execute(request, response);

        NixTrustedPublicKeyIModel iModel = response.getIModel();
        assertNotNull(iModel);
        assertNull(iModel.getTrustedPublicKey());
    }

    @Test
    void execute_virtualNixRepo_smartRemoteMember_resolvesKeyFromUpstream() {
        String upstreamKey = "nix-upstream";
        LocalRepoConfig upstreamConfig = mockLocalConfig(upstreamKey, KEY_REF);
        RemoteRepoConfig smartMember = org.mockito.Mockito.mock(RemoteRepoConfig.class);
        when(smartMember.isSmart()).thenReturn(true);
        when(smartMember.getUrl()).thenReturn("https://upstream-art/artifactory/" + upstreamKey);

        VirtualRepoConfig virtual = mockVirtualNix(List.of("nix-smart-remote"));

        when(request.getPathParamByKey(any())).thenReturn(REPO_KEY);
        when(repositoryConfigService.getRepoConfigByKey(REPO_KEY)).thenReturn(virtual);
        when(repositoryConfigService.getRepoConfigByKey("nix-smart-remote")).thenReturn(smartMember);
        when(repositoryConfigService.getRepoConfigByKey(upstreamKey)).thenReturn(upstreamConfig);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        when(centralConfigDescriptor.getKeyPair(KEY_REF)).thenReturn(ed25519KeyPair(ALIAS));

        service.execute(request, response);

        NixTrustedPublicKeyIModel iModel = response.getIModel();
        assertNotNull(iModel);
        assertEquals(ALIAS + ":" + EXPECTED_RAW_BASE64, iModel.getTrustedPublicKey());
    }

    private static KeyPairDescriptor ed25519KeyPair(String alias) {
        KeyPairDescriptor kp = new KeyPairDescriptor();
        kp.setKey("kp-" + alias);
        kp.setAlias(alias);
        kp.setKeyType(KeyPairDescriptor.ED25519_TYPE);
        kp.setPublicKey(ED25519_PEM);
        return kp;
    }

    private static SecurityConfig secConfig(String primaryKeyPairRef) {
        SecurityConfig sc = new SecurityConfig();
        sc.setPrimaryKeyPairRef(primaryKeyPairRef);
        return sc;
    }

    private static LocalRepoConfig mockLocalConfig(String key, String primaryKeyPairRef) {
        LocalRepoConfig config = org.mockito.Mockito.mock(LocalRepoConfig.class);
        when(config.getSecurityConfig()).thenReturn(secConfig(primaryKeyPairRef));
        return config;
    }

    private static VirtualRepoConfig mockVirtualNix(List<String> memberKeys) {
        VirtualRepoConfig virtual = org.mockito.Mockito.mock(VirtualRepoConfig.class);
        when(virtual.getPackageType()).thenReturn(RepoType.Nix);
        VirtualRepositoryRepoTypeConfig repoTypeConfig =
                org.mockito.Mockito.mock(VirtualRepositoryRepoTypeConfig.class);
        when(repoTypeConfig.getRepositoryRefs()).thenReturn(memberKeys);
        when(virtual.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        return virtual;
    }

    private static void assertErrorContains(RestResponse<NixTrustedPublicKeyIModel> response, String expected) {
        Object entity = response.getEntity();
        assertNotNull(entity, "Expected error entity but got null");
        assertTrue(entity.toString().contains(expected),
                "Expected error to contain <" + expected + "> but was <" + entity + ">");
    }
}
