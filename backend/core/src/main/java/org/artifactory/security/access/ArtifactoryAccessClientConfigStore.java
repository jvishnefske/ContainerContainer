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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.jfrog.sysconf.SysConfigConstants;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.access.client.AccessAuthToken;
import org.jfrog.access.client.AccessClientBuilder;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.RootCertificateHolder;
import org.jfrog.access.client.confstore.AccessClientConfigStore;
import org.jfrog.access.common.ServerInfo;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.version.AccessVersion;
import org.jfrog.security.file.PemHelper;
import org.jfrog.security.file.SecurityFolderHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.common.ConstantValues.accessClientUseArtifactoryRootCertificateEnabled;
import static org.jfrog.security.file.SecurityFolderHelper.PERMISSIONS_MODE_600;

/**
 * Client-side configuration of the Access client: server URL, root certificate, admin token and client version
 * files. It never writes an {@code access.creds} file with default administrator credentials: converting a legacy
 * embedded-server layout only deletes the obsolete key and token files (IA1).
 *
 * @author Yinon Avraham.
 */
@Slf4j
public class ArtifactoryAccessClientConfigStore implements AccessClientConfigStore {

    private static final String CACHED_SERVER_URL = "serverUrl";
    private static final String CACHED_ADMIN_TOKEN = "adminToken";
    private static final String CACHED_ADMIN_CREDS = "adminCreds";

    private final Cache<String, Object> cachedValues = CacheBuilder.newBuilder().build();
    private final boolean routerEnabled;
    private final ArtifactoryHome artifactoryHome;
    private final File rootCrtFile;
    private final File clientVersionFile;
    private final String routerUrl;
    private ServiceId serviceId;

    ArtifactoryAccessClientConfigStore(ServiceId serviceId) {
        this.artifactoryHome = requireNonNull(ArtifactoryHome.get(), "Artifactory home is required");
        this.routerEnabled = artifactoryHome.getSysConfig().getBoolean(SysConfigConstants.KEY_ROUTER_ENABLED, true);
        this.routerUrl =
                requireNonNull(artifactoryHome.getSysConfig().helper().getRouterUrl(), "routerUrl is required");
        this.rootCrtFile = new File(artifactoryHome.getAccessClientDir(), "keys/root.crt");
        this.clientVersionFile = new File(artifactoryHome.getAccessClientDir(), "data/access.version.properties");
        this.serviceId = requireNonNull(serviceId, "service ID is required");
        convertClientConfigFromEmbeddedAccessServerIfNeeded();
    }

    private static String readFirstLine(File file) throws IOException {
        Path path = file.toPath();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return reader.readLine();
        }
    }

    private void convertClientConfigFromEmbeddedAccessServerIfNeeded() {
        try {
            // Convert the access client config from embedded server to bundled server according to the existence of the
            // admin token file.
            Path keysFolder = artifactoryHome.getAccessClientDir().toPath().resolve("keys");
            File adminTokenFile = keysFolder.resolve(serviceId + ".token").toFile();
            if (adminTokenFile.exists()) {
                log.debug("Admin token file exists: '{}', starting access client config conversion from " +
                        "embedded server to bundled server.", adminTokenFile.getAbsolutePath());
                // Stock Artifactory wrote an access.creds file holding the published default admin password here.
                // The fork ships no default credential (IA1), so nothing is written.
                // remove the old format of the admin token
                Files.delete(adminTokenFile.toPath());
                // Remove obsolete files
                Files.deleteIfExists(keysFolder.resolve(serviceId + ".token"));
                Files.deleteIfExists(keysFolder.resolve(serviceId + ".key"));
                Files.deleteIfExists(keysFolder.resolve(serviceId + ".crt"));
                Files.deleteIfExists(keysFolder.resolve("keystore.jks"));
            } else {
                log.debug("Admin token file does not exist: '{}', skipping access client config conversion from " +
                        "embedded server to bundled server.", adminTokenFile.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert access client config from embedded to bundled server.", e);
        }
    }

    @Nonnull
    @Override
    public AccessClientBuilder newClientBuilder() {
        String adminToken = getAccessAdminToken();
        AccessClientBuilder clientBuilder = AccessClientBuilder.newBuilder()
                .serverUrl(getAccessServerSettings().getUrl())
                .serviceId(getServiceId())
                // no need to pass the certificate to the access client, the client will use the certificate from the
                // Access service.
                .rootCertificate(accessClientUseArtifactoryRootCertificateEnabled.getBoolean() ?
                        newRootCertificateHolder() : null)
                .defaultAuth(adminToken == null ? null : new AccessAuthToken(adminToken))
                .forceRest(isForceRest())
                .forceGrpc(isForceGrpc())
                .startupPingRetries(ConstantValues.accessClientStartupPingRetries.getInt(artifactoryHome))
                .cacheRefreshTimeout(ConstantValues.accessClientCacheRefreshTimeout.getInt(artifactoryHome))
                .grpcClientKeepAliveTimeout(ConstantValues.accessGrpcClientKeepAliveTimeout.getInt(artifactoryHome))
                .grpcClientResubscribeDelay(ConstantValues.accessGrpcClientResubscribeDelay.getInt(artifactoryHome))
                .grpcMaxInboundMessageSize(ConstantValues.accessGrpcMaxInboundMessageSize.getInt(artifactoryHome))
                .projectsConfigCacheExpiry(
                        ConstantValues.accessClientProjectsConfigCacheExpiry.getLong(artifactoryHome))
                .projectsConfigCacheSize(ConstantValues.accessClientProjectsConfigCacheSize.getLong(artifactoryHome))
                .projectsConfigIgnored(ConstantValues.accessClientProjectsConfigIgnored.getBoolean(artifactoryHome))
                .httpSsoConfigIgnored(ConstantValues.accessClientHttpSsoConfigIgnored.getBoolean(artifactoryHome))
                .serverInfo(ServerInfo.builder()
                        .server("Artifactory")
                        .serverRevision(ConstantValues.artifactoryServiceRevision.getString())
                        .serverVersion(ConstantValues.artifactoryServiceVersion.getString())
                        .build());

        if (routerEnabled) {
            try {
                URL url = new URL(getAccessServerSettings().getUrl());
                clientBuilder.grpcUrl(url.getHost() + ":" + url.getPort());
            } catch (MalformedURLException e) {
                log.error("Invalid Router URL", e);
                throw new AccessClientException(e);
            }
        } else {
            clientBuilder.grpcUrl(ConstantValues.accessClientGrpcUrlOverride.getString(artifactoryHome));
        }

        getAuthCacheMaxSize().ifPresent(clientBuilder::authenticationCacheMaxSize);
        getTokenVerifyResultCacheSize().ifPresent(clientBuilder::tokenVerificationResultCacheSize);
        getTokenVerifyResultCacheExpiry().ifPresent(clientBuilder::tokenVerificationResultCacheExpiry);
        getConnectionTimeout().ifPresent(clientBuilder::connectionTimeout);
        getSocketTimeout().ifPresent(clientBuilder::socketTimeout);
        getMaxConnections().ifPresent(clientBuilder::maxConnections);
        getAccessClientPlatformConfigCacheExpiry().ifPresent(clientBuilder::platformConfigCacheExpiry);
        getAccessClientPlatformConfigCacheSize().ifPresent(clientBuilder::platformConfigCacheSize);
        getUserCacheExpireAfterAccessSecs().ifPresent(clientBuilder::expireAfterAccessUserCache);
        getUserCacheExpireAfterWriteSecs().ifPresent(clientBuilder::expireAfterWriteUserCache);
        getUserCacheMaxSize().ifPresent(clientBuilder::userCacheMaxSize);

        // This will allow the Access client to use the scoped principal authorization feature (advanced cache).
        clientBuilder.scopedPrincipalAuthorizationEnabled(true);

        clientBuilder.aclCacheAccessExpirySeconds(ConstantValues
                .aclCacheAccessExpirySeconds.getLong(artifactoryHome));
        clientBuilder.aclCacheWriteExpirySeconds(ConstantValues
                .aclCacheWriteExpirySeconds.getLong(artifactoryHome));

        return clientBuilder;
    }

    private boolean isForceGrpc() {
        return ConstantValues.accessClientForceGrpc.getBoolean(artifactoryHome);
    }

    private boolean isForceRest() {
        return ConstantValues.accessClientForceRest.getBoolean(artifactoryHome);
    }

    private OptionalLong getAuthCacheMaxSize() {
        return getClientLongSetting(ConstantValues.accessClientAuthCacheMaxSize);
    }

    private OptionalInt getMaxConnections() {
        return getClientIntNonNegativeSetting(ConstantValues.accessClientMaxConnections);
    }

    private OptionalInt getSocketTimeout() {
        return getClientIntNonNegativeSetting(ConstantValues.accessClientSocketTimeout);
    }

    private OptionalInt getConnectionTimeout() {
        return getClientIntNonNegativeSetting(ConstantValues.accessClientConnectionTimeout);
    }

    private OptionalLong getTokenVerifyResultCacheSize() {
        return getClientLongSetting(ConstantValues.accessClientTokenVerifyResultCacheSize);
    }

    private OptionalLong getTokenVerifyResultCacheExpiry() {
        return getClientLongSetting(ConstantValues.accessClientTokenVerifyResultCacheExpiry);
    }

    private OptionalLong getAccessClientPlatformConfigCacheExpiry() {
        return getClientLongSetting(ConstantValues.accessClientPlatformConfigCacheExpiry);
    }

    private OptionalLong getAccessClientPlatformConfigCacheSize() {
        return getClientLongSetting(ConstantValues.accessClientPlatformConfigCacheSize);
    }

    private OptionalLong getUserCacheExpireAfterAccessSecs() {
        return getClientLongSetting(ConstantValues.accessClientUserCacheExpireAfterAccessSecs);
    }

    private OptionalLong getUserCacheExpireAfterWriteSecs() {
        return getClientLongSetting(ConstantValues.accessClientUserCacheExpireAfterWriteSecs);
    }

    private OptionalLong getUserCacheMaxSize() {
        return getClientLongSetting(ConstantValues.accessClientUserCacheMaxSize);
    }

    private OptionalInt getClientIntNonNegativeSetting(ConstantValues constantValue) {
        OptionalLong optionalLong = getClientLongSetting(constantValue,
                value -> value <= 0
        );

        if (optionalLong.isPresent()) {
            return OptionalInt.of(Long.valueOf(optionalLong.getAsLong()).intValue());
        }

        return OptionalInt.empty();
    }

    private OptionalLong getClientLongSetting(ConstantValues constantValue) {
        return getClientLongSetting(constantValue, value -> value < 0);
    }

    private OptionalLong getClientLongSetting(ConstantValues constantValue,

            Predicate<Long> defaultTester) {
        long value = constantValue.getLong(artifactoryHome);
        log.debug("sys-prop value: {}={}", constantValue.getPropertyName(), value);
        return defaultTester.test(value) ? OptionalLong.empty() : OptionalLong.of(value);
    }

    private RootCertificateHolder newRootCertificateHolder() {
        return new RootCertificateHolder() {
            @Nullable
            @Override
            public Certificate get() {
                return getRootCertificate();
            }

            @Override
            public void set(@Nullable Certificate certificate) {
                if (certificate != null) {
                    storeRootCertificate(certificate);
                }
            }
        };
    }

    @Nonnull
    @Override
    public ServiceId getServiceId() {
        if (serviceId == null) {
            throw new IllegalStateException("Service ID was not set");
        }
        return serviceId;
    }

    public void setServiceId(@Nonnull ServiceId serviceId) {
        this.serviceId = requireNonNull(serviceId, "service ID is required");
    }

    @Nonnull
    @Override
    public String getServiceNodeId() {
        return artifactoryHome.getHaAwareHostId();
    }

    @Override
    public void storeRootCertificate(@Nonnull Certificate certificate) {
        try {
            FileUtils.forceMkdir(rootCrtFile.getParentFile());
            PemHelper.saveCertificate(rootCrtFile, certificate);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save root.crt file to path: " + rootCrtFile.getAbsolutePath(), e);
        }
    }

    @Nonnull
    @Override
    public Certificate getRootCertificate() {
        try {
            return PemHelper.readCertificate(rootCrtFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read root certificate from file: " + rootCrtFile.getAbsolutePath(),
                    e);
        }
    }

    @Override
    public boolean isRootCertificateExists() {
        return rootCrtFile.exists();
    }

    @Override
    public void storeAdminToken(@Nonnull String tokenValue) {
        setAdminToken(tokenValue);
    }

    @Nonnull
    @Override
    public String getAdminToken() {
        return Optional.ofNullable(getAccessAdminToken())
                .orElseThrow(() -> new IllegalStateException("Admin token missing"));
    }

    private void setAdminToken(String adminToken) {
        if (adminToken == null) {
            cachedValues.invalidate(CACHED_ADMIN_TOKEN);
        } else {
            cachedValues.put(CACHED_ADMIN_TOKEN, adminToken);
        }

        File adminTokenFile = artifactoryHome.getAccessAdminTokenFile();
        saveAccessTokenToFile(adminTokenFile, adminToken);
    }

    @Override
    public boolean isAdminTokenExists() {
        String adminToken = getCachedAdminToken();
        return ((adminToken != null) ? adminToken : getStoredRawAdminToken()) != null;
    }

    @Nullable
    private String getAccessAdminToken() {
        String adminToken = getCachedAdminToken();
        if (adminToken == null) {
            adminToken = getDecryptStoredAccessAdminToken();
            if (adminToken != null) {
                setCachedAdminToken(adminToken);
            }
        }
        return adminToken;
    }

    private String getDecryptStoredAccessAdminToken() {
        String storedAdminToken = getStoredRawAdminToken();
        return decryptIfNeeded(storedAdminToken);
    }

    private String decryptIfNeeded(String token) {
        if (token != null) {
            token = CryptoHelper.decryptIfNeeded(artifactoryHome, token);
        }
        return token;
    }

    @Nullable
    private String getStoredRawAdminToken() {
        File accessAdminTokenFile = artifactoryHome.getAccessAdminTokenFile();
        if (accessAdminTokenFile.exists()) {
            return getTokenFromAccessTokenFile(accessAdminTokenFile);
        }
        return null;
    }

    @Override
    @Nonnull
    public Stream<String> getAllAdminTokenCandidatesWithNullsForMissing() {
        File accessAdminTokenFile = artifactoryHome.getAccessAdminTokenFile();

        Stream<File> accessAdminTokenFiles = Stream.of(accessAdminTokenFile);// first the token file
        Stream<String> fileTokens = accessAdminTokenFiles.map(this::getTokenFromAccessTokenFile);

        return fileTokens.map(this::decryptIfNeeded);
    }

    private String getTokenFromAccessTokenFile(File accessAdminTokenFile) {
        try {
            if (!accessAdminTokenFile.exists()) {
                return null;
            }
            String line = readFirstLine(accessAdminTokenFile);
            if (StringUtils.isNotBlank(line)) {
                log.debug("Got raw access token: from file: {}", accessAdminTokenFile);
                return line;
            }
        } catch (IOException e) {
            log.warn("Got IOException reading token file", e);
        }
        return null;
    }

    String getCachedAdminToken() {
        return (String) cachedValues.getIfPresent(CACHED_ADMIN_TOKEN);
    }

    void setCachedAdminToken(String value) {
        cachedValues.put(CACHED_ADMIN_TOKEN, value);
    }

    @Override
    public void revokeAdminToken() {
        setAdminToken(null);
    }

    void invalidateAdminCredentialsCache() {
        cachedValues.invalidate(CACHED_ADMIN_CREDS);
    }

    @Override
    public void discardBootstrapAdminCredentials() {
        // deprecated
    }

    void invalidateCache() {
        cachedValues.invalidateAll();
    }

    @Override
    public AccessVersion getAccessClientVersion() {
        if (clientVersionFile.exists()) {
            Properties props = new Properties();
            try (InputStream input = new FileInputStream(clientVersionFile)) {
                props.load(input);
                return AccessVersion.read(props);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read access client version from file: " +
                        clientVersionFile.getAbsolutePath(), e);
            }
        }
        return null;
    }

    @Override
    public void storeAccessClientVersion(@Nonnull AccessVersion accessVersion) {
        Properties props = new Properties();
        accessVersion.write(props);
        try {
            FileUtils.forceMkdir(clientVersionFile.getParentFile());
            try (OutputStream out = new FileOutputStream(clientVersionFile)) {
                props.store(out, "JFrog Access Client Version");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save access client version to file: " + this.clientVersionFile, e);
        }
    }

    private void saveAccessTokenToFile(@Nonnull File tokenFile, String tokenString) {
        if (tokenString == null) {
            try {
                log.debug("Deleting access.admin.token");
                Files.deleteIfExists(tokenFile.toPath());
            } catch (IOException e) {
                log.error("Failed delete token file {}", tokenFile, e);
            }
        } else {
            try {
                tokenString = CryptoHelper.encryptIfNeeded(artifactoryHome, tokenString);
                log.debug("Storing token in file access.admin.token");
                Files.write(tokenFile.toPath(), Collections.singletonList(tokenString));
                SecurityFolderHelper.setPermissionsOnSecurityFile(tokenFile.toPath(), PERMISSIONS_MODE_600);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write token to file '" +
                        tokenFile.getAbsolutePath() + "'", e);
            }
        }
    }

    /**
     * Chooses the bundled Access Server URL to use with the following strategy:
     * <ol>
     * <li>System property {@link ConstantValues#accessClientServerUrlOverride} - can be used in tests, online, etc.
     * <li>Config descriptor - Access Client Settings
     * <li>Dev/test mode by default uses a spawned access standalone process with default port
     * (yields NOT-BUNDLED)</li>
     * <li>Detect port on localhost - this is the default case for production Artifactory with bundled Access
     * (yields BUNDLED)</li>
     * </ol>
     *
     * @return the Access Server URL
     * @see ConstantValues#accessClientServerUrlOverride
     * @see ConstantValues#test
     * @see ConstantValues#dev
     * @see ConstantValues#devHa
     */
    private AccessServerSettings getAccessServerSettings() {
        try {
            return (AccessServerSettings) cachedValues.get(CACHED_SERVER_URL, this::getServerSettings);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Could not get Access Server settings.", e);
        }
    }

    private AccessServerSettings getServerSettings() {
        AccessServerSettings serverSettings = null;
        String serverUrl;
        // 1 - URL provided from System Property
        serverUrl = ConstantValues.accessClientServerUrlOverride.getString(artifactoryHome);
        if (isNotBlank(serverUrl)) {
            serverSettings = new AccessServerSettings(serverUrl, "System Property");
        }
        // 2 - System Config
        if (serverSettings == null) {
            String routerUrl = this.routerUrl;
            serverUrl = routerUrl + "/access";
            log.debug("Using Access Server URL (via Router): {}", serverUrl);
            serverSettings = new AccessServerSettings(serverUrl, "System Config");
        }
        log.info("Using Access Server URL: {}", serverSettings);
        return serverSettings;
    }

    private static class AccessServerSettings {
        private final String url;
        private final String source;

        private AccessServerSettings(String url, String source) {
            this.url = url;
            this.source = source;
        }

        public String getUrl() {
            return url;
        }

        @Override
        public String toString() {
            return url + " source: " + source;
        }
    }
}
