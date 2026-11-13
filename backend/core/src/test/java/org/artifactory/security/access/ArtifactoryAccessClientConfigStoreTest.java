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

import com.jfrog.sysconf.SysConfig;
import com.jfrog.sysconf.SysConfigHelper;
import org.apache.commons.io.FileUtils;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.client.AccessAuth;
import org.jfrog.access.client.AccessAuthToken;
import org.jfrog.access.client.AccessClientBuilder;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.token.JwtAccessTokenImpl;
import org.jfrog.access.version.AccessVersion;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.EncryptionWrapperFactory;
import org.jfrog.security.crypto.JFrogCryptoHelper;
import org.jfrog.security.crypto.PlainTextEncryptionWrapper;
import org.jfrog.security.file.PemHelper;
import org.jfrog.security.file.SecurityFolderHelper;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.util.Properties;

import static org.artifactory.common.ConstantValues.permissionsCacheType;
import static org.artifactory.test.TestUtils.createCertificate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * @author Yinon Avraham.
 */
public class ArtifactoryAccessClientConfigStoreTest extends ArtifactoryHomeBoundTest {

    public static final ServiceId TEST_SERVICE_ID = ServiceId.generateUniqueId("test");
    ArtifactoryHome artHome;
    private File testDir;
    private File rootCrtFile;
    private File accessCredsFile;
    private File accessAdminTokenFile;
    private File masterKeyFile;
    private EncryptionWrapper masterEncryption;
    private ArtifactorySystemProperties artSysProperties;
    private SysConfig sysConfig;
    private SysConfigHelper sysConfigHelper;

    @DataProvider
    public static Object[][] provideStoreAdminToken() {
        return new Object[][] {
                {true},
                {false}
        };
    }

    @DataProvider
    public static Object[][] provideNewClientBuilder() {
        return new Object[][] {
                {"http://config-url", "http://sys-prop-url", "auth-token", 10L, 20L, 30L, 40L, 50, 60, 70, false,
                        false, false, null},
                {"http://config-url", null, null, 10L, null, 30L, null, 50, 60, 70, false, false, true, null},
                {null, "http://sys-prop-url", "auth-token", null, 20L, null, 40L, 50, 60, 70, false, false, false,
                        null},
                {null, "http://sys-prop-url", "auth-token", -1L, 20L, -1L, 40L, 50, 60, 70, false, false, false, null},
                {null, "http://sys-prop-url", "auth-token", null, null, null, null, 50, 60, 70, false, false, false,
                        null},
                {"http://config-url", "http://sys-prop-url", "auth-token", -1L, -1L, -1L, -1L, 50, 60, 70, false,
                        false, false, null},
                {"http://config-url", null, null, 0L, 20L, 0L, 40L, 50, 60, 70, false, false, false, null},
                {null, "http://sys-prop-url", "auth-token", -1L, 0L, -1L, 0L, 50, 60, 70, false, false, false, null},
                {null, "http://sys-prop-url", "auth-token", null, 0L, null, 0L, 50, 60, 70, false, false, false, null},
                {"http://config-url", "http://sys-prop-url", "auth-token", 10L, 20L, 30L, 0L, null, 60, 70, false,
                        false, false, null},
                {"http://config-url", "http://sys-prop-url", "auth-token", 10L, 20L, 30L, 0L, 50, null, 70, false,
                        false, false, null},
                {"http://config-url", "http://sys-prop-url", "auth-token", 10L, 20L, 30L, 0L, 50, 60, null, false,
                        false, false, null},
                {"http://config-url", "http://sys-prop-url", "auth-token", 10L, 20L, 30L, 0L, 0, 60, 70, false, false
                        , false, null},
                {"http://config-url", "http://sys-prop-url", "auth-token", 10L, 20L, 30L, 0L, 50, 0, 70, false, false
                        , false, null},
                {"http://config-url", "http://sys-prop-url", "auth-token", 10L, 20L, 30L, 0L, 50, 60, 0, false, false
                        , false, null},
                {"http://config-url", "http://sys-prop-url", "auth-token", 10L, 20L, 30L, 0L, -1, 60, 70, false,
                        false, false, null},
                {"http://config-url", "http://sys-prop-url", "auth-token", 10L, 20L, 30L, 0L, 50, -1, 70, false,
                        false, false, null},
                {"http://config-url", "http://sys-prop-url", "auth-token", 10L, 20L, 30L, 0L, 50, 60, -1, false,
                        false, false, null},
                {"http://config-url", "http://sys-prop-url", "auth-token", 10L, 20L, 30L, 0L, 50, 60, -1, true, false
                        , false, null},
                {"http://config-url", "http://sys-prop-url", "auth-token", 10L, 20L, 30L, 0L, 50, 60, -1, true, true,
                        false, null},
                {"http://config-url", "http://sys-prop-url", "auth-token", 10L, 20L, 30L, 0L, 50, 60, -1, false, true
                        , false, 100},
        };
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    @BeforeMethod
    public void setup() throws Exception {
        testDir = Files.createTempDirectory(ArtifactoryAccessClientConfigStoreTest.class.getSimpleName()).toFile();
        rootCrtFile = new File(testDir, "keys/root.crt");
        accessCredsFile = new File(testDir, "keys/access.creds");
        accessAdminTokenFile = new File(testDir, "access.admin.token");
        masterKeyFile = new File(testDir, "master.key");
        SecurityFolderHelper.createKeyFile(masterKeyFile);
        removeMasterKey();
        artSysProperties = new ArtifactorySystemProperties();
        artHome = mock(ArtifactoryHome.class);
    }

    @AfterMethod
    public void cleanup() throws Exception {
        FileUtils.forceDelete(testDir);
        ArtifactoryHome.unbind();
    }

    private void removeMasterKey() {
        masterEncryption = new PlainTextEncryptionWrapper();
    }

    private void initMasterKey() {
        masterEncryption = EncryptionWrapperFactory.createArtifactoryKeyWrapper(masterKeyFile,
                masterKeyFile.getParentFile(), 0, f -> true);
    }

    @Test
    public void testServiceId() throws Exception {
        ArtifactoryAccessClientConfigStore configStore = createConfigStore();
        assertNotNull(configStore.getServiceId());
        ServiceId serviceId = ServiceId.generateUniqueId("foo");
        configStore.setServiceId(serviceId);
        assertEquals(configStore.getServiceId(), serviceId);
    }

    @Test
    public void testAccessClientVersion() throws Exception {
        ArtifactoryAccessClientConfigStore configStore = createConfigStore();
        //No access.version.properties file
        assertNull(configStore.getAccessClientVersion());
        //Store the version
        AccessVersion newVersion = createAccessVersion("ver", "time", "rev");
        configStore.storeAccessClientVersion(newVersion);
        //Read the version from the file
        File versionFile = new File(testDir, "data/access.version.properties");
        try (InputStream input = new FileInputStream(versionFile)) {
            Properties properties = new Properties();
            properties.load(input);
            AccessVersion storedVersion = AccessVersion.read(properties);
            assertEqualVersions(storedVersion, newVersion);
        }
        //Get the version from the config store
        AccessVersion storedVersion = configStore.getAccessClientVersion();
        assertEqualVersions(storedVersion, newVersion);
    }

    @Test
    public void testGetAdminToken() throws Exception {
        ArtifactoryAccessClientConfigStore configStore = createConfigStore();
        //get throws if there is no token
        assertFalse(configStore.isAdminTokenExists());
        assertThrows(IllegalStateException.class, configStore::getAdminToken);
        //get returns the token in clear text
        String adminToken = "the-token";
        configStore.storeAdminToken(adminToken);
        assertTrue(configStore.isAdminTokenExists());
        assertEquals(configStore.getAdminToken(), adminToken);
        //get returns the encrypted token in clear text
        initMasterKey();

        assertTrue(configStore.isAdminTokenExists());
        assertEquals(configStore.getAdminToken(), adminToken);
    }

    @Test(dataProvider = "provideStoreAdminToken")
    public void testStoreAdminToken(boolean saveDescriptorAllowed) throws Exception {
        ArtifactoryAccessClientConfigStore configStore = createConfigStore(saveDescriptorAllowed);
        //store admin token, no master key
        assertFalse(configStore.isAdminTokenExists());
        String adminToken = "the-token";
        configStore.storeAdminToken(adminToken);
        assertTrue(configStore.isAdminTokenExists());
        assertEquals(configStore.getAdminToken(), adminToken);
        //store admin token, master key exists
        initMasterKey();
        configStore.storeAdminToken(adminToken);
        assertTrue(configStore.isAdminTokenExists());
        assertEquals(configStore.getAdminToken(), adminToken);

        //revoke the admin token
        configStore.revokeAdminToken();
        assertFalse(configStore.isAdminTokenExists());
    }

    @Test
    public void testRootCertificate() throws Exception {
        ArtifactoryAccessClientConfigStore configStore = createConfigStore();
        //get non-existing root certificate throws
        assertFalse(configStore.isRootCertificateExists());
        assertFalse(rootCrtFile.exists());
        assertThrows(RuntimeException.class, configStore::getRootCertificate);
        //create certificate and store it
        KeyPair keyPair = JFrogCryptoHelper.generateKeyPair();
        Certificate certificate = createCertificate(keyPair);
        configStore.storeRootCertificate(certificate);
        assertTrue(configStore.isRootCertificateExists());
        assertEquals(configStore.getRootCertificate(), certificate);
        assertTrue(rootCrtFile.exists());
        assertEquals(PemHelper.readCertificate(rootCrtFile).getEncoded(), certificate.getEncoded());
    }

    @Test
    public void testConvertClientConfigFromEmbeddedServer() throws Exception {
        //Create the obsolete files
        File keysDir = new File(testDir, "keys");
        FileUtils.forceMkdir(keysDir);
        File adminTokenFile = new File(keysDir, TEST_SERVICE_ID + ".token");
        File keyFile = new File(keysDir, TEST_SERVICE_ID + ".key");
        File crtFile = new File(keysDir, TEST_SERVICE_ID + ".crt");
        File keystoreFile = new File(keysDir, "keystore.jks");
        FileUtils.write(adminTokenFile, "the-token");
        FileUtils.write(keyFile, "the-key");
        FileUtils.write(crtFile, "the-crt");
        FileUtils.write(keystoreFile, "the-keystore");
        assertTrue(adminTokenFile.exists());
        assertTrue(keyFile.exists());
        assertTrue(crtFile.exists());
        assertTrue(keystoreFile.exists());

        ArtifactoryAccessClientConfigStore configStore = createConfigStore();
        assertFalse(adminTokenFile.exists());
        assertThrows(configStore::getAdminToken);   // the converter should discard the old admin token
        assertFalse(keyFile.exists());
        assertFalse(crtFile.exists());
        assertFalse(keystoreFile.exists());
        assertFalse(accessCredsFile.exists(), "no access.creds with default credentials may be written");
    }

    @Test(dataProvider = "provideNewClientBuilder")
    public void testNewClientBuilder(String configServerUrl, String sysPropServerUrl, String adminToken,
            Long configCacheSize, Long sysPropCacheSize,
            Long configCacheExpiry, Long sysPropCacheExpiry,
            Integer maxConnections, Integer connectionTimeout,
            Integer socketTimeout, boolean forceRest, boolean forceGrpc, boolean tokenVerifyResultCacheSize,
            Integer accessGrpcMaxInboundMessageSize)
            throws Exception {

        String expectedServerUrl =
                sysPropServerUrl != null ? sysPropServerUrl : configServerUrl + "/access"; // -> access router path
        AccessAuth expectedAuth = adminToken == null ? null : new AccessAuthToken(adminToken);
        long expectedCacheSize = sysPropCacheSize != null && sysPropCacheSize >= 0 ? sysPropCacheSize :
                configCacheSize != null && configCacheSize >= 0 ? configCacheSize :
                        1000; //client default
        long expectedCacheExpiry = sysPropCacheExpiry != null && sysPropCacheExpiry >= 0 ? sysPropCacheExpiry :
                configCacheSize != null && configCacheExpiry >= 0 ? configCacheExpiry :
                        60; //client default
        int expectedMaxConnections;
        if (maxConnections == null) {
            expectedMaxConnections = ConstantValues.accessClientMaxConnections.getInt();
        } else if (maxConnections <= 0) {
            expectedMaxConnections = 3;
        } else {
            expectedMaxConnections = maxConnections;
        }
        int expectedAccessGrpcMaxInboundMessageSize = accessGrpcMaxInboundMessageSize == null ?
                ConstantValues.accessGrpcMaxInboundMessageSize.getInt() : accessGrpcMaxInboundMessageSize;
        int expectedConnectionTimeout = connectionTimeout != null && connectionTimeout > 0 ? connectionTimeout : 10000;
        int expectedSocketTimeout = socketTimeout != null && socketTimeout > 0 ? socketTimeout : 60000;
        artSysProperties.setProperty(ConstantValues.accessClientServerUrlOverride.getPropertyName(), expectedServerUrl);
        artSysProperties.setProperty(ConstantValues.accessClientTokenVerifyResultCacheSize.getPropertyName(),
                Long.toString(expectedCacheSize));
        artSysProperties.setProperty(ConstantValues.accessClientTokenVerifyResultCacheExpiry.getPropertyName(),
                Long.toString(expectedCacheExpiry));
        artSysProperties.setProperty(ConstantValues.accessClientMaxConnections.getPropertyName(),
                Integer.toString(expectedMaxConnections));
        artSysProperties.setProperty(ConstantValues.accessClientConnectionTimeout.getPropertyName(),
                Integer.toString(expectedConnectionTimeout));
        artSysProperties.setProperty(ConstantValues.accessClientUseArtifactoryRootCertificateEnabled.getPropertyName(),
                "true");
        if (socketTimeout != null) {
            artSysProperties.setProperty(ConstantValues.accessClientSocketTimeout.getPropertyName(),
                    Integer.toString(socketTimeout));
        }
        if (accessGrpcMaxInboundMessageSize != null) {
            artSysProperties.setProperty(ConstantValues.accessGrpcMaxInboundMessageSize.getPropertyName(),
                    Integer.toString(accessGrpcMaxInboundMessageSize));
        }
        artSysProperties.setProperty(ConstantValues.accessClientForceRest.getPropertyName(),
                Boolean.toString(forceRest));
        artSysProperties.setProperty(ConstantValues.accessClientForceGrpc.getPropertyName(),
                Boolean.toString(forceGrpc));
        artSysProperties.setProperty(permissionsCacheType.getPropertyName(), "access");
        when(artHome.getArtifactoryProperties()).thenReturn(artSysProperties);

        ArtifactoryHome.bind(artHome);
        ArtifactoryAccessClientConfigStore configStore = createConfigStore(tokenVerifyResultCacheSize);
        Certificate certificate = createCertificate(JFrogCryptoHelper.generateKeyPair());
        configStore.storeRootCertificate(certificate);

        if (adminToken == null) {
            configStore.revokeAdminToken();
        } else {
            configStore.storeAdminToken(adminToken);
        }

        AccessClientBuilder builder = configStore.newClientBuilder();

        assertEquals(builder.getServiceId(), TEST_SERVICE_ID);
        assertEquals(builder.getServerUrl(), expectedServerUrl);
        assertEquals(builder.getRootCertificateHolder().get(), certificate);
        assertEquals(builder.getDefaultAuth(), expectedAuth);
        assertEquals(builder.getTokenVerificationResultCacheSize(), expectedCacheSize);
        assertEquals(builder.getTokenVerificationResultCacheExpiry(), expectedCacheExpiry);
        assertEquals(builder.getMaxConnections(), expectedMaxConnections);
        assertEquals(builder.getConnectionTimeout(), expectedConnectionTimeout);
        assertEquals(builder.getSocketTimeout(), expectedSocketTimeout);
        assertEquals(builder.isForceRest(), forceRest);
        assertEquals(builder.isForceGrpc(), forceGrpc);
        assertEquals(builder.grpcMaxInboundMessageSize(), expectedAccessGrpcMaxInboundMessageSize);
        assertTrue(builder.scopedPrincipalAuthorizationEnabled());
    }

    @Test
    public void testDefaultBuilderValues() {
        try {
            ArtifactoryHome.bind(artHome);
            when(artHome.getArtifactoryProperties()).thenReturn(artSysProperties);
            ArtifactoryAccessClientConfigStore configStore = createConfigStore(true);
            artSysProperties.setProperty(permissionsCacheType.getPropertyName(), "access");
            assertTrue(configStore.newClientBuilder().scopedPrincipalAuthorizationEnabled());
            artSysProperties.setProperty(permissionsCacheType.getPropertyName(), "shadow");
            assertTrue(configStore.newClientBuilder().scopedPrincipalAuthorizationEnabled());
            artSysProperties.setProperty(permissionsCacheType.getPropertyName(), "legacy");
            assertTrue(configStore.newClientBuilder().scopedPrincipalAuthorizationEnabled());
        } finally {
            artSysProperties.removeProperty(permissionsCacheType);
        }
    }

    private void assertEqualVersions(AccessVersion actual, AccessVersion expected) {
        assertEquals(actual.getName(), expected.getName());
        assertEquals(actual.getTimestamp(), expected.getTimestamp());
        assertEquals(actual.getRevision(), expected.getRevision());
    }

    private AccessVersion createAccessVersion(String version, String timestamp, String revision) {
        Properties properties = new Properties();
        properties.setProperty("access.version", version);
        properties.setProperty("access.timestamp", timestamp);
        properties.setProperty("access.revision", revision);
        return AccessVersion.read(properties);
    }

    private ArtifactoryAccessClientConfigStore createConfigStore() {
        return createConfigStore(true);
    }

    private ArtifactoryAccessClientConfigStore createConfigStore(boolean SaveDescriptorAllowed) {
        AccessServiceImpl accessService = mock(AccessServiceImpl.class);
        SysConfig sysConfig = mock(SysConfig.class);
        SysConfigHelper sysConfigHelper = mock(SysConfigHelper.class);
        accessService.runAfterContextCreated(any(Runnable.class));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(accessService).runAfterContextCreated(any(Runnable.class));
        //Artifactory home
        when(artHome.getSysConfig()).thenReturn(sysConfig);
        when(artHome.getSysConfig().helper()).thenReturn(sysConfigHelper);
        when(artHome.getAccessClientDir()).thenReturn(testDir);
        when(artHome.getAccessAdminCredsFile()).thenReturn(accessCredsFile);
        when(artHome.getAccessAdminTokenFile()).thenReturn(accessAdminTokenFile);
        when(artHome.getMasterEncryptionWrapper()).thenAnswer(invocation -> masterEncryption);
        when(artHome.getArtifactoryProperties()).thenReturn(artSysProperties);
        when(artHome.getSysConfig().getBoolean(anyString(), anyBoolean())).thenReturn(false);
        when(artHome.getSysConfig().helper().getRouterUrl()).thenReturn("http://config-url");
        when(artHome.getSysConfig()).thenReturn(new SysConfig.Builder().withHome("").build());
        //Security service
        when(accessService.securityService()).thenReturn(mock(SecurityService.class));

        // pass validation with existing token
        when(accessService.parseToken(anyString()))
                .thenAnswer(invocation -> mock(JwtAccessTokenImpl.class));
        ArtifactoryHome.bind(artHome);

        return new ArtifactoryAccessClientConfigStore(TEST_SERVICE_ID);
    }
}