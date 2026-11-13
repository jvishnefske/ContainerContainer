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

package org.artifactory.security.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.config.ImportSettingsImpl;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.api.security.access.ProxyTokenResponse;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.api.security.access.UserTokenSpec;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.group.LdapGroupSetting;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.fs.FileInfo;
import org.artifactory.model.security.UserImpl;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.sapi.common.ImportKind;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.SimpleUser;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.AuthClientImpl;
import org.jfrog.access.client.auth.AuthenticationResult;
import org.jfrog.access.client.auth.MTlsClientImpl;
import org.jfrog.access.client.auth.ldap.AuthSettingsClient;
import org.jfrog.access.client.cert.CertClient;
import org.jfrog.access.client.config.ConfigClient;
import org.jfrog.access.client.model.HttpSsoSetting;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.jfrog.access.client.model.ProxySetting;
import org.jfrog.access.client.model.SamlEncryptedAssertion;
import org.jfrog.access.client.model.SamlSetting;
import org.jfrog.access.client.platformconfig.GrpcPlatformConfigClient;
import org.jfrog.access.client.registry.RegistryClient;
import org.jfrog.access.client.system.SystemClient;
import org.jfrog.access.client.token.TokenClient;
import org.jfrog.access.client.token.TokenRequest;
import org.jfrog.access.client.token.TokenResponseImpl;
import org.jfrog.access.rest.system.LdapConfig;
import org.jfrog.access.rest.system.MTlsConfigurationModel;
import org.jfrog.access.router.RouterGrpcClient;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.JwtAccessTokenImpl;
import org.jfrog.access.token.verifier.VerifyFailureReason;
import org.jfrog.access.util.JoinKeyUtils;
import org.jfrog.client.http.RestResponse;
import org.jfrog.security.crypto.JFrogCryptoHelper;
import org.jfrog.security.ssl.CertificateGenerationException;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.artifactory.mpu.MultipartUploadService.MPU_SCOPE;
import static org.artifactory.security.access.AppliedPermissionsScopeToken.APPLIED_PERMISSIONS_GROUPS_PREFIX;
import static org.artifactory.security.access.AppliedPermissionsScopeToken.APPLIED_PERMISSIONS_USER;
import static org.artifactory.test.TestUtils.createCertificate;
import static org.easymock.EasyMock.createNiceMock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * @author Yinon Avraham
 */
public class AccessServiceImplTest extends ArtifactoryHomeBoundTest {

    private static final CharSequence TEST_JFROG_JOIN_KEY =
            "cc949ef041b726994a225dc20e018f2310101010101010101010101010101010";
    //default value of ConstantValues.accessTokenNonAdminMaxExpiresIn
    private static long constValueDefaultMaxExpiresIn = TimeUnit.HOURS.toSeconds(1);
    private static long USER_TOKEN_MAX_EXPIRES_IN_MINUTES_DEFAULT =
            TimeUnit.SECONDS.toMinutes(constValueDefaultMaxExpiresIn);

    private static MockedStatic<AccessConfigHolder> staticAccessConfigHolder;
    private static final AccessConfigHolder spyAccessConfigHolder = Mockito.spy(AccessConfigHolder.class);

    @BeforeClass
    static void beforeAll() {
        staticAccessConfigHolder = Mockito.mockStatic(AccessConfigHolder.class);
        //noinspection ResultOfMethodCallIgnored
        staticAccessConfigHolder.when(AccessConfigHolder::getInstance).thenReturn(spyAccessConfigHolder);
    }

    @AfterClass
    static void afterAll() {
        staticAccessConfigHolder.close();
    }

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private UserGroupStoreService userGroupStore;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private UserGroupService userGroupService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ApplicationEventPublisher publisher;
    @Mock
    private ConfigsService configsService;
    @Mock
    private AccessClient accessClient;
    @Mock
    private ArtifactoryApplicationContext applicationContext;
    @Mock
    private SystemClient systemClient;
    @Mock
    private GrpcPlatformConfigClient platformConfigClient;
    @Mock
    private AuthSettingsClient authSettingsClient;
    private AccessServiceImpl accessServiceSpy;
    private AutoCloseable mocks;

    @BeforeMethod
    public void resetAll() throws CertificateGenerationException, IOException {
        mocks = openMocks(this);

        Mockito.reset(spyAccessConfigHolder);

        when(applicationContext.getContextPath()).thenReturn("artifactory");
        AccessServiceImpl accessService =
                new AccessServiceImpl(publisher, configsService, applicationContext);
        accessService.setAddonsManager(addonsManager);
        accessService.setSecurityService(securityService);
        accessService.setAuthorizationService(authorizationService);
        accessService.setUserGroupService(userGroupService);
        accessService.setUserGroupStore(userGroupStore);
        accessService.setRepositoryService(repositoryService);
        accessService.setAuthFailureMaxRetry(2);


        accessServiceSpy = Mockito.spy(accessService);
        when(accessClient.useAuth(argThat(i -> true))).thenReturn(accessClient);
        when(accessClient.useAuth(argThat(i -> true), argThat(i -> true))).thenReturn(accessClient);
        TokenClient tokenClient = mock(TokenClient.class);
        //noinspection ConstantConditions
        when(tokenClient.create(argThat(i -> true)))
                .thenReturn(new TokenResponseImpl("blah", "blah", "blah", Long.MAX_VALUE, null));
        when(accessClient.token()).thenReturn(tokenClient);
        systemClient = mock(SystemClient.class);
        when(systemClient.getAccessServerName()).thenReturn("blah");
        when(systemClient.getStartupPingRetries()).thenReturn(5);
        when(accessClient.system()).thenReturn(systemClient);
        RestResponse restResponse = mock(RestResponse.class);
        when(restResponse.isSuccessful()).thenReturn(true);
        when(restResponse.getBodyAsString()).thenReturn("{\"token\":\"aToken\"}");
        when(accessClient.restCall(argThat(i -> true))).thenReturn(restResponse);
        CertClient certClient = mock(CertClient.class);

        Certificate certificate = createCertificate(JFrogCryptoHelper.generateKeyPair());
        when(certClient.getRootCertificate()).thenReturn(certificate);
        when(accessClient.cert()).thenReturn(certClient);
        when(accessClient.router()).thenReturn(mock(RouterGrpcClient.class));

        //newClientBuilder()->getAccessServerSettings()->detectBundledAccessServerUrl() is hard to mock, so we stub
        doReturn(accessClient).when(accessServiceSpy).buildAccessClient();

        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        ConfigClient configClient = mock(ConfigClient.class);
        when(accessClient.config()).thenReturn(configClient);

        MTlsClientImpl mTlsClient = mock(MTlsClientImpl.class);
        AuthClientImpl authClient = mock(AuthClientImpl.class);

        when(accessClient.cachedAuth()).thenReturn(authClient);
        when(accessClient.cachedAuth().mtls()).thenReturn(mTlsClient);

        RegistryClient registryClient = mock(RegistryClient.class);
        SecretKey secretKey =
                new SecretKeySpec(JoinKeyUtils.getSigningKey(""), JWSAlgorithm.HS256.getName());
        String secretKeyId = JoinKeyUtils.getKeyId("");
        String tokenValue =
                JwtAccessTokenImpl.builder()
                        .buildAndSign(secretKey, secretKeyId).getTokenValue();
        when(registryClient.join(anyString())).thenReturn(new ObjectMapper().readTree
                ("{\"token\":\"" + tokenValue + "\"}"));
        when(accessClient.registry()).thenReturn(registryClient);
        File joinKeyFile = new File(ArtifactoryHome.get().getSysConfig().helper().getJoinKeyFile());
        FileUtils.write(joinKeyFile, TEST_JFROG_JOIN_KEY);
        when(accessClient.cachedGrpcPlatformConfigs()).thenReturn(platformConfigClient);
        accessServiceSpy.init();

        ArtifactoryContextThreadBinder.bind(applicationContext);
    }

    @AfterMethod
    public void afterMethod() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void updateConfigToAccessHolderEmptyOnlyDefaultProxyUpdated() {
        when(spyAccessConfigHolder.isEmpty()).thenReturn(true);
        when(spyAccessConfigHolder.isNullLdapConfig()).thenReturn(true);
        when(spyAccessConfigHolder.drainLdapSetting()).thenReturn(List.of());
        when(spyAccessConfigHolder.drainLdapGroupSetting()).thenReturn(List.of());
        when(spyAccessConfigHolder.isNullHttpSsoSetting()).thenReturn(true);
        when(spyAccessConfigHolder.drainProxySettings()).thenReturn(new ProxyDescriptor());
        when(spyAccessConfigHolder.isNullOAuthGeneralSetting()).thenReturn(true);
        when(spyAccessConfigHolder.isNullOAuthSettings()).thenReturn(true);
        when(spyAccessConfigHolder.drainOAuthGeneralSettings()).thenReturn(null);
        when(spyAccessConfigHolder.drainOAuthSetting()).thenReturn(List.of());

        when(accessServiceSpy.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);

        accessServiceSpy.updateConfigurationToAccess();

        verify(authSettingsClient).upsertProxySetting(any());
        verifyNoMoreInteractions(authSettingsClient);
    }

    @SneakyThrows
    @Test
    public void updateConfigToAccessHolderFullAllUpdated() {
        KeyPair keyPair = JFrogCryptoHelper.generateKeyPair();
        Certificate certificate = createCertificate(keyPair);
        SamlEncryptedAssertion samlEncryptedAssertion = SamlEncryptedAssertion.builder()
                .certificate(certificate)
                .privateKey(keyPair.getPrivate())
                .build();
        when(spyAccessConfigHolder.isEmpty()).thenReturn(false);
        Map<String, Object> accessConfigMap = Map.of("DescriptorKey", "DescriptorValue");
        when(spyAccessConfigHolder.drainConfig()).thenReturn(accessConfigMap);
        when(spyAccessConfigHolder.isNullLdapConfig()).thenReturn(false);
        LdapConfig ldapConfig = mock(LdapConfig.class);
        when(spyAccessConfigHolder.drainLdapConfig()).thenReturn(ldapConfig);
        when(spyAccessConfigHolder.drainLdapSetting()).thenReturn(List.of(new LdapSetting()));
        when(spyAccessConfigHolder.drainLdapGroupSetting()).thenReturn(List.of(new LdapGroupSetting()));
        when(spyAccessConfigHolder.isNullHttpSsoSetting()).thenReturn(false);
        when(spyAccessConfigHolder.drainHttpSsoSetting()).thenReturn(mock(HttpSsoSetting.class));
        when(spyAccessConfigHolder.drainProxySettings()).thenReturn(new ProxyDescriptor());
        when(spyAccessConfigHolder.isNullSamlSetting()).thenReturn(false);
        when(spyAccessConfigHolder.drainSamlSetting()).thenReturn(SamlSetting.builder().name("okta")
                .enableIntegration(false)
                .emailAttribute("mail").build());
        when(spyAccessConfigHolder.isNullSamlEncryption()).thenReturn(false);
        when(spyAccessConfigHolder.drainSamlEncryptedAssertion()).thenReturn(samlEncryptedAssertion);
        when(spyAccessConfigHolder.isNullOAuthGeneralSetting()).thenReturn(false);
        when(spyAccessConfigHolder.isNullOAuthSettings()).thenReturn(false);
        when(spyAccessConfigHolder.drainOAuthGeneralSettings()).thenReturn(
                OAuthGeneralSettings.builder().enable(true).defaultSetting("npm").build());
        when(spyAccessConfigHolder.drainOAuthSetting()).thenReturn(
                List.of(OAuthSetting.builder().name("oauth1").build()));

        when(accessServiceSpy.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);

        accessServiceSpy.updateConfigurationToAccess();

        verify(accessServiceSpy).updateConfigurationModel(accessConfigMap);
        verify(authSettingsClient).updateLdapConfig(ldapConfig);
        verify(authSettingsClient).upsertLdapSetting(anyList());
        verify(authSettingsClient).upsertLdapGroupSettings(anyCollection());
        verify(authSettingsClient).upsertHttpSsoSetting(any());
        verify(authSettingsClient).upsertProxySetting(any());
        verify(authSettingsClient).upsertSamlEncryptedAssertion(any());
        verify(authSettingsClient).upsertSamlSetting(any());
        verify(authSettingsClient).upsertOAuthGeneralSettings(any());
        verify(authSettingsClient).upsertOAuthSettings(anyCollection());
        verifyNoMoreInteractions(authSettingsClient);
    }

    @SneakyThrows
    @Test
    public void updateConfigToAccessHolderOnlySaml() {
        KeyPair keyPair = JFrogCryptoHelper.generateKeyPair();
        Certificate certificate = createCertificate(keyPair);
        SamlEncryptedAssertion samlEncryptedAssertion = SamlEncryptedAssertion.builder()
                .certificate(certificate)
                .privateKey(keyPair.getPrivate())
                .build();
        when(spyAccessConfigHolder.isEmpty()).thenReturn(false);
        when(spyAccessConfigHolder.drainConfig()).thenReturn(null);
        Map<String, Object> accessConfigMap = Map.of("DescriptorKey", "DescriptorValue");
        when(spyAccessConfigHolder.drainConfig()).thenReturn(accessConfigMap);
        when(spyAccessConfigHolder.isNullSamlSetting()).thenReturn(false);
        when(spyAccessConfigHolder.drainSamlSetting()).thenReturn(SamlSetting.builder().name("okta")
                .enableIntegration(false)
                .emailAttribute("mail").build());
        when(spyAccessConfigHolder.isNullSamlEncryption()).thenReturn(false);
        when(spyAccessConfigHolder.drainSamlEncryptedAssertion()).thenReturn(samlEncryptedAssertion);

        when(accessServiceSpy.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);
        doThrow(new ForbiddenException("")).when(authSettingsClient).upsertSamlEncryptedAssertion(any());
        accessServiceSpy.updateConfigurationToAccess();

        verify(authSettingsClient).upsertProxySetting(any());
        verify(authSettingsClient).upsertSamlEncryptedAssertion(any());
        verify(authSettingsClient).upsertSamlSetting(any());
        verifyNoMoreInteractions(authSettingsClient);
    }

    @Test
    public void updateConfigToAccessHolderFullAllUpdatedWithoutEncryptedAssertion() throws IOException {
        when(spyAccessConfigHolder.isEmpty()).thenReturn(false);
        Map<String, Object> accessConfigMap = Map.of("DescriptorKey", "DescriptorValue");
        when(spyAccessConfigHolder.drainConfig()).thenReturn(accessConfigMap);
        when(spyAccessConfigHolder.isNullLdapConfig()).thenReturn(false);
        LdapConfig ldapConfig = mock(LdapConfig.class);
        when(spyAccessConfigHolder.drainLdapConfig()).thenReturn(ldapConfig);
        when(spyAccessConfigHolder.drainLdapSetting()).thenReturn(List.of(new LdapSetting()));
        when(spyAccessConfigHolder.drainLdapGroupSetting()).thenReturn(List.of(new LdapGroupSetting()));
        when(spyAccessConfigHolder.isNullHttpSsoSetting()).thenReturn(false);
        when(spyAccessConfigHolder.drainHttpSsoSetting()).thenReturn(mock(HttpSsoSetting.class));
        when(spyAccessConfigHolder.drainProxySettings()).thenReturn(new ProxyDescriptor());
        when(spyAccessConfigHolder.isNullSamlSetting()).thenReturn(false);
        when(spyAccessConfigHolder.drainSamlSetting()).thenReturn(SamlSetting.builder().name("okta")
                .enableIntegration(false)
                .emailAttribute("mail").build());

        when(accessServiceSpy.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);

        accessServiceSpy.updateConfigurationToAccess();

        verify(accessServiceSpy).updateConfigurationModel(accessConfigMap);
        verify(authSettingsClient).updateLdapConfig(ldapConfig);
        verify(authSettingsClient).upsertLdapSetting(anyList());
        verify(authSettingsClient).upsertLdapGroupSettings(anyCollection());
        verify(authSettingsClient).upsertHttpSsoSetting(any());
        verify(authSettingsClient).upsertProxySetting(any());
        verify(authSettingsClient).upsertSamlSetting(any());
        verifyNoMoreInteractions(authSettingsClient);
    }

    private void stubMinimalHolderForUpdateConfigToAccess() {
        when(spyAccessConfigHolder.isEmpty()).thenReturn(true);
        when(spyAccessConfigHolder.isNullLdapConfig()).thenReturn(true);
        when(spyAccessConfigHolder.drainLdapSetting()).thenReturn(List.of());
        when(spyAccessConfigHolder.drainLdapGroupSetting()).thenReturn(List.of());
        when(spyAccessConfigHolder.isNullHttpSsoSetting()).thenReturn(true);
        when(spyAccessConfigHolder.drainProxySettings()).thenReturn(new ProxyDescriptor());
        when(spyAccessConfigHolder.isNullOAuthGeneralSetting()).thenReturn(true);
        when(spyAccessConfigHolder.isNullOAuthSettings()).thenReturn(true);
        when(spyAccessConfigHolder.drainOAuthGeneralSettings()).thenReturn(null);
        when(spyAccessConfigHolder.drainOAuthSetting()).thenReturn(List.of());
    }

    @Test
    public void updateAllProxies_nullDrained_skipsPlatformConfigPush() {
        stubMinimalHolderForUpdateConfigToAccess();
        when(spyAccessConfigHolder.drainAllProxies()).thenReturn(null);
        when(accessServiceSpy.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);

        accessServiceSpy.updateConfigurationToAccess();

        verify(platformConfigClient, never()).setConfig(anyString(), any(), anyBoolean());
    }

    @Test
    public void updateAllProxies_emptyList_pushesEmptyPayloadUnderProxySettingsAll() {
        stubMinimalHolderForUpdateConfigToAccess();
        when(spyAccessConfigHolder.drainAllProxies()).thenReturn(List.of());
        when(accessServiceSpy.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);

        accessServiceSpy.updateConfigurationToAccess();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(platformConfigClient).setConfig(eq("proxy.settings.all"), payload.capture(), eq(false));
        assertTrue(((List<?>) payload.getValue()).isEmpty());
    }

    @Test
    public void updateAllProxies_proxyDescriptors_mappedToProxySettingsAndPushed() {
        ProxyDescriptor proxy = new ProxyDescriptor();
        proxy.setKey("p1");
        proxy.setHost("host.example");
        proxy.setPort(8080);
        proxy.setUsername("user");
        proxy.setPassword("secret");
        proxy.setNtHost("nt");
        proxy.setDomain("dom");
        proxy.setPlatformDefault(true);
        proxy.setRedirectedToHosts("redirect.example");
        proxy.setServices("jfrt,jfxr");

        stubMinimalHolderForUpdateConfigToAccess();
        when(spyAccessConfigHolder.drainAllProxies()).thenReturn(List.of(proxy));
        when(accessServiceSpy.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);

        accessServiceSpy.updateConfigurationToAccess();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(platformConfigClient).setConfig(eq("proxy.settings.all"), payload.capture(), eq(false));
        @SuppressWarnings("unchecked")
        List<ProxySetting> pushed = (List<ProxySetting>) payload.getValue();
        assertEquals(pushed.size(), 1);
        ProxySetting setting = pushed.get(0);
        assertEquals(setting.getKey(), "p1");
        assertEquals(setting.getHost(), "host.example");
        assertEquals(setting.getPort(), 8080);
        assertEquals(setting.getUsername(), "user");
        assertEquals(setting.getPassword(), "secret");
        assertEquals(setting.getNtHost(), "nt");
        assertEquals(setting.getDomain(), "dom");
        assertTrue(setting.isPlatformDefault());
        assertEquals(setting.getRedirectedToHosts(), "redirect.example");
        assertEquals(setting.getServices(), "jfrt,jfxr");
    }

    @Test
    public void updateAllProxies_filtersNullEntriesAndProxiesWithNullOrBlankKeys() {
        ProxyDescriptor valid = new ProxyDescriptor();
        valid.setKey("valid");
        valid.setHost("h");
        valid.setPort(80);
        ProxyDescriptor nullKey = new ProxyDescriptor();
        ProxyDescriptor blankKey = new ProxyDescriptor();
        blankKey.setKey("   ");

        ArrayList<ProxyDescriptor> proxies = new ArrayList<>();
        proxies.add(valid);
        proxies.add(null);
        proxies.add(nullKey);
        proxies.add(blankKey);

        stubMinimalHolderForUpdateConfigToAccess();
        when(spyAccessConfigHolder.drainAllProxies()).thenReturn(proxies);
        when(accessServiceSpy.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);

        accessServiceSpy.updateConfigurationToAccess();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(platformConfigClient).setConfig(eq("proxy.settings.all"), payload.capture(), eq(false));
        @SuppressWarnings("unchecked")
        List<ProxySetting> pushed = (List<ProxySetting>) payload.getValue();
        assertEquals(pushed.size(), 1);
        assertEquals(pushed.get(0).getKey(), "valid");
    }

    @Test
    public void updateAllProxies_platformConfigClientThrows_failureSwallowed() {
        ProxyDescriptor proxy = new ProxyDescriptor();
        proxy.setKey("p1");
        proxy.setHost("h");
        proxy.setPort(80);

        stubMinimalHolderForUpdateConfigToAccess();
        when(spyAccessConfigHolder.drainAllProxies()).thenReturn(List.of(proxy));
        when(accessServiceSpy.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);

        doThrow(new AccessClientException("boom"))
                .when(platformConfigClient).setConfig(anyString(), any(), anyBoolean());

        accessServiceSpy.updateConfigurationToAccess();

        verify(platformConfigClient).setConfig(eq("proxy.settings.all"), any(), eq(false));
    }

    @Test
    public void testUpsertEncryptedAssertion() throws CertificateGenerationException {
        KeyPair keyPair = JFrogCryptoHelper.generateKeyPair();
        Certificate certificate = createCertificate(keyPair);
        when(accessServiceSpy.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);
        accessServiceSpy.upsertSamlEncryptedAssertion(SamlEncryptedAssertion.builder()
                .override(true)
                .certificate(certificate)
                .privateKey(keyPair.getPrivate()).build());
    }

    @Test(dataProvider = "provideNormalizeInstanceId")
    public void testNormalizeInstanceId(String original, String expected) {
        assertEquals(AccessServiceImpl.normalizeInstanceId(original), expected);
    }

    @Test(expectedExceptions = {IllegalArgumentException.class})
    public void testNormalizeInstanceIdTooShort() {
        AccessServiceImpl.normalizeInstanceId("foo");
    }

    @Test(dataProvider = "provideValidExpiresInForNonAdmin")
    public void testAssertExpiresInForNonAdminUsers(boolean nonnull, Long maxExpiresIn, Long expiresIn,
            Long maxExpiresInConstValue) {
        Long finalExpire = maxExpiresInConstValue;
        if (nonnull) {
            finalExpire = Optional.ofNullable(maxExpiresIn)
                    .map(TimeUnit.MINUTES::toSeconds)
                    .orElse(maxExpiresInConstValue);
        }
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.accessTokenNonAdminMaxExpiresIn.getPropertyName(),
                        finalExpire.toString());

        TokenRequest tokenRequest = TokenRequest.scopes("api:*").nonRefreshable().expiresIn(expiresIn).build();
        accessServiceSpy.assertValidExpiresInForNonAdmin(tokenRequest, "testuser", false);
    }

    @Test
    public void createTokenForMcScopeToken() {
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.hasMcScope()).thenReturn(true);
        TokenSpec<UserTokenSpec> tokenSpec = UserTokenSpec.create("test").scope(List.of("api:test:r"));
        CreatedTokenInfo tokenInfo = accessServiceSpy.createToken(tokenSpec,
                true, true);
        assertEquals(tokenInfo != null, true);

    }

    // IA5: no token for a user who must change the password, whoever asks for it
    @Test
    public void createTokenRefusedForUserWhoMustChangePassword() {
        when(securityService.isAdmin()).thenReturn(true);
        UserImpl flagged = new UserImpl("test");
        flagged.setMustChangePassword(true);
        when(userGroupService.findUser("test", false)).thenReturn(flagged);
        TokenSpec<UserTokenSpec> tokenSpec = UserTokenSpec.create("test").scope(List.of("api:*"));
        AuthorizationException refused = Assertions.assertThrows(AuthorizationException.class,
                () -> accessServiceSpy.createToken(tokenSpec, true, true));
        assertTrue(refused.getMessage().contains("password change required"));
        Mockito.verify(accessClient.token(), Mockito.never()).create(any());
    }

    @Test
    public void createTokenAllowedForUnflaggedUser() {
        when(securityService.isAdmin()).thenReturn(true);
        when(userGroupService.findUser("test", false)).thenReturn(new UserImpl("test"));
        TokenSpec<UserTokenSpec> tokenSpec = UserTokenSpec.create("test").scope(List.of("api:*"));
        assertNotNull(accessServiceSpy.createToken(tokenSpec, true, true));
    }

    @Test
    public void createTokenForNonAdminWithMpuScope() {
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.currentUsername()).thenReturn("test");
        when(userGroupService.currentUser()).thenReturn(new UserImpl("test"));
        TokenSpec<UserTokenSpec> tokenSpec =
                UserTokenSpec.create("test").scope(List.of(MPU_SCOPE));
        Assertions.assertThrows(AuthorizationException.class, () -> accessServiceSpy.createToken(tokenSpec,
                true, true));
    }

    // RTDEV-92173: a non-admin user must not be able to smuggle an unvalidated group via a
    // "member-of-groups:" clause riding alongside a legitimate "applied-permissions/groups:" clause.
    // Before the fix, AccessTokenAuthenticationProvider#collectGroupNamesFromScope() only inspected the
    // applied-permissions/groups clause when both were present, so the requested "admin-group" (which the
    // user is NOT a member of) was never checked by assertValidGroupsForNonAdmin() and the token was minted.
    @Test
    public void createTokenForNonAdmin_smuggledMemberOfGroupsClause_throwsAuthorizationException() {
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.currentUsername()).thenReturn("test");
        UserImpl testUser = new UserImpl("test");
        testUser.addGroup("anchor-group");
        when(userGroupService.currentUser()).thenReturn(testUser);

        // "test" is a genuine member of "anchor-group" (the legitimate anchor) but not of "admin-group"
        // (the group being smuggled in via the member-of-groups clause).
        TokenSpec<UserTokenSpec> tokenSpec = UserTokenSpec.create("test")
                .scope(List.of("applied-permissions/groups:anchor-group", "member-of-groups:admin-group"));

        Assertions.assertThrows(AuthorizationException.class,
                () -> accessServiceSpy.createToken(tokenSpec, true, true));
    }

    // Non-regression companion: when the user is a genuine member of every group named across both clause
    // types, the combined-clause scope must still be honored - the fix must not over-reject legitimate requests.
    @Test
    public void createTokenForNonAdmin_combinedScopeWithGenuineMembership_succeeds() {
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.currentUsername()).thenReturn("test");
        UserImpl testUser = new UserImpl("test");
        testUser.addGroup("anchor-group");
        testUser.addGroup("readers");
        when(userGroupService.currentUser()).thenReturn(testUser);

        TokenSpec<UserTokenSpec> tokenSpec = UserTokenSpec.create("test")
                .scope(List.of("applied-permissions/groups:anchor-group", "member-of-groups:readers"));

        CreatedTokenInfo tokenInfo = accessServiceSpy.createToken(tokenSpec, true, true);

        assertNotNull(tokenInfo);
    }

    @Test(dataProvider = "provideInvalidExpiresInForNonAdmin", expectedExceptions = AuthorizationException.class)
    public void failAssertExpiresInForNonAdminUsers(boolean nonnull, Long maxExpiresIn, Long expiresIn,
            Long maxExpiresInConstValue) {
        testAssertExpiresInForNonAdminUsers(nonnull, maxExpiresIn, expiresIn, maxExpiresInConstValue);
    }


    @Test
    public void testAccessTimeOutDuringBootstrap() throws Exception {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.accessClientWaitForServer.getPropertyName(), "2");
        doThrow(new AccessClientException("message")).when(this.accessClient).ping();
        try {
            accessServiceSpy.initAccessService("service-id");
        } catch (Exception e) {
        }
        verify(this.accessClient, times(0)).close();
    }

    @Test(expectedExceptions = AccessClientException.class,
            expectedExceptionsMessageRegExp = "HTTP response status 401:Test")
    public void testEnsureAuthMaxRetriesReached() {
        accessServiceSpy.ensureAuth(() -> {
            throw new AccessClientHttpException(HttpStatus.SC_UNAUTHORIZED, "Test");
        });
    }

    @Test
    public void testEnsureAuth2ndCallSuccess() {
        AtomicBoolean failedOnce = new AtomicBoolean(false);
        String result = accessServiceSpy.ensureAuth(() -> {
            if (!failedOnce.get()) {
                failedOnce.set(true);
                throw new AccessClientHttpException(HttpStatus.SC_UNAUTHORIZED, "Test");
            } else {
                return "Test";
            }
        });
        Assert.assertEquals(result, "Test");
    }

    @Test
    public void testExtractMtlsUsername() {
        String expectedUsername = "vova";

        AuthenticationResult<String> extractedUserName = new AuthenticationResult<>() {
            @Nullable
            @Override
            public String getPrincipal() {
                return expectedUsername;
            }

            @Override
            public boolean isSuccessful() {
                return true;
            }

            @Nonnull
            @Override
            public String getReason() {
                return "none";
            }

            @Override
            public String getCookie() {
                return "none";
            }
        };
        when(addonsManager.isEntitled(EntitlementType.MTLS)).thenReturn(true);
        when(accessClient.cachedAuth().mtls().extractUsername(any())).thenReturn(extractedUserName);
        HttpServletRequest request = createNiceMock(HttpServletRequest.class);
        String actualUsername = accessServiceSpy.extractMtlsUsername(request);
        verify(accessClient.cachedAuth().mtls()).extractUsername(any());

        assertEquals(actualUsername, expectedUsername);
    }

    @Test
    public void testExtractMtlsUsernameNotEntitled() {
        String expectedUsername = "vova";

        AuthenticationResult<String> extractedUserName = new AuthenticationResult<>() {
            @Nullable
            @Override
            public String getPrincipal() {
                return expectedUsername;
            }

            @Override
            public boolean isSuccessful() {
                return true;
            }

            @Nonnull
            @Override
            public String getReason() {
                return "none";
            }

            @Override
            public String getCookie() {
                return "none";
            }
        };
        when(addonsManager.isEntitled(EntitlementType.MTLS)).thenReturn(false);
        when(accessClient.cachedAuth().mtls().extractUsername(any())).thenReturn(extractedUserName);
        HttpServletRequest request = createNiceMock(HttpServletRequest.class);
        String actualUsername = accessServiceSpy.extractMtlsUsername(request);
        verify(accessClient.cachedAuth().mtls(), never()).extractUsername(any());

        assertNull(actualUsername);
    }

    @Test
    public void testExtractMtlsUsernameNull() {

        AuthenticationResult<String> extractedUserName = new AuthenticationResult<>() {
            @Nullable
            @Override
            public String getPrincipal() {
                return null;
            }

            @Override
            public boolean isSuccessful() {
                return false;
            }

            @Nonnull
            @Override
            public String getReason() {
                return "some fake reason";
            }

            @Override
            public String getCookie() {
                return "none";
            }
        };
        when(addonsManager.isEntitled(EntitlementType.MTLS)).thenReturn(true);
        when(accessClient.cachedAuth().mtls().extractUsername(any())).thenReturn(extractedUserName);
        HttpServletRequest request = createNiceMock(HttpServletRequest.class);
        String actualUsername = accessServiceSpy.extractMtlsUsername(request);
        assertNull(actualUsername);
    }

    @Test
    public void testIsMtlsEnabled() {
        when(addonsManager.isEntitled(EntitlementType.MTLS)).thenReturn(true);
        when(accessClient.config().getMTlsConfiguration()).thenReturn(
                MTlsConfigurationModel.builder().enabled(true).build());
        assertTrue(accessServiceSpy.isMtlsEnabled());
    }

    @Test
    public void testIsMtlsEnabledConfigNull() {
        when(addonsManager.isEntitled(EntitlementType.MTLS)).thenReturn(true);
        when(accessClient.config().getMTlsConfiguration()).thenReturn(null);
        assertFalse(accessServiceSpy.isMtlsEnabled());
    }

    @Test
    public void testIsMtlsEnabledConfigDisabled() {
        when(addonsManager.isEntitled(EntitlementType.MTLS)).thenReturn(true);
        when(accessClient.config().getMTlsConfiguration()).thenReturn(
                MTlsConfigurationModel.builder().enabled(false).build());
        assertFalse(accessServiceSpy.isMtlsEnabled());
    }

    @Test
    public void testIsMtlsEnabledNotEntitled() {
        when(addonsManager.isEntitled(EntitlementType.MTLS)).thenReturn(false);
        assertFalse(accessServiceSpy.isMtlsEnabled());
    }

    @Test(expectedExceptions = AccessClientException.class,
            expectedExceptionsMessageRegExp = "java.lang.Exception: Test")
    public void testEnsureAuthFailsByCodeException() {
        accessServiceSpy.ensureAuth(() -> {
            throw new Exception("Test");
        });
    }

    @Test
    public void testAssertAcceptedScopePermissionScopes() {
        List<String> scopes =
                List.of("system:health:r", "system:livelogs:r", "system:health:r", "system:supportbundle:x");
        accessServiceSpy.assertAcceptedScope(scopes);
        assertThrows(IllegalArgumentException.class,
                () -> accessServiceSpy.assertAcceptedScope(Collections.singletonList("invalid:health:r")));
        assertThrows(IllegalArgumentException.class,
                () -> accessServiceSpy
                        .assertAcceptedScope(Collections.singletonList("system:health:somethinginvalid")));
    }

    @Test
    public void whenHybridMigrationThenSaasImportPerformed() throws IOException {
        ImportSettingsImpl importSettings = new ImportSettingsImpl(null);
        importSettings.setImportKind(ImportKind.HYBRID_MIGRATION);

        accessServiceSpy.performImport(importSettings);

        verify(systemClient, times(0)).importAccessServer();
        verify(systemClient, times(1)).importAccessServerSaasMigration();
    }

    @Test
    public void samlTest() {
        when(accessServiceSpy.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);
        accessServiceSpy.isSamlEnabled();
        accessServiceSpy.getSamlSettings();
        accessServiceSpy.upsertSamlSetting(SamlSetting.builder().build());
    }

    @Test
    public void whenStandartImportThenNotSaasImportPerformed() throws IOException {
        ImportSettingsImpl importSettings = new ImportSettingsImpl(null);
        importSettings.setImportKind(ImportKind.STANDARD);

        accessServiceSpy.performImport(importSettings);

        verify(systemClient, times(1)).importAccessServer();
        verify(systemClient, times(0)).importAccessServerSaasMigration();
    }

    @Test(dataProvider = "provideDataForAssertUserPermissionScopeForNonAdmin")
    public void testAssertUserPermissionScopeForNonAdmin(List<String> scopesToCreate, List<String> scopesFromToken) {
        JwtAccessToken jwtAccessToken = new JwtAccessTokenImpl
                .Builder()
                .scope(scopesFromToken)
                .build();
        AccessTokenAuthentication tokenAuthentication = new AccessTokenAuthentication("token", jwtAccessToken,
                null, null);
        try (MockedStatic<AuthenticationHelper> authenticationHelper = mockStatic(AuthenticationHelper.class)) {
            authenticationHelper.when(AuthenticationHelper::getAuthentication).thenReturn(tokenAuthentication);

            assertThrows(AuthorizationException.class,
                    () -> accessServiceSpy
                            .assertUserPermissionScopeForNonAdmin(scopesToCreate));
        }
    }

    @Test(dataProvider = "provideDataForUserPermissionsExists")
    public void testUserPermissionsExists(List<String> scopes, boolean exists) {
        boolean actual = accessServiceSpy.userPermissionsExists(scopes);
        assertEquals(exists, actual);
    }

    @Test
    public void buildAccessClient_shouldNotFail() {
        when(accessServiceSpy.buildAccessClient()).thenCallRealMethod();

        AccessClient actual = accessServiceSpy.buildAccessClient();
        assertNotNull(actual, "buildAccessClient() should return a new AccessClient");
    }

    @Test
    public void testCreateTokenAsProxy() {
        TokenSpec<?> tokenSpec = mock(TokenSpec.class);
        String username = "test-user";
        String grantType = "client_credentials";
        ProxyTokenResponse expectedResponse = mock(ProxyTokenResponse.class);
        AccessRestClient accessRestClient = mock(AccessRestClient.class);
        accessServiceSpy.setAccessRestClient(accessRestClient);
        doReturn(List.of("api:*", "member-of-groups:test-group")).when(tokenSpec).getScope();
        doReturn(List.of("aud1")).when(tokenSpec).getAudience();
        doReturn(3600L).when(tokenSpec).getExpiresIn();
        doReturn(true).when(tokenSpec).getRefreshable();
        doReturn("desc").when(tokenSpec).getDescription();
        doReturn(false).when(tokenSpec).isReference();
        doReturn(expectedResponse).when(accessRestClient)
                .executePostRequest(any(), anyString(), anyString(), eq(ProxyTokenResponse.class));

        ProxyTokenResponse response;
        try (MockedStatic<AuthenticationHelper> authenticationHelperMockedStatic =
                mockStatic(AuthenticationHelper.class)) {
            Authentication authentication = mock(Authentication.class);
            SimpleUser principal = new SimpleUser(new UserImpl());
            when(authentication.getPrincipal()).thenReturn(principal);
            authenticationHelperMockedStatic.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            response = accessServiceSpy.createTokenAsProxy(tokenSpec, username, grantType);
        }

        assertEquals(response, expectedResponse);
        verify(accessRestClient)
                .executePostRequest(any(), anyString(), anyString(), eq(ProxyTokenResponse.class));
    }

    @DataProvider
    private static Object[][] provideNormalizeInstanceId() {
        String uuid = UUID.randomUUID().toString();
        return new Object[][] {
                {uuid, uuid},
                {"b0902ad17dfa28f5:7c169c4a:15849ae7faa:-8000", "b0902ad17dfa28f5_7c169c4a_15849ae7faa_-8000"},
                {" asdf 1234-erty_dfg5+sfg:sdf65=ghtr54 ", "_asdf_1234-erty_dfg5_sfg_sdf65_ghtr54"}
        };
    }

    @DataProvider
    private static Object[][] provideValidExpiresInForNonAdmin() {
        long validDefaultExpiresIn = USER_TOKEN_MAX_EXPIRES_IN_MINUTES_DEFAULT * 60 / 2;
        return new Object[][] {
                //{nonnull, constValueDefaultMaxExpiresIn, expiresIn, maxExpiresInConstValue}
                {false, null, validDefaultExpiresIn, constValueDefaultMaxExpiresIn}, //expects default max expires in
                {true, null, validDefaultExpiresIn, constValueDefaultMaxExpiresIn}, //expects default max expires in
                {true, null, USER_TOKEN_MAX_EXPIRES_IN_MINUTES_DEFAULT, constValueDefaultMaxExpiresIn}, //expects
                // default max expires in
                {true, 10L, 5L * 60, constValueDefaultMaxExpiresIn},
                {true, 10L, 10L * 60, constValueDefaultMaxExpiresIn},
                {true, 0L, 10L, constValueDefaultMaxExpiresIn},
                {true, 0L, null, constValueDefaultMaxExpiresIn},
                {true, 0L, Long.MAX_VALUE, constValueDefaultMaxExpiresIn},
                {true, null, constValueDefaultMaxExpiresIn - 60, constValueDefaultMaxExpiresIn - 60},
                {true, null, constValueDefaultMaxExpiresIn, constValueDefaultMaxExpiresIn},
                {true, null, constValueDefaultMaxExpiresIn + 60, constValueDefaultMaxExpiresIn + 60},
        };
    }

    @DataProvider
    private static Object[][] provideInvalidExpiresInForNonAdmin() {
        long invalidDefaultExpiresIn = USER_TOKEN_MAX_EXPIRES_IN_MINUTES_DEFAULT * 60 + 1;
        return new Object[][] {
                //{nonnull, constValueDefaultMaxExpiresIn, expiresIn, maxExpiresInConstValue}
                {false, null, invalidDefaultExpiresIn, constValueDefaultMaxExpiresIn}, //expects default max expires in
                {true, null, invalidDefaultExpiresIn, constValueDefaultMaxExpiresIn}, //expects default max expires in
                {true, null, 0L, constValueDefaultMaxExpiresIn},
                {true, 10L, 10L * 60 + 1, constValueDefaultMaxExpiresIn},
                {true, 10L, null, constValueDefaultMaxExpiresIn},
                {true, null, Long.MAX_VALUE, constValueDefaultMaxExpiresIn},
                {true, null, constValueDefaultMaxExpiresIn, constValueDefaultMaxExpiresIn - 60},
                {true, null, constValueDefaultMaxExpiresIn + 60, constValueDefaultMaxExpiresIn},
                {true, null, constValueDefaultMaxExpiresIn + 60 + 60, constValueDefaultMaxExpiresIn + 60},
        };
    }

    @DataProvider
    private static Object[][] provideDataForAssertUserPermissionScopeForNonAdmin() {
        return new Object[][] {
                {Collections.singletonList(APPLIED_PERMISSIONS_USER), null},
                {Collections.singletonList(APPLIED_PERMISSIONS_USER), new ArrayList<>()},
                {Collections.singletonList(APPLIED_PERMISSIONS_USER), Collections.singletonList("dummy")},
                {Collections.singletonList(APPLIED_PERMISSIONS_USER), Stream.of("dummy",
                        APPLIED_PERMISSIONS_GROUPS_PREFIX).toList()},
        };
    }

    @DataProvider
    private static Object[][] provideDataForUserPermissionsExists() {
        return new Object[][] {
                {Collections.singletonList(APPLIED_PERMISSIONS_USER), true},
                {null, false},
                {new ArrayList<>(), false},
                {Collections.singletonList("dummy"), false},
        };
    }

    @Test
    public void verifyTokenIfServiceIdChanged_validToken_returnsTrue() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getTokenValue()).thenReturn("valid-token");

        TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        TokenClient tokenClient = accessClient.token();
        when(tokenClient.verify("valid-token")).thenReturn(verifyResult);
        when(configsService.getConfig("artifactory.service_id")).thenReturn(serviceId);

        assertTrue(accessServiceSpy.verifyTokenIfServiceIdChanged(token));
    }

    @Test
    public void verifyTokenIfServiceIdChanged_invalidToken_returnsFalse() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getTokenValue()).thenReturn("bad-token");

        TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(false);
        when(verifyResult.getReason()).thenReturn("invalid signature");
        TokenClient tokenClient = accessClient.token();
        when(tokenClient.verify("bad-token")).thenReturn(verifyResult);
        when(configsService.getConfig("artifactory.service_id")).thenReturn(serviceId);

        assertFalse(accessServiceSpy.verifyTokenIfServiceIdChanged(token));
    }

    @Test
    public void verifyTokenIfServiceIdChanged_serviceIdChanged_validToken_returnsTrue() {
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getTokenValue()).thenReturn("stale-token");

        TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        TokenClient tokenClient = accessClient.token();
        when(tokenClient.verify("stale-token")).thenReturn(verifyResult);
        when(configsService.getConfig("artifactory.service_id")).thenReturn("jfrt@new-instance-id");

        assertTrue(accessServiceSpy.verifyTokenIfServiceIdChanged(token));
    }

    // RTDEV-92030: non-admin user must not obtain an admin-scoped token when the parsed access_token
    // carries an admin scope. Signature verification is mocked as successful so this test isolates
    // the scope-validation layer.
    @Test
    public void refreshToken_nonAdminWithForgedAdminScope_throwsAuthorizationException() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();

        JwtAccessToken forgedToken = mock(JwtAccessToken.class);
        when(forgedToken.getIssuer()).thenReturn(serviceId + "/users/reader");
        when(forgedToken.getSubject()).thenReturn(serviceId + "/users/reader");
        when(forgedToken.getScope()).thenReturn(List.of("applied-permissions/admin"));
        when(forgedToken.getAudience()).thenReturn(List.of("jfrt@*"));
        when(forgedToken.getTokenId()).thenReturn("forged-token-id");
        when(forgedToken.getExpirationTime()).thenReturn(null);
        doReturn(forgedToken).when(accessServiceSpy).parseToken(anyString());

        UserImpl nonAdminUser = new UserImpl("reader");
        nonAdminUser.setGroupAdmin(Boolean.FALSE);
        when(userGroupService.findUser("reader")).thenReturn(nonAdminUser);

        // Signature check passes (token is parseable and structurally valid); scope check must reject.
        TokenVerifyResult validSignature = mock(TokenVerifyResult.class);
        when(validSignature.isSuccessful()).thenReturn(true);
        doReturn(validSignature).when(accessServiceSpy).verifyAndGetResult(forgedToken);
        TokenClient tokenClient = accessClient.token();
        when(tokenClient.refresh(anyString(), any(TokenRequest.class)))
                .thenReturn(new TokenResponseImpl("escalated-token", "Bearer", null, 300L, null));

        TokenSpec<?> tokenSpec = mock(TokenSpec.class);
        when(tokenSpec.getScope()).thenReturn(null);
        when(tokenSpec.getAudience()).thenReturn(null);
        when(tokenSpec.getExpiresIn()).thenReturn(null);
        when(tokenSpec.getRefreshable()).thenReturn(null);

        assertThrows(AuthorizationException.class,
                () -> accessServiceSpy.refreshToken(tokenSpec, "forged.access.token.x", "valid-refresh-token"));
    }

    // RTDEV-92030: a legitimate non-admin refresh (user scope, valid token) must still succeed
    // after the privilege-escalation fix is in place.
    @Test
    public void refreshToken_nonAdminWithValidUserScope_succeeds() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();

        JwtAccessToken legitimateToken = mock(JwtAccessToken.class);
        when(legitimateToken.getIssuer()).thenReturn(serviceId + "/users/reader");
        when(legitimateToken.getSubject()).thenReturn(serviceId + "/users/reader");
        when(legitimateToken.getScope()).thenReturn(List.of("applied-permissions/user"));
        when(legitimateToken.getAudience()).thenReturn(List.of("jfrt@*"));
        when(legitimateToken.getTokenId()).thenReturn("reader-token-id");
        when(legitimateToken.getExpirationTime()).thenReturn(null);
        doReturn(legitimateToken).when(accessServiceSpy).parseToken(anyString());

        UserImpl nonAdminUser = new UserImpl("reader");
        nonAdminUser.setGroupAdmin(Boolean.FALSE);
        when(userGroupService.findUser("reader")).thenReturn(nonAdminUser);

        TokenVerifyResult validSignature = mock(TokenVerifyResult.class);
        when(validSignature.isSuccessful()).thenReturn(true);
        doReturn(validSignature).when(accessServiceSpy).verifyAndGetResult(legitimateToken);
        TokenClient tokenClient = accessClient.token();
        when(tokenClient.refresh(anyString(), any(TokenRequest.class)))
                .thenReturn(new TokenResponseImpl("new-access-token", "Bearer", "new-refresh-token", 300L, null));

        TokenSpec<?> tokenSpec = mock(TokenSpec.class);
        when(tokenSpec.getScope()).thenReturn(null);
        when(tokenSpec.getAudience()).thenReturn(null);
        when(tokenSpec.getExpiresIn()).thenReturn(null);
        when(tokenSpec.getRefreshable()).thenReturn(null);

        CreatedTokenInfo result = accessServiceSpy.refreshToken(tokenSpec, "valid.access.token", "valid-refresh-token");

        assertNotNull(result);
        assertEquals(result.getTokenValue(), "new-access-token");
    }

    // RTDEV-92030 Layer 2: a token with a forged/invalid signature must be rejected before
    // any scope or claims are trusted.
    @Test
    public void refreshToken_invalidSignature_throwsAuthorizationException() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();

        JwtAccessToken badSignatureToken = mock(JwtAccessToken.class);
        when(badSignatureToken.getIssuer()).thenReturn(serviceId + "/users/reader");
        when(badSignatureToken.getSubject()).thenReturn(serviceId + "/users/reader");
        when(badSignatureToken.getScope()).thenReturn(List.of("applied-permissions/user"));
        when(badSignatureToken.getAudience()).thenReturn(List.of("jfrt@*"));
        when(badSignatureToken.getTokenId()).thenReturn("bad-sig-token-id");
        when(badSignatureToken.getExpirationTime()).thenReturn(null);
        doReturn(badSignatureToken).when(accessServiceSpy).parseToken(anyString());

        TokenVerifyResult signatureFailure = mock(TokenVerifyResult.class);
        when(signatureFailure.isSuccessful()).thenReturn(false);
        when(signatureFailure.getReason()).thenReturn(VerifyFailureReason.SIGNATURE.label());
        doReturn(signatureFailure).when(accessServiceSpy).verifyAndGetResult(badSignatureToken);

        TokenSpec<?> tokenSpec = mock(TokenSpec.class);
        when(tokenSpec.getScope()).thenReturn(null);
        when(tokenSpec.getAudience()).thenReturn(null);
        when(tokenSpec.getExpiresIn()).thenReturn(null);
        when(tokenSpec.getRefreshable()).thenReturn(null);

        assertThrows(AuthorizationException.class,
                () -> accessServiceSpy.refreshToken(tokenSpec, "tampered.access.token", "valid-refresh-token"));
    }

    // RTDEV-92030 Layer 2: an expired but correctly-signed token must be allowed through the
    // signature check (the refresh flow exists precisely to replace expired tokens).
    @Test
    public void refreshToken_expiredTokenWithValidSignature_isAllowedThroughSignatureCheck() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();

        JwtAccessToken expiredToken = mock(JwtAccessToken.class);
        when(expiredToken.getIssuer()).thenReturn(serviceId + "/users/reader");
        when(expiredToken.getSubject()).thenReturn(serviceId + "/users/reader");
        when(expiredToken.getScope()).thenReturn(List.of("applied-permissions/user"));
        when(expiredToken.getAudience()).thenReturn(List.of("jfrt@*"));
        when(expiredToken.getTokenId()).thenReturn("expired-token-id");
        when(expiredToken.getExpirationTime()).thenReturn(null);
        doReturn(expiredToken).when(accessServiceSpy).parseToken(anyString());

        UserImpl nonAdminUser = new UserImpl("reader");
        nonAdminUser.setGroupAdmin(Boolean.FALSE);
        when(userGroupService.findUser("reader")).thenReturn(nonAdminUser);

        // verify() reports the token as expired — signature was valid, just past its TTL.
        TokenVerifyResult expiredResult = mock(TokenVerifyResult.class);
        when(expiredResult.isSuccessful()).thenReturn(false);
        when(expiredResult.getReason()).thenReturn(VerifyFailureReason.EXPIRED.label());
        doReturn(expiredResult).when(accessServiceSpy).verifyAndGetResult(expiredToken);
        TokenClient tokenClient = accessClient.token();
        when(tokenClient.refresh(anyString(), any(TokenRequest.class)))
                .thenReturn(new TokenResponseImpl("refreshed-access-token", "Bearer", "new-refresh-token", 300L, null));

        TokenSpec<?> tokenSpec = mock(TokenSpec.class);
        when(tokenSpec.getScope()).thenReturn(null);
        when(tokenSpec.getAudience()).thenReturn(null);
        when(tokenSpec.getExpiresIn()).thenReturn(null);
        when(tokenSpec.getRefreshable()).thenReturn(null);

        CreatedTokenInfo result =
                accessServiceSpy.refreshToken(tokenSpec, "expired.access.token", "valid-refresh-token");

        assertNotNull(result);
        assertEquals(result.getTokenValue(), "refreshed-access-token");
    }

    // RTDEV-92030: TokenSpec scope override must be validated, not only the parsed access_token scope.
    @Test
    public void refreshToken_nonAdminWithTokenSpecAdminScope_throwsAuthorizationException() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();

        JwtAccessToken legitimateToken = mock(JwtAccessToken.class);
        when(legitimateToken.getIssuer()).thenReturn(serviceId + "/users/reader");
        when(legitimateToken.getSubject()).thenReturn(serviceId + "/users/reader");
        when(legitimateToken.getScope()).thenReturn(List.of("applied-permissions/user"));
        when(legitimateToken.getAudience()).thenReturn(List.of("jfrt@*"));
        when(legitimateToken.getTokenId()).thenReturn("reader-token-id");
        when(legitimateToken.getExpirationTime()).thenReturn(null);
        doReturn(legitimateToken).when(accessServiceSpy).parseToken(anyString());

        UserImpl nonAdminUser = new UserImpl("reader");
        nonAdminUser.setGroupAdmin(Boolean.FALSE);
        when(userGroupService.findUser("reader")).thenReturn(nonAdminUser);

        TokenVerifyResult validSignature = mock(TokenVerifyResult.class);
        when(validSignature.isSuccessful()).thenReturn(true);
        doReturn(validSignature).when(accessServiceSpy).verifyAndGetResult(legitimateToken);

        TokenSpec<?> tokenSpec = mock(TokenSpec.class);
        when(tokenSpec.getScope()).thenReturn(List.of("applied-permissions/admin"));
        when(tokenSpec.getAudience()).thenReturn(null);
        when(tokenSpec.getExpiresIn()).thenReturn(null);
        when(tokenSpec.getRefreshable()).thenReturn(null);

        assertThrows(AuthorizationException.class,
                () -> accessServiceSpy.refreshToken(tokenSpec, "valid.access.token", "valid-refresh-token"));
    }

    // RTDEV-92030: a revoked access token must not be accepted for refresh.
    @Test
    public void refreshToken_revokedToken_throwsAuthorizationException() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();

        JwtAccessToken revokedToken = mock(JwtAccessToken.class);
        when(revokedToken.getIssuer()).thenReturn(serviceId + "/users/reader");
        when(revokedToken.getSubject()).thenReturn(serviceId + "/users/reader");
        when(revokedToken.getScope()).thenReturn(List.of("applied-permissions/user"));
        when(revokedToken.getAudience()).thenReturn(List.of("jfrt@*"));
        when(revokedToken.getTokenId()).thenReturn("revoked-token-id");
        when(revokedToken.getExpirationTime()).thenReturn(null);
        doReturn(revokedToken).when(accessServiceSpy).parseToken(anyString());

        TokenVerifyResult revokedResult = mock(TokenVerifyResult.class);
        when(revokedResult.isSuccessful()).thenReturn(false);
        when(revokedResult.getReason()).thenReturn(VerifyFailureReason.REVOKED.label());
        doReturn(revokedResult).when(accessServiceSpy).verifyAndGetResult(revokedToken);

        TokenSpec<?> tokenSpec = mock(TokenSpec.class);
        when(tokenSpec.getScope()).thenReturn(null);
        when(tokenSpec.getAudience()).thenReturn(null);
        when(tokenSpec.getExpiresIn()).thenReturn(null);
        when(tokenSpec.getRefreshable()).thenReturn(null);

        assertThrows(AuthorizationException.class,
                () -> accessServiceSpy.refreshToken(tokenSpec, "revoked.access.token", "valid-refresh-token"));
    }

    // RTDEV-92030: null scope on the access token must not cause NPE during refresh validation.
    @Test
    public void refreshToken_nullAccessTokenScope_doesNotFailWithNpeOnScopeValidation() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();

        JwtAccessToken tokenWithNullScope = mock(JwtAccessToken.class);
        when(tokenWithNullScope.getIssuer()).thenReturn(serviceId + "/users/reader");
        when(tokenWithNullScope.getSubject()).thenReturn(serviceId + "/users/reader");
        when(tokenWithNullScope.getScope()).thenReturn(null);
        when(tokenWithNullScope.getAudience()).thenReturn(List.of("jfrt@*"));
        when(tokenWithNullScope.getTokenId()).thenReturn("null-scope-token-id");
        when(tokenWithNullScope.getExpirationTime()).thenReturn(null);
        doReturn(tokenWithNullScope).when(accessServiceSpy).parseToken(anyString());

        UserImpl nonAdminUser = new UserImpl("reader");
        nonAdminUser.setGroupAdmin(Boolean.FALSE);
        when(userGroupService.findUser("reader")).thenReturn(nonAdminUser);

        TokenVerifyResult validSignature = mock(TokenVerifyResult.class);
        when(validSignature.isSuccessful()).thenReturn(true);
        doReturn(validSignature).when(accessServiceSpy).verifyAndGetResult(tokenWithNullScope);
        TokenClient tokenClient = accessClient.token();
        when(tokenClient.refresh(anyString(), any(TokenRequest.class)))
                .thenReturn(new TokenResponseImpl("new-access-token", "Bearer", "new-refresh-token", 300L, null));

        TokenSpec<?> tokenSpec = mock(TokenSpec.class);
        when(tokenSpec.getScope()).thenReturn(List.of("applied-permissions/user"));
        when(tokenSpec.getAudience()).thenReturn(null);
        when(tokenSpec.getExpiresIn()).thenReturn(null);
        when(tokenSpec.getRefreshable()).thenReturn(null);

        // Scope validation must not throw NPE when access_token.getScope() is null.
        // The tokenSpec provides the effective scope so the flow proceeds to Access normally.
        CreatedTokenInfo result =
                accessServiceSpy.refreshToken(tokenSpec, "valid.access.token", "valid-refresh-token");
        assertNotNull(result);
        assertEquals(result.getTokenValue(), "new-access-token");
    }

    // RTDEV-92030: repo-path permission on refresh is evaluated for the token owner, not the HTTP caller.
    @Test
    public void refreshToken_repoPathPermissionCheckedAgainstTokenOwner_notHttpPrincipal() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();
        String repoPathScope = "path:my-repo/path/file.jar:read";

        JwtAccessToken tokenWithRepoPathScope = mock(JwtAccessToken.class);
        when(tokenWithRepoPathScope.getIssuer()).thenReturn(serviceId + "/users/reader");
        when(tokenWithRepoPathScope.getSubject()).thenReturn(serviceId + "/users/reader");
        when(tokenWithRepoPathScope.getScope()).thenReturn(List.of(repoPathScope));
        when(tokenWithRepoPathScope.getAudience()).thenReturn(List.of("jfrt@*"));
        when(tokenWithRepoPathScope.getTokenId()).thenReturn("repo-path-token-id");
        when(tokenWithRepoPathScope.getExpirationTime()).thenReturn(null);
        doReturn(tokenWithRepoPathScope).when(accessServiceSpy).parseToken(anyString());

        UserImpl nonAdminUser = new UserImpl("reader");
        nonAdminUser.setGroupAdmin(Boolean.FALSE);
        when(userGroupService.findUser("reader")).thenReturn(nonAdminUser);

        FileInfo fileInfo = mock(FileInfo.class);
        when(fileInfo.isFolder()).thenReturn(false);
        when(repositoryService.getItemInfo(any(RepoPath.class))).thenReturn(fileInfo);

        when(securityService.isAuthenticated()).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(false);
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        when(authorizationService.canRead(eq(nonAdminUser), any(RepoPath.class))).thenReturn(false);

        TokenVerifyResult validSignature = mock(TokenVerifyResult.class);
        when(validSignature.isSuccessful()).thenReturn(true);
        doReturn(validSignature).when(accessServiceSpy).verifyAndGetResult(tokenWithRepoPathScope);

        TokenSpec<?> tokenSpec = mock(TokenSpec.class);
        when(tokenSpec.getScope()).thenReturn(null);
        when(tokenSpec.getAudience()).thenReturn(null);
        when(tokenSpec.getExpiresIn()).thenReturn(null);
        when(tokenSpec.getRefreshable()).thenReturn(null);

        assertThrows(AuthorizationException.class,
                () -> accessServiceSpy.refreshToken(tokenSpec, "valid.access.token", "valid-refresh-token"));
        verify(authorizationService).canRead(eq(nonAdminUser), any(RepoPath.class));
    }

    // RTDEV-92030: when both access_token and TokenSpec scopes are null, validation must not NPE.
    @Test
    public void refreshToken_bothNullScopes_rejectedWithoutNpe() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();

        JwtAccessToken tokenWithNullScope = mock(JwtAccessToken.class);
        when(tokenWithNullScope.getIssuer()).thenReturn(serviceId + "/users/reader");
        when(tokenWithNullScope.getSubject()).thenReturn(serviceId + "/users/reader");
        when(tokenWithNullScope.getScope()).thenReturn(null);
        when(tokenWithNullScope.getAudience()).thenReturn(List.of("jfrt@*"));
        when(tokenWithNullScope.getTokenId()).thenReturn("null-scope-token-id");
        when(tokenWithNullScope.getExpirationTime()).thenReturn(null);
        doReturn(tokenWithNullScope).when(accessServiceSpy).parseToken(anyString());

        UserImpl nonAdminUser = new UserImpl("reader");
        nonAdminUser.setGroupAdmin(Boolean.FALSE);
        when(userGroupService.findUser("reader")).thenReturn(nonAdminUser);

        TokenVerifyResult validSignature = mock(TokenVerifyResult.class);
        when(validSignature.isSuccessful()).thenReturn(true);
        doReturn(validSignature).when(accessServiceSpy).verifyAndGetResult(tokenWithNullScope);

        TokenSpec<?> tokenSpec = mock(TokenSpec.class);
        when(tokenSpec.getScope()).thenReturn(null);
        when(tokenSpec.getAudience()).thenReturn(null);
        when(tokenSpec.getExpiresIn()).thenReturn(null);
        when(tokenSpec.getRefreshable()).thenReturn(null);

        try {
            accessServiceSpy.refreshToken(tokenSpec, "valid.access.token", "valid-refresh-token");
        } catch (NullPointerException e) {
            Assert.fail("Scope validation must not NPE when both access_token and TokenSpec scopes are null", e);
        } catch (Exception e) {
            // Empty effective scope is rejected after validation (e.g. by Access TokenRequest); any non-NPE is fine.
            return;
        }
        Assert.fail("Expected refresh to fail when effective scope is empty");
    }

    // RTDEV-92030: when the issuer has no user identity (e.g. a client/service subject), group membership and
    // per-user repo-path permissions cannot be evaluated, but unsupported scopes must still be rejected.
    @Test
    public void refreshToken_issuerWithoutUserIdentity_unsupportedScopeRejected() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();

        JwtAccessToken tokenWithoutUserIssuer = mock(JwtAccessToken.class);
        when(tokenWithoutUserIssuer.getIssuer()).thenReturn(serviceId);
        when(tokenWithoutUserIssuer.getSubject()).thenReturn(serviceId);
        when(tokenWithoutUserIssuer.getScope()).thenReturn(List.of("applied-permissions/admin"));
        when(tokenWithoutUserIssuer.getAudience()).thenReturn(List.of("jfrt@*"));
        when(tokenWithoutUserIssuer.getTokenId()).thenReturn("no-user-issuer-token-id");
        when(tokenWithoutUserIssuer.getExpirationTime()).thenReturn(null);
        doReturn(tokenWithoutUserIssuer).when(accessServiceSpy).parseToken(anyString());

        TokenVerifyResult validSignature = mock(TokenVerifyResult.class);
        when(validSignature.isSuccessful()).thenReturn(true);
        doReturn(validSignature).when(accessServiceSpy).verifyAndGetResult(tokenWithoutUserIssuer);

        TokenSpec<?> tokenSpec = mock(TokenSpec.class);
        when(tokenSpec.getScope()).thenReturn(null);
        when(tokenSpec.getAudience()).thenReturn(null);
        when(tokenSpec.getExpiresIn()).thenReturn(null);
        when(tokenSpec.getRefreshable()).thenReturn(null);

        assertThrows(AuthorizationException.class,
                () -> accessServiceSpy.refreshToken(tokenSpec, "service-token", "valid-refresh-token"));
    }

    // RTDEV-92030: same no-user-identity issuer, but with a scope that is permitted for non-admins; the refresh
    // must still succeed (path-existence/unsupported-scope checks run, group/repo-path-owner checks are skipped).
    @Test
    public void refreshToken_issuerWithoutUserIdentity_validScopeSucceeds() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();

        JwtAccessToken tokenWithoutUserIssuer = mock(JwtAccessToken.class);
        when(tokenWithoutUserIssuer.getIssuer()).thenReturn(serviceId);
        when(tokenWithoutUserIssuer.getSubject()).thenReturn(serviceId);
        when(tokenWithoutUserIssuer.getScope()).thenReturn(List.of("applied-permissions/user"));
        when(tokenWithoutUserIssuer.getAudience()).thenReturn(List.of("jfrt@*"));
        when(tokenWithoutUserIssuer.getTokenId()).thenReturn("no-user-issuer-token-id");
        when(tokenWithoutUserIssuer.getExpirationTime()).thenReturn(null);
        doReturn(tokenWithoutUserIssuer).when(accessServiceSpy).parseToken(anyString());

        TokenVerifyResult validSignature = mock(TokenVerifyResult.class);
        when(validSignature.isSuccessful()).thenReturn(true);
        doReturn(validSignature).when(accessServiceSpy).verifyAndGetResult(tokenWithoutUserIssuer);
        TokenClient tokenClient = accessClient.token();
        when(tokenClient.refresh(anyString(), any(TokenRequest.class)))
                .thenReturn(new TokenResponseImpl("service-new-token", "Bearer", "new-refresh-token", 300L, null));

        TokenSpec<?> tokenSpec = mock(TokenSpec.class);
        when(tokenSpec.getScope()).thenReturn(null);
        when(tokenSpec.getAudience()).thenReturn(null);
        when(tokenSpec.getExpiresIn()).thenReturn(null);
        when(tokenSpec.getRefreshable()).thenReturn(null);

        CreatedTokenInfo result = accessServiceSpy.refreshToken(tokenSpec, "service-token", "valid-refresh-token");

        assertNotNull(result);
        assertEquals(result.getTokenValue(), "service-new-token");
    }

    // RTDEV-92030: path.checksum:* repo-path scopes are Access's internal, irreversible-hash representation of
    // long paths that were already validated at token-creation time. Refresh must skip re-checking permissions
    // on them (the path cannot be recovered from the hash) instead of failing the request.
    @Test
    public void refreshToken_checksumRepoPathScope_skippedFromPermissionCheck() {
        String serviceId = accessServiceSpy.getArtifactoryServiceId().getFormattedName();
        String checksumScope = "path.checksum:deadbeef1234:read";

        JwtAccessToken tokenWithChecksumScope = mock(JwtAccessToken.class);
        when(tokenWithChecksumScope.getIssuer()).thenReturn(serviceId + "/users/reader");
        when(tokenWithChecksumScope.getSubject()).thenReturn(serviceId + "/users/reader");
        when(tokenWithChecksumScope.getScope()).thenReturn(List.of(checksumScope));
        when(tokenWithChecksumScope.getAudience()).thenReturn(List.of("jfrt@*"));
        when(tokenWithChecksumScope.getTokenId()).thenReturn("checksum-scope-token-id");
        when(tokenWithChecksumScope.getExpirationTime()).thenReturn(null);
        doReturn(tokenWithChecksumScope).when(accessServiceSpy).parseToken(anyString());

        UserImpl nonAdminUser = new UserImpl("reader");
        nonAdminUser.setGroupAdmin(Boolean.FALSE);
        when(userGroupService.findUser("reader")).thenReturn(nonAdminUser);

        TokenVerifyResult validSignature = mock(TokenVerifyResult.class);
        when(validSignature.isSuccessful()).thenReturn(true);
        doReturn(validSignature).when(accessServiceSpy).verifyAndGetResult(tokenWithChecksumScope);
        TokenClient tokenClient = accessClient.token();
        when(tokenClient.refresh(anyString(), any(TokenRequest.class)))
                .thenReturn(new TokenResponseImpl("new-access-token", "Bearer", "new-refresh-token", 300L, null));

        TokenSpec<?> tokenSpec = mock(TokenSpec.class);
        when(tokenSpec.getScope()).thenReturn(null);
        when(tokenSpec.getAudience()).thenReturn(null);
        when(tokenSpec.getExpiresIn()).thenReturn(null);
        when(tokenSpec.getRefreshable()).thenReturn(null);

        // No repositoryService/authorizationService stubbing: if the checksum scope were not skipped, this would
        // fail (e.g. NotFoundException from RepositoryService.getItemInfo on the un-decodable hashed "path").
        CreatedTokenInfo result =
                accessServiceSpy.refreshToken(tokenSpec, "valid.access.token", "valid-refresh-token");

        assertNotNull(result);
        assertEquals(result.getTokenValue(), "new-access-token");
        verifyNoInteractions(repositoryService);
    }
}
