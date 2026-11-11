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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.jfrog.sysconf.SysConfigConstants;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.vavr.control.Try;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.security.AuthorizationException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.api.security.access.ProxyTokenRequest;
import org.artifactory.api.security.access.ProxyTokenResponse;
import org.artifactory.api.security.access.TokenInfo;
import org.artifactory.api.security.access.TokenIssuedByOtherServiceException;
import org.artifactory.api.security.access.TokenNotFoundException;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.api.security.access.UserTokenSpec;
import org.artifactory.api.subscriptions.SubscriptionsService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.config.AccessConfigMapUtils;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.security.EncryptionPolicy;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
import org.artifactory.descriptor.security.ldap.group.LdapGroupSetting;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.event.AccessClientBeforeCloseEvent;
import org.artifactory.event.CacheType;
import org.artifactory.event.InvalidateCacheEvent;
import org.artifactory.fs.ItemInfo;
import org.artifactory.metrics.framework.adoption.FeatureAdoptionUtils;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportKind;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.BuildAcl;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.ReleaseBundleAcl;
import org.artifactory.security.RepoAcl;
import org.artifactory.security.SecurityInfo;
import org.artifactory.security.ServletHttpRequestImpl;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.convertor.LdapArtifactoryAndAccessConvertor;
import org.artifactory.security.access.migration.populator.PatchYamlAccessConfigMigrationService;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.EntitlementsEventListener;
import org.artifactory.spring.Reloadable;
import org.artifactory.state.model.ArtifactoryStateManager;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.security.service.access.GroupMapper;
import org.artifactory.storage.db.security.service.access.UserMapper;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.util.AolUtils;
import org.artifactory.util.ThreadDumpUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.access.client.AccessAuthToken;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientBootstrap;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.AuthenticationResult;
import org.jfrog.access.client.auth.GrpcAuthClient;
import org.jfrog.access.client.auth.ldap.AuthSettingsClient;
import org.jfrog.access.client.model.CrowdSetting;
import org.jfrog.access.client.model.HttpSsoSetting;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.jfrog.access.client.model.ProxySetting;
import org.jfrog.access.client.model.SamlEncryptedAssertion;
import org.jfrog.access.client.model.SamlSetting;
import org.jfrog.access.client.platformconfig.GrpcPlatformConfigClient;
import org.jfrog.access.client.platformconfig.LicenseClient;
import org.jfrog.access.client.token.TokenRequest;
import org.jfrog.access.client.token.TokenResponse;
import org.jfrog.access.client.token.TokensInfoResponse;
import org.jfrog.access.common.AccessAuthz;
import org.jfrog.access.common.Issuer;
import org.jfrog.access.common.PermissionScopeMatcher;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.common.SubjectFQN;
import org.jfrog.access.common.token.TokenVerifyResponse;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.key.join.JoinKeyBootstrapper;
import org.jfrog.access.model.Realm;
import org.jfrog.access.rest.imports.ImportEntitiesRequest;
import org.jfrog.access.rest.system.MTlsConfigurationModel;
import org.jfrog.access.rest.system.UserLockPolicyConfig;
import org.jfrog.access.rest.user.CustomDatumRequestImpl;
import org.jfrog.access.rest.user.LoginRequest;
import org.jfrog.access.rest.user.UserRegistrationRequestImpl;
import org.jfrog.access.router.RouterGrpcClient;
import org.jfrog.access.router.registration.RouterRegistrationData;
import org.jfrog.access.token.AccessTokenType;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.verifier.VerifyFailureReason;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.common.opentracing.TraceHeaderInjector;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.jfrog.security.util.ULID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Iterables.concat;
import static com.jfrog.sysconf.SysLayout.SECURITY_DIR;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.artifactory.common.home.ArtifactoryHome.ACCESS_KEYS_DIR_NAME;
import static org.artifactory.common.home.ArtifactoryHome.ACCESS_SUB_DIR;
import static org.artifactory.common.home.ArtifactoryHome.CLUSTER_ID;
import static org.artifactory.common.home.ArtifactoryHome.SERVICE_ID_FILE_NAME;
import static org.artifactory.config.AccessConfigHolder.SECURITY_ANONYMOUS_ACCESS_ENABLED;
import static org.artifactory.config.AccessConfigHolder.SECURITY_MAP;
import static org.artifactory.config.AccessConfigKeys.ACCESS_ADOPTION_CONFIG;
import static org.artifactory.config.AccessConfigKeys.ACCESS_ADOPTION_CONFIG_DISABLED;
import static org.artifactory.mpu.MultipartUploadService.MPU_SCOPE;
import static org.artifactory.security.access.AccessServiceConstants.ARTIFACTORY_SERVICE_ANY_ID_PATTERN;
import static org.artifactory.security.access.AccessServiceConstants.ARTIFACTORY_SERVICE_TYPE;
import static org.artifactory.security.access.AppliedPermissionsScopeToken.APPLIED_PERMISSIONS_GROUPS_PREFIX;
import static org.artifactory.security.access.AppliedPermissionsScopeToken.APPLIED_PERMISSIONS_USER;
import static org.artifactory.security.access.AppliedPermissionsScopeToken.SCOPE_APPLIED_PERMISSIONS_GROUPS_PATTERN;
import static org.artifactory.security.access.AppliedPermissionsScopeToken.SCOPE_APPLIED_PERMISSIONS_USER_PATTERN;
import static org.artifactory.security.access.ArtifactoryAdminScopeToken.V1_SCOPE_SERVICE_ID_ARTIFACTORY_ADMIN_PATTERN;
import static org.artifactory.security.access.ArtifactoryRepoPathScopeToken.PATH_CHECKSUM_PREFIX;
import static org.artifactory.security.access.ArtifactoryRepoPathScopeToken.READ_PERMISSION;
import static org.artifactory.security.access.ArtifactoryRepoPathScopeToken.SCOPE_ARTIFACTORY_REPO_PATH_PATTERN;
import static org.artifactory.security.access.MemberOfGroupsScopeToken.SCOPE_MEMBER_OF_GROUPS_PATTERN;
import static org.artifactory.security.access.convertor.LdapArtifactoryAndAccessConvertor.convertArtifactoryLdapGroupSettingTOAccessGroupSetting;
import static org.artifactory.security.access.convertor.LdapArtifactoryAndAccessConvertor.convertLdapArtifactorySettingToAccessLdapSetting;
import static org.artifactory.storage.db.security.service.access.AclMapper.toFullAccessPermission;
import static org.artifactory.storage.db.security.service.access.UserPropertiesSearchHelper.getSearchableProp;
import static org.jfrog.access.client.proxy.GrpcProxyClientImpl.PROXY_SETTINGS_ALL_KEY;
import static org.jfrog.access.common.SubjectFQN.USERS_NAME_PART;
import static org.jfrog.access.token.JwtAccessToken.SCOPE_DELIMITER;
import static org.jfrog.access.util.AccessCredsFileHelper.ADMIN_DEFAULT_USERNAME;
import static org.jfrog.config.home.JFrogHome.ETC_FOLDER;
import static org.jfrog.security.file.SecurityFolderHelper.PERMISSIONS_MODE_644;
import static org.jfrog.security.file.SecurityFolderHelper.setPermissionsOnSecurityFile;

/**
 * @author Yinon Avraham
 */
@Service
@Lazy
@Reloadable(beanClass = AccessService.class, initAfter = ArtifactoryStateManager.class,
        listenOn = {CentralConfigKey.securityPasswordSettings})
public class AccessServiceImpl implements AccessService, ContextCreationListener, EntitlementsEventListener {

    public static final String ACCESS_BOOTSTRAP_JSON = "access.bootstrap.json";
    private static final Logger log = LoggerFactory.getLogger(AccessServiceImpl.class);
    private static final int MIN_INSTANCE_ID_LENGTH = 20;
    //Accepted scopes:
    private static final String SCOPE_API = "api:*";
    private static final Pattern SCOPE_API_PATTERN = Pattern.compile(Pattern.quote(SCOPE_API));
    private static final String SCOPE_PAIRING = "internal:service-trust/pairing/.*";
    private static final Pattern SCOPE_PAIRING_PATTERN = Pattern.compile(SCOPE_PAIRING);
    private static final String SCOPE_ARTIFACTORY_COLD = "internal:artifactory-cold/.*";
    private static final String SCOPE_ARTIFACTORY_FEDERATED_REPO = "internal:federated-repo/.*";
    private static final Pattern MPU_PATTERN = Pattern.compile(MPU_SCOPE);
    private static final Pattern SCOPE_ARTIFACTORY_COLD_PATTERN = Pattern.compile(SCOPE_ARTIFACTORY_COLD);
    private static final Pattern SCOPE_ARTIFACTORY_FEDERATED_REPO_PATTERN =
            Pattern.compile(SCOPE_ARTIFACTORY_FEDERATED_REPO);
    private static final String DEFAULT_ADMIN_PASSWORD = "password";
    private static final String USER_TOKEN_CREATION_ERROR_MESSAGE =
            "The user: '%s' can only create user token(s) for themselves (requested: %s)";
    private static final String ARTIFACTORY_SERVICE_ID = "artifactory.service_id";
    private static final int AUTH_FAILURE_MAX_RETRY = 15;
    private static final int AUTH_FAILURE_RETRY_INTERVAL = 1000;
    public static final SamlSetting SAML_DEFAULT_SETTINGS = SamlSetting
            .builder()
            .name("default")
            .verifyAudienceRestriction(true)
            .autoUserCreation(true)
            .groupAttribute("")
            .certificate("")
            .emailAttribute("")
            .loginUrl("")
            .logoutUrl("")
            .serviceProviderName("")
            .build();
    public static final OAuthGeneralSettings OAUTH_DEFAULT_SETTINGS = OAuthGeneralSettings
            .builder()
            .enable(false)
            .build();
    private final List<Pattern> acceptedScopePatternsByNonAdmin = Lists
            .newArrayList(SCOPE_API_PATTERN, SCOPE_MEMBER_OF_GROUPS_PATTERN, SCOPE_APPLIED_PERMISSIONS_GROUPS_PATTERN,
                    SCOPE_APPLIED_PERMISSIONS_USER_PATTERN, SCOPE_ARTIFACTORY_REPO_PATH_PATTERN,
                    SCOPE_PAIRING_PATTERN, SCOPE_ARTIFACTORY_COLD_PATTERN, SCOPE_ARTIFACTORY_FEDERATED_REPO_PATTERN);
    private final List<Pattern> acceptedScopePatternsByAdmin = Lists.newArrayList(
            V1_SCOPE_SERVICE_ID_ARTIFACTORY_ADMIN_PATTERN);
    private final List<Pattern> acceptedScopePatterns = Lists.newArrayList(
            concat(acceptedScopePatternsByNonAdmin, acceptedScopePatternsByAdmin));
    private AddonsManager addonsManager;
    private final ApplicationEventPublisher publisher;
    private final ConfigsService configsService;
    private final ContextStateDependantActionRunner contextStateDependantActionRunner =
            new ContextStateDependantActionRunner();
    private UserGroupStoreService userGroupStore;
    private AuthorizationService authorizationService;
    private SecurityService securityService;
    private UserGroupService userGroupService;
    private RepositoryService repositoryService;
    private ArtifactoryHome artifactoryHome;
    private JoinKeyBootstrapper joinKeyBootstrapper;
    private ServiceId serviceId;
    private AccessClient accessClient;
    private AccessRestClient accessRestClient;
    private volatile GrpcAuthClient grpcAuthClient;
    private volatile AuthSettingsClient authSettingsClient;
    private String accessServerName;
    private ArtifactoryAccessClientConfigStore configStore;
    private long lastAdminSecurityIssueCheck;
    private Boolean hasAdminSecurityIssue;
    private final ArtifactoryContext context;
    private final Set<AccessClientUpdateListener> accessClientUpdateListeners = new HashSet<>();

    private LicenseClient licenseClient;
    private GrpcPlatformConfigClient platformConfigClient;
    private int authFailureMaxRetry;

    private final PatchYamlAccessConfigMigrationService yamlAccessConfigMigrationService;
    public static final long USER_TOKEN_MAX_EXPIRES_UNLIMITED = 0L;
    private static final String SCOPE_DUMMY = "dummy";

    public ReadWriteLock rwAccessClientLock = new ReentrantReadWriteLock(false);

    // Static map to hold the old scopes and their replacements
    private static final Map<String, String> scopeMap = new HashMap<>();

    // Static block to populate the map once on class loading
    static {
        scopeMap.put("ui:*", "internal:ui");
        scopeMap.put("member-of-groups:<groups>", "applied-permissions/groups:<groups>");
        scopeMap.put("member-of-groups:*", "applied-permissions/user");
        scopeMap.put("admin", "applied-permissions/admin");
        scopeMap.put("<service-id>:admin", "applied-permissions/admin");
        scopeMap.put("ha-propagate", "internal:ha-propagate:x");
        scopeMap.put("api:ha-propagate", "internal:ha-propagate:x");
        scopeMap.put("internal-sync", "internal:ha-propagate:x");
        scopeMap.put("internal-federation", "internal:federation:all");
        scopeMap.put("rwd", "internal:binarystore:r,w,d");
        scopeMap.put("blob:rwd", "internal:binarystore:r,w,d");
        scopeMap.put("authentication:login", "internal:auth:x");
    }

    @Autowired
    public AccessServiceImpl(ApplicationEventPublisher publisher,
            ConfigsService configsService, ApplicationContext applicationContext) {
        this.publisher = publisher;
        this.configsService = configsService;
        this.context = (ArtifactoryContext) applicationContext;
        this.yamlAccessConfigMigrationService = new PatchYamlAccessConfigMigrationService();
        setAuthFailureMaxRetry(AUTH_FAILURE_MAX_RETRY);
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    public static String getServiceIdFilePathUnderEtc() {
        return Paths.get(SECURITY_DIR, ACCESS_SUB_DIR, getServiceIdFilePath()).toString();
    }

    private static String getServiceIdFilePath() {
        return Paths.get(ACCESS_KEYS_DIR_NAME, SERVICE_ID_FILE_NAME).toString();
    }

    static String normalizeInstanceId(String id) {
        Matcher matcher = ServiceId.ELEMENT_PATTERN.matcher(id);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            //Fill the gap (if any)
            for (int i = builder.length(); i < matcher.start(); i++) {
                builder.append("_");
            }
            builder.append(matcher.group());
        }
        String normalizedId = builder.toString();
        if (normalizedId.length() < MIN_INSTANCE_ID_LENGTH) {
            throw new IllegalArgumentException("Instance ID is too short. (normalized='" + normalizedId +
                    "', original='" + id + "')");
        }
        return normalizedId;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setAuthorizationService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Autowired
    public void setUserGroupService(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    @Autowired
    public void setUserGroupStore(UserGroupStoreService userGroupStore) {
        this.userGroupStore = userGroupStore;
    }

    @Autowired
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }


    public void setAuthFailureMaxRetry(int authFailureMaxRetry) {
        this.authFailureMaxRetry = authFailureMaxRetry;
    }

    public int authFailureMaxRetry() {
        return authFailureMaxRetry;
    }

    @Autowired
    public void setAccessRestClient(AccessRestClient accessRestClient) {
        this.accessRestClient = accessRestClient;
    }

    SecurityService securityService() {
        return securityService;
    }

    ArtifactoryHome artifactoryHome() {
        return artifactoryHome;
    }

    @Override
    public void registerAcceptedScopePattern(@Nonnull Pattern pattern) {
        synchronized (acceptedScopePatterns) {
            log.debug("Registering accepted scope pattern: {}", requireNonNull(pattern, "pattern is required"));
            if (acceptedScopePatterns.stream().noneMatch(p -> p.pattern().equals(pattern.pattern()))) {
                acceptedScopePatterns.add(pattern);
            } else {
                log.debug("Pattern already exists in the accepted scope patterns: '{}'", pattern);
            }
        }
    }

    @Nonnull
    @Override
    public List<TokenInfo> getTokenInfos() {
        TokensInfoResponse tokensInfoResponse = ensureAuth(() -> getAccessClient().token().getTokensInfo());
        return tokensInfoResponse.getTokens().stream()
                .map(this::toTokenInfo)
                .filter(this::isNonInternalToken)
                .collect(Collectors.toList());
    }

    @Override
    public AccessClient getAccessClient() {
        rwAccessClientLock.readLock().lock();
        try {
            return accessClient;
        } finally {
            rwAccessClientLock.readLock().unlock();
        }
    }

    @Override
    public GrpcPlatformConfigClient getPlatformConfigClient() {
        if (platformConfigClient == null) {
            rwAccessClientLock.writeLock().lock();
            try {
                if (null == platformConfigClient) {
                    platformConfigClient = getAccessClient().cachedGrpcPlatformConfigs();
                }
                return platformConfigClient;
            } finally {
                rwAccessClientLock.writeLock().unlock();
            }
        }
        return platformConfigClient;
    }

    @Override
    public GrpcAuthClient getGrpcAuthClient() {
        if (grpcAuthClient == null) {
            rwAccessClientLock.writeLock().lock();
            try {
                if (grpcAuthClient == null) {
                    grpcAuthClient = getAccessClient().grpcAuth(getAccessAuthSettingsClient());
                }
            } finally {
                rwAccessClientLock.writeLock().unlock();
            }
        }
        return grpcAuthClient;
    }

    @Override
    public AuthSettingsClient getAccessAuthSettingsClient() {
        if (authSettingsClient == null) {
            rwAccessClientLock.writeLock().lock();
            try {
                if (authSettingsClient == null) {
                    authSettingsClient = getAccessClient().cachedAuthSettingClient(
                            artifactoryHome.getMasterEncryptionWrapper());
                }
            } finally {
                rwAccessClientLock.writeLock().unlock();
            }
        }
        return authSettingsClient;
    }

    @Override
    public <T> T ensureAuth(Callable<T> call) {
        try {
            return callWithRetry(call);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AccessClientException(e);
        }
    }

    private <T> T callWithRetry(Callable<T> call) throws InterruptedException {
        int retryAttempt = 0;
        RuntimeException lastException;
        do {
            try {
                return call.call();
            } catch (AccessClientHttpException e) {
                lastException = e;
                if (e.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
                    retryAttempt++;
                    waitForRejoin();
                } else {
                    throw e;
                }
            } catch (StatusRuntimeException e) {
                lastException = e;
                if (e.getStatus() == Status.UNAUTHENTICATED) {
                    retryAttempt++;
                    waitForRejoin();
                } else {
                    throw e;
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new AccessClientException(e);
            }
        } while (retryAttempt < authFailureMaxRetry());

        throw lastException;
    }

    private void waitForRejoin() throws InterruptedException {
        String msg = "Access Token is invalid, waiting for access client to rejoin and retrying...";
        log.warn(msg);
        Thread.sleep(AUTH_FAILURE_RETRY_INTERVAL);
    }

    private void runWithFallback(Runnable runnable) {
        ensureAuth(() -> {
            runnable.run();
            return null;
        });
    }

    private TokenInfo toTokenInfo(org.jfrog.access.client.token.TokenInfo clientTokenInfo) {
        return new TokenInfoImpl(clientTokenInfo.getTokenId(),
                clientTokenInfo.getIssuer(),
                clientTokenInfo.getSubject(),
                clientTokenInfo.getExpirationTime(),
                clientTokenInfo.getIssuedAt(),
                clientTokenInfo.isRefreshable());
    }

    private boolean isNonInternalToken(TokenInfo tokenInfo) {
        try {
            SubjectFQN subject = SubjectFQN.fromFullyQualifiedName(tokenInfo.getSubject());
            if (subject.getServiceId().equals(getArtifactoryServiceId())) {
                return UserTokenSpec.isUserToken(tokenInfo);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void init() {
        artifactoryHome = ArtifactoryHome.get();
        joinKeyBootstrapper = new JoinKeyBootstrapper(artifactoryHome.getSysConfig(),
                artifactoryHome.getSysConfig().helper().getBootstrapKeyReadTimeoutMs());
        String oldServiceId = initServiceIdAndReturnOldValue();
        initAccessClientIfNeeded(oldServiceId);
        initAccessAdminDefaultPasswordCheck();
        updateAccessAdoptionConfig();
        updateAccessProperties();
        updateLdapConfiguration(AccessConfigHolder.getInstance().drainLdapSetting(), AccessConfigHolder.getInstance()
                .drainLdapGroupSetting());
    }

    private void updateAccessProperties() {
        Map<String, Object> accessConfigMap = new HashMap<>();
        AccessConfigMapUtils.addDisableRememberMe(ConstantValues.securityDisableRememberMe.getBoolean(),
                accessConfigMap);
        updateConfigurationModel(accessConfigMap);
    }

    private void initAccessClientIfNeeded(String oldServiceId) {
        rwAccessClientLock.writeLock().lock();
        try {
            if (accessClient == null) {
                initAccessService(oldServiceId);
            }
        } finally {
            rwAccessClientLock.writeLock().unlock();
        }
    }

    @Override
    public void initAccessService(String oldServiceId) {
        rwAccessClientLock.writeLock().lock();
        try {
            AccessClient oldAccessClient = getAccessClient();
            boolean shouldCloseOldClient = false;
            try {
                configStore = new ArtifactoryAccessClientConfigStore(serviceId);
                bootstrapAccessClient();
                shouldCloseOldClient = true;
                accessServerName = getAccessServerName();
                licenseClient = getAccessClient().cachedLicense();
                platformConfigClient = null;
                authSettingsClient = null;
                grpcAuthClient = null;
                log.debug("Sending License to Access");
                sendLicenseToAccess();
                log.debug("License sent to Access");
                registerInFederation(oldServiceId, false);
                log.info("Initialized access service successfully with client id {}, closing old client id {}",
                        getAccessClient().hashCode(), oldAccessClient != null ? oldAccessClient.hashCode() : "[null]");
            } finally {
                if (shouldCloseOldClient) {
                    closeOldAccessClient(oldAccessClient);
                }
            }
        } finally {
            rwAccessClientLock.writeLock().unlock();
        }
    }

    private void closeOldAccessClient(AccessClient oldAccessClient) {
        if (oldAccessClient == null) {
            return;
        }
        try {
            publisher.publishEvent(new AccessClientBeforeCloseEvent(this));
            log.debug("Closing access client");
            oldAccessClient.close();
        } catch (Exception e) {
            log.debug("Failed closing old access client: ", e);
        }
    }

    AccessClient buildAccessClient() {
        TraceHeaderInjector injector = ContextHelper.get().beanForType(TraceHeaderInjector.class);
        return configStore.newClientBuilder()
                .originUser(() -> securityService.currentUsername())
                .originUserIp(() -> AuthenticationHelper.getRemoteAddress(AuthenticationHelper.getAuthentication()))
                .traceHeaderInjector(injector)
                .create();
    }

    /**
     * Initializes the Artifactory Service ID. The service_id resides in the database, but can also be imported from
     * the filesystem to support transition from older versions to recent ones, and system import. The import from the
     * filesystem can also be made from the legacy $CLUSTER_HOME directory. If the service_id changes for some reason,
     * the Access client admin token is revoked.
     *
     * @return original ServiceId if existed
     */
    public String initServiceIdAndReturnOldValue() {
        boolean serviceIdChanged = false;
        String originalServiceId = configsService.getConfig(ARTIFACTORY_SERVICE_ID);
        // Try to import from filesystem if it exists
        tryToImportServiceId();
        String serviceId = configsService.getConfig(ARTIFACTORY_SERVICE_ID);
        if (StringUtils.isNotBlank(serviceId)) {
            serviceIdChanged = setServiceIdAndCheckIfChanged(originalServiceId, serviceId);
        } else {
            // Case of first Artifactory startup
            String instanceId = generateServiceInstanceID();
            this.serviceId = new ServiceId(ARTIFACTORY_SERVICE_TYPE, instanceId);
            try {
                configsService.addConfig(ARTIFACTORY_SERVICE_ID, this.serviceId.toString(), System.currentTimeMillis());
                log.debug("Successfully generated serviceId and uploaded to DB: {}", this.serviceId);
            } catch (StorageException e) {
                // Failed over insert, other node insert serviceId already, get what is inside DB.
                log.debug("Couldn't upload newly generated serviceId to DB, retrying reading from DB for existing one");
                pause();
                serviceId = configsService.getConfig(ARTIFACTORY_SERVICE_ID);
                if (StringUtils.isNotBlank(serviceId)) {
                    this.serviceId = ServiceId.fromFormattedName(serviceId);
                    log.debug("Successfully loaded serviceId from DB: {}", this.serviceId);
                } else {
                    // This means a true sql error, throw it.
                    throw new RuntimeException("Failed to create/load serviceId", e);
                }
            }
        }
        log.info("Initialized new service id: " + this.serviceId);
        return serviceIdChanged ? originalServiceId : null;
    }

    protected boolean setServiceIdAndCheckIfChanged(String originalServiceId, String serviceId) {
        this.serviceId = ServiceId.fromFormattedName(serviceId);
        log.debug("Successfully loaded serviceId from config: " + this.serviceId);
        if (StringUtils.isNotBlank(originalServiceId) && !originalServiceId.equals(serviceId)) {
            log.info("The service_id has changed. This will entail re-generation of the Access admin token " +
                    "and re-registration in Access Federation");
            return true;
        }
        return false;
    }

    private void tryToImportServiceId() {
        // Trying to load service id from file system (could be either system import or old Artifactory version)
        File serviceIdFile = getAccessServiceIdFile();
        // Support legacy cluster file and corrupted states where cluster.id file exist but service_id file not
        File clusterIdFile = getClusterIdFile();
        try {
            File candidate = chooseBestCandidate(serviceIdFile, clusterIdFile);
            // If candidate file exists, import it and delete it. Otherwise, do nothing.
            if (candidate.exists()) {
                log.debug("Instance is HA-Primary or Pro, handling serviceId local file");
                String formattedServiceId = Files.readAllLines(candidate.toPath()).get(0);
                String dbServiceId = configsService.getConfig(ARTIFACTORY_SERVICE_ID);
                // Update db
                if (StringUtils.isNotBlank(dbServiceId)) {
                    // Case there is an old serviceId in DB
                    overwriteServiceId(formattedServiceId, dbServiceId);
                } else {
                    // Case there is no serviceId in DB
                    configsService.addConfigInNewTransaction(ARTIFACTORY_SERVICE_ID, formattedServiceId,
                            System.currentTimeMillis());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize the service ID.", e);
        }
        // Rename the file to avoid future imports (even on non-primary nodes)
        renameOldFileName(serviceIdFile);
        renameOldFileName(clusterIdFile);
    }

    private File chooseBestCandidate(File serviceIdFile, File clusterIdFile) {
        if (!serviceIdFile.exists() && artifactoryHome.isHaConfigured()) {
            return clusterIdFile;
        }
        return serviceIdFile;
    }

    private void overwriteServiceId(String formattedServiceId, String dbServiceId) {
        configsService
                .updateConfigInNewTransaction(ARTIFACTORY_SERVICE_ID, formattedServiceId, System.currentTimeMillis());
        log.warn("ServiceId overwrite: detected {} file. In case of HA: if you want to set a new " +
                        "ServiceId you need to restart each node. Old value: {}, new value: {}",
                SERVICE_ID_FILE_NAME, dbServiceId, formattedServiceId);
    }

    private File getAccessServiceIdFile() {
        return new File(artifactoryHome.getAccessClientDir(), getServiceIdFilePath());
    }

    private File getClusterIdFile() {
        return new File(artifactoryHome.getEtcDir(), CLUSTER_ID);
    }

    private void renameOldFileName(File file) {
        // If file doesn't exist no need to move it.
        if (!file.exists()) {
            return;
        }
        long date = System.currentTimeMillis();
        File targetFile = new File(file.getAbsolutePath() + "." + date + ".bak");
        if (!file.renameTo(targetFile)) {
            log.warn("Failed to delete old file from file system: {}", file.getAbsoluteFile());
        }
    }

    private ServiceId createServiceId(String serviceId) {
        return ServiceId.fromFormattedName(serviceId);
    }

    private String generateServiceInstanceID() {
        String id = ULID.random().toLowerCase();
        return normalizeInstanceId(id);
    }

    @Nonnull
    @Override
    public TokenResponse createTokenWithAccessAdminCredentials(@Nonnull String serviceIdAsString) {
        ServiceId tokenSubjectServiceId;
        try {
            tokenSubjectServiceId = createServiceId(serviceIdAsString);
        } catch (RuntimeException oops) {
            log.error("Failed to parse '{}' as valid service id for token request: {}", serviceIdAsString,
                    oops.getMessage());
            log.debug("", oops);
            throw oops;
        }
        try {
            List<String> scopes = Collections.singletonList(AccessAuthz.ADMIN);
            String accessServiceId = ensureAuth(
                    () -> getAccessClient().system().getAccessServiceId().getFormattedName());
            TokenRequest tokenRequest = new TokenRequest(scopes, false, tokenSubjectServiceId.toString(), null,
                    AccessClientBootstrap.SERVICE_ADMIN_TOKEN_EXPIRY, null, Lists.newArrayList(accessServiceId));
            assertAdminUserCanCreateToken(tokenRequest);
            tokenRequest = replacePathTokenScopeWithChecksum(tokenRequest);
            return getAccessClient().token().create(tokenRequest);
        } catch (AccessClientException e) {
            final String err =
                    String.format("Failed to create token for subject %s: %s", tokenSubjectServiceId,
                            e.getMessage());
            log.error(err);
            log.debug(err, e);
            throw new IllegalStateException(err, e);
        }
    }

    @Nonnull
    @Override
    public CreatedTokenInfo createToken(@Nonnull TokenSpec tokenSpec) {
        return createToken(tokenSpec, false, false);
    }

    @Nonnull
    @Override
    public CreatedTokenInfo createCrossPlatformToken(@Nonnull TokenSpec tokenSpec, @Nonnull String subject) {
        // TODO (Gidi) this code is not finished, the method allows admin permission on other platform
        try {
            // Create remote Artifactory service id
            String[] split = StringUtils.split(subject, "@");
            ServiceId serviceId = new ServiceId("jfrt", split[1]);
            // Create subject form remote service id
            subject = tokenSpec.createSubject(serviceId).toString();
            // Fill minimal requirement for token
            List<String> scope = Lists.newArrayList(tokenSpec.getScope());
            addUserGroupsToScopeIfNeeded(scope, tokenSpec);
            List<String> audience = getEffectiveAudience(tokenSpec);
            TokenRequest tokenRequest = new TokenRequest(scope, tokenSpec.isRefreshable(), subject, getIssuer(),
                    tokenSpec.getExpiresIn(), null, audience);
            String currentUsername = securityService.currentUsername();
            // Only system user can create cross platform token for internal communication
            if (!securityService.isAdmin() || !SecurityService.USER_SYSTEM.equals(currentUsername)) {
                throw new AuthorizationException(String.format(
                        "The user: '%s' is not allowed to create cross platform token(s) to service %s",
                        currentUsername, tokenRequest.getSubject()));
            }
            TokenRequest checksumScopedTokenRequest = replacePathTokenScopeWithChecksum(tokenRequest);
            return ensureAuth(() -> {
                        TokenResponse tokenResponse = getAccessClient().token().create(checksumScopedTokenRequest);
                        return toCreatedTokenInfo(tokenResponse, scope);
                    }
            );
        } catch (AccessClientException e) {
            log.error("Failed to create token for subject '{}': {}", subject, e.getMessage());
            log.debug("Failed to create token for subject '{}'", subject, e);
            throw new AccessClientException("Failed to create token for subject '" + subject + "'.", e);
        }
    }

    @Override
    @Nonnull
    public CreatedTokenInfo createToken(@Nonnull TokenSpec tokenSpec,
            boolean skipUserCanCreateLongLivedTokenAssertion,
            boolean skipAcceptedScopeAssertion) {
        return ensureAuth(() -> {
            String subject = null;
            try {
                subject = tokenSpec.createSubject(serviceId).toString();
                List<String> scope = getEffectiveScope(tokenSpec, skipAcceptedScopeAssertion);
                List<String> audience = getEffectiveAudience(tokenSpec);
                TokenRequest tokenRequest = new TokenRequest(scope, tokenSpec.isRefreshable(), subject, getIssuer(),
                        tokenSpec.getExpiresIn(), tokenSpec.getExtension(), audience, tokenSpec.isReference(),
                        tokenSpec.getDescription(), null);
                assertLoggedInCanCreateToken(tokenRequest, skipUserCanCreateLongLivedTokenAssertion);
                tokenRequest = replacePathTokenScopeWithChecksum(tokenRequest);
                TokenResponse tokenResponse = getAccessClient().token().create(tokenRequest);
                return toCreatedTokenInfo(tokenResponse, scope);
            } catch (AccessClientHttpException e) {
                throw e;
            } catch (AccessClientException e) {
                log.error("Failed to create token for subject '{}': {}", subject, e.getMessage());
                log.debug("Failed to create token for subject '{}'", subject, e);
                throw new AccessClientException("Failed to create token for subject '" + subject + "'.", e);
            }
        });
    }

    @Nonnull
    @Override
    public CreatedTokenInfo createNoPermissionToken(@Nonnull List<String> scope,
            @Nonnull TokenSpec tokenSpec, @Nullable String extraData) {
        return ensureAuth(() -> {
            String subject = null;
            try {
                subject = tokenSpec.createSubject(serviceId).toString();
                List<String> audience = getEffectiveAudience(tokenSpec);
                TokenRequest tokenRequest = new TokenRequest(scope, tokenSpec.isRefreshable(), subject, null,
                        tokenSpec.getExpiresIn(), extraData, audience);
                tokenRequest = replacePathTokenScopeWithChecksum(tokenRequest);
                TokenResponse tokenResponse = getAccessClient().token().create(tokenRequest);
                return toCreatedTokenInfo(tokenResponse, scope);
            } catch (AccessClientException e) {
                log.error("Failed to create token for subject '{}': {}", subject, e.getMessage());
                log.debug("Failed to create token for subject '{}'", subject, e);
                throw new AccessClientException("Failed to create token for subject '" + subject + "'.", e);
            }
        });
    }

    @Override
    public ProxyTokenResponse createTokenAsProxy(@Nonnull TokenSpec<?> tokenSpec, String username, String grantType) {
        String audienceAsString = StringUtils.join(getEffectiveAudience(tokenSpec), " ");
        List<String> accessScopes = replaceScopesWithAccessScopes(tokenSpec.getScope());
        String scopeAsString = StringUtils.join(accessScopes, SCOPE_DELIMITER);
        ProxyTokenRequest proxyTokenRequest = new ProxyTokenRequest(
                grantType,
                username,
                scopeAsString,
                tokenSpec.getExpiresIn(),
                tokenSpec.getRefreshable(),
                tokenSpec.getDescription(),
                audienceAsString,
                tokenSpec.isReference()
        );
        return accessRestClient.executePostRequest(proxyTokenRequest,
                "/api/v1/tokens",
                getAuthorizationValue(),
                ProxyTokenResponse.class);
    }

    @Nonnull
    private static String getAuthorizationValue() {
        if (AuthenticationHelper.getAuthentication() instanceof AccessTokenAuthentication authentication) {
            return "Bearer " + authentication.getAccessTokenValue();
        } else {
            SimpleUser principal = (SimpleUser) AuthenticationHelper.getAuthentication().getPrincipal();
            String basicAuth = principal.getUsername() + ":" + principal.getPassword();
            log.warn("Trying to access Access server with basic authentication for the username: {}",
                    principal.getUsername());
            return "Basic " + Base64.getEncoder().encodeToString(basicAuth.getBytes(UTF_8));
        }
    }

    private List<String> replaceScopesWithAccessScopes(List<String> scopes) {
        return scopes.stream()
                .map(this::getScopeReplacement)
                .toList();
    }

    private String getScopeReplacement(String scope) {
        if (scope.startsWith("member-of-groups:") && !scope.endsWith("*")) {
            return scope.replace("member-of-groups:", "applied-permissions/groups:");
        }
        return scopeMap.getOrDefault(scope, scope);
    }

    /**
     * Replace all ArtifactoryRepoPathScopeToken tokens with a checksum repoPath.
     */
    private TokenRequest replacePathTokenScopeWithChecksum(TokenRequest tokenRequest) {
        List<String> newScope = Lists.newArrayList();
        List<String> scope = tokenRequest.getScope();
        for (String scopeToken : scope) {
            if (ArtifactoryRepoPathScopeToken.accepts(scopeToken)) {
                ArtifactoryRepoPathScopeToken parse = ArtifactoryRepoPathScopeToken.parse(scopeToken);
                if (!parse.isChecksumPath()) {
                    newScope.add(
                            PATH_CHECKSUM_PREFIX + ":" + parse.getRepoPathChecksum() + ":" + parse.getPermissions());
                } else { // already checksum
                    newScope.add(scopeToken);
                }
            } else {
                newScope.add(scopeToken);
            }
        }
        return new TokenRequest(newScope, tokenRequest.isRefreshable(), tokenRequest.getSubject(),
                tokenRequest.getIssuer(),
                tokenRequest.getExpiresIn(),
                tokenRequest.getExtension(), tokenRequest.getAudience(), tokenRequest.isIncludeReferenceToken(),
                tokenRequest.getDescription(), null);
    }

    private void assertLoggedInCanCreateToken(TokenRequest tokenRequest,
            boolean skipUserCanCreateLongLivedTokenAssertion) {
        String currentUsername = securityService.currentUsername();
        if (securityService.isAdmin() || SecurityService.USER_SYSTEM.equals(currentUsername)
                || securityService.hasMcScope()) {
            assertAdminUserCanCreateToken(tokenRequest);
        } else {
            assertNonAdminUserCanCreateToken(tokenRequest, currentUsername, skipUserCanCreateLongLivedTokenAssertion);
        }
        assertAllAudienceAreArtifactoryInstances(tokenRequest);
    }

    private void assertAllAudienceAreArtifactoryInstances(TokenRequest tokenRequest) {
        Optional<String> illegalAudience = tokenRequest.getAudience().stream()
                .filter(aud -> !ARTIFACTORY_SERVICE_ANY_ID_PATTERN.matcher(aud).matches())
                .findFirst();
        if (illegalAudience.isPresent()) {
            throw new AuthorizationException("Illegal audience: " + illegalAudience.get() + ", audience can contain " +
                    "only service IDs of Artifactory servers.");
        }
    }

    private void assertNonAdminUserCanCreateToken(TokenRequest tokenRequest, String currentUsername,
            boolean skipUserCanCreateLongLivedTokenAssertion) {
        //non-admin users can only create tokens for themselves under this artifactory service ID
        if (!UserTokenSpec.isUserTokenSubject(tokenRequest.getSubject())) {
            throw new AuthorizationException(
                    String.format(USER_TOKEN_CREATION_ERROR_MESSAGE,
                            currentUsername, tokenRequest.getSubject()));
        }
        String subjectUsername = UserTokenSpec.extractUsername(tokenRequest.getSubject());
        if (!currentUsername.equalsIgnoreCase(subjectUsername)) {
            throw new AuthorizationException(
                    String.format(USER_TOKEN_CREATION_ERROR_MESSAGE,
                            currentUsername, subjectUsername));
        }
        ServiceId subjectServiceId = SubjectFQN.fromFullyQualifiedName(tokenRequest.getSubject()).getServiceId();
        if (!serviceId.equals(subjectServiceId)) {
            throw new AuthorizationException(String.format(
                    "The user: '%s' can only create user token(s) for themselves under this Artifactory service ID " +
                            "(requested: %s)",
                    currentUsername, tokenRequest.getSubject()));
        }
        assertValidScopeForNonAdmin(tokenRequest.getScope());
        //non-admin users can have limited expires in
        assertValidExpiresInForNonAdmin(tokenRequest, currentUsername, skipUserCanCreateLongLivedTokenAssertion);
    }

    private void assertAdminUserCanCreateToken(TokenRequest tokenRequest) {
        tokenRequest.getScope().forEach(scopeToken -> {
            if (ArtifactoryAdminScopeToken.accepts(scopeToken) &&
                    !ArtifactoryAdminScopeToken.isAdminScopeOnService(scopeToken, getArtifactoryServiceId())) {
                throw new AuthorizationException("Admin can create token with admin privileges only on this " +
                        "Artifactory instance: " + getArtifactoryServiceId() +
                        " (requested: " + serviceId + ")");
            }
            if (ArtifactoryRepoPathScopeToken.accepts(scopeToken)) {
                assertPathInTokenExists(ArtifactoryRepoPathScopeToken.getPath(scopeToken));
            }
        });
    }

    private void assertValidScopeForNonAdmin(@Nonnull JwtAccessToken accessToken, @Nullable List<String> scopes) {
        List<String> scopesToValidate = nullSafeScope(scopes);
        Issuer issuer = new Issuer(accessToken.getIssuer());
        String issuedBy = issuer.getIssuedBy();
        if (issuedBy == null) {
            // No token owner identity: group membership and per-user repo-path permissions cannot be evaluated.
            // Unsupported-scope and path-existence checks still run on the effective scope (RTDEV-92030).
            log.debug("Token owner wasn't found - validating scopes for refresh of token {}",
                    accessToken.getTokenId());
            assertUnsupportedScopesForNonAdminRefresh(scopesToValidate);
            assertRepoPathsExistForScope(scopesToValidate);
            return;
        }
        UserInfo user;
        try {
            user = userGroupService.findUser(issuedBy);
        } catch (UsernameNotFoundException e) {
            log.debug("Cannot refresh token " + accessToken.getTokenId(), e);
            throw new AuthorizationException("Cannot refresh token, token owner does not exist");
        }
        if (!user.isEffectiveAdmin() && !SecurityService.USER_SYSTEM.equals(issuedBy)) {
            if (!issuedBy.equals(SubjectFQN.extractUsername(accessToken.getSubject()))) {
                throw new AuthorizationException(
                        "Cannot refresh token, user: '" + issuedBy + "' is not compatible with token subject");
            }
            assertValidScopeForNonAdminRefresh(scopesToValidate, user);
        }
    }

    private void assertValidScopeForNonAdmin(List<String> scopes) {
        List<String> scopesToValidate = nullSafeScope(scopes);
        Optional<String> unsupportedScopeToken = scopesToValidate.stream()
                .filter(this::scopeUnsupportedForNonAdmin)
                .findFirst();
        if (unsupportedScopeToken.isPresent()) {
            throw new AuthorizationException("Logged in user cannot request token with scope: " +
                    unsupportedScopeToken.get());
        }
        assertValidGroupsForNonAdmin(scopesToValidate, userGroupService.currentUser());
        assertDummyScopeForNonAdmin();
        assertUserPermissionScopeForNonAdmin(scopesToValidate);
        assertValidRepoPathsForNonAdmin(scopesToValidate);
    }

    /**
     * Scope checks for the refresh path.
     * parseToken() decodes without verifying the signature, so the effective scope must be
     * validated independently of the token claims (RTDEV-92030).
     *
     * assertUserPermissionScopeForNonAdmin and assertDummyScopeForNonAdmin inspect
     * AuthenticationHelper.getAuthentication() and are silent no-ops when the caller is
     * unauthenticated (simple refresh flow). This is intentional: those guards enforce
     * constraints on the caller's own authentication token, which is not present in the
     * unauthenticated path. Token-creation time already enforced them.
     *
     * Repo-path permissions are validated against the token owner (issuer), not the HTTP
     * caller, so refresh cannot inherit repo access from a different authenticated principal.
     */
    private void assertValidScopeForNonAdminRefresh(@Nonnull List<String> scopes, @Nonnull UserInfo tokenOwner) {
        assertValidGroupsForNonAdmin(scopes, tokenOwner);
        assertUnsupportedScopesForNonAdminRefresh(scopes);
        assertValidRepoPathsForNonAdminRefresh(scopes, tokenOwner);
        assertUserPermissionScopeForNonAdmin(scopes);
        assertDummyScopeForNonAdmin();
    }

    private void assertUnsupportedScopesForNonAdminRefresh(@Nonnull List<String> scopes) {
        Optional<String> unsupportedScopeToken = scopes.stream()
                .filter(this::scopeUnsupportedForNonAdmin)
                .findFirst();
        if (unsupportedScopeToken.isPresent()) {
            throw new AuthorizationException(
                    "Cannot refresh token with scope not permitted for non-admin user: " +
                            unsupportedScopeToken.get());
        }
    }

    private void assertRepoPathsExistForScope(@Nonnull List<String> scopes) {
        collectRepoPathsFromScope(getNonChecksumRepoPathScopes(scopes)).forEach(this::assertPathInTokenExists);
    }

    private boolean isNonChecksumRepoPathScope(String scopeToken) {
        return ArtifactoryRepoPathScopeToken.accepts(scopeToken)
                && !ArtifactoryRepoPathScopeToken.parse(scopeToken).isChecksumPath();
    }

    @Nonnull
    private List<String> getNonChecksumRepoPathScopes(@Nonnull List<String> scopes) {
        return scopes.stream().filter(this::isNonChecksumRepoPathScope).toList();
    }

    @Nonnull
    private List<String> nullSafeScope(@Nullable List<String> scopes) {
        return scopes == null ? Collections.emptyList() : scopes;
    }

    /**
     * if we have scope 'applied-permissions/user' in scopes, we should have it in existing token
     *
     * @param scopes scopes from token request.
     */
    @VisibleForTesting
    void assertUserPermissionScopeForNonAdmin(List<String> scopes) {
        boolean userPermissionsExists = userPermissionsExists(scopes);
        if (userPermissionsExists) {
            Authentication authentication = AuthenticationHelper.getAuthentication();
            if (authentication instanceof AccessTokenAuthentication accessTokenAuthentication) {
                List<String> scopesFromToken = accessTokenAuthentication.getAccessToken().getScope();
                boolean userPermissionsExistsInToken = userPermissionsExists(scopesFromToken);
                if (!userPermissionsExistsInToken) {
                    throw new AuthorizationException("user cannot create token with " +
                            "'applied-permissions/user' if it not exists in authentication token");
                }
            }
        }
    }

    @VisibleForTesting
    boolean userPermissionsExists(List<String> scopes) {
        if (scopes == null) {
            return false;
        }
        return scopes.stream().anyMatch(APPLIED_PERMISSIONS_USER::equalsIgnoreCase);
    }

    void assertDummyScopeForNonAdmin() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication instanceof AccessTokenAuthentication accessTokenAuthentication) {
            List<String> scopesFromToken = accessTokenAuthentication.getAccessToken().getScope();
            boolean dummyScope = scopesFromToken.stream().anyMatch(SCOPE_DUMMY::equalsIgnoreCase);
            if (dummyScope) {
                throw new AuthorizationException("user with dummy scope cannot create token");
            }
        }
    }

    private boolean scopeUnsupportedForNonAdmin(String scopeToken) {
        return acceptedScopePatternsByNonAdmin.stream()
                .noneMatch(pattern -> pattern.matcher(scopeToken).matches());
    }

    /**
     * Assert non admin user has permissions on given path, and that those paths exists
     */
    private void assertValidRepoPathsForNonAdmin(List<String> scope) {
        List<String> repoPathsFromScope = collectRepoPathsFromScope(scope);
        repoPathsFromScope.forEach(this::assertPathInTokenExists);
        boolean hasPermissionsOnAllRepos = scope.stream()
                .filter(ArtifactoryRepoPathScopeToken::accepts)
                .allMatch(this::hasPermissionsOnRepoPath);
        if (!hasPermissionsOnAllRepos) {
            throw new AuthorizationException("Logged in user cannot create repo path token with scope: " +
                    "no permissions on paths");
        }
    }

    /**
     * Repo-path scope validation for refresh: paths must exist and the token owner must hold read permission.
     * Issued tokens store long repo paths as path.checksum scopes; those are skipped here because they were
     * validated at creation and the access_token signature prevents scope tampering (RTDEV-92030).
     */
    private void assertValidRepoPathsForNonAdminRefresh(@Nonnull List<String> scopes, @Nonnull UserInfo tokenOwner) {
        List<String> explicitRepoPathScopes = getNonChecksumRepoPathScopes(scopes);
        collectRepoPathsFromScope(explicitRepoPathScopes).forEach(this::assertPathInTokenExists);
        boolean hasPermissionsOnAllRepos = explicitRepoPathScopes.stream()
                .allMatch(scopeToken -> hasPermissionsOnRepoPathForUser(scopeToken, tokenOwner));
        if (!hasPermissionsOnAllRepos) {
            throw new AuthorizationException(
                    "Cannot refresh token with repo path scope: token owner has no permissions on paths");
        }
    }

    private void assertPathInTokenExists(String repoPathName) {
        RepoPath repoPath = RepoPathFactory.create(repoPathName);
        ItemInfo itemInfo;
        try {
            itemInfo = repositoryService.getItemInfo(repoPath);
        } catch (ItemNotFoundRuntimeException nfe) {
            log.debug("", nfe);
            throw new NotFoundException(
                    "Cannot create a token with provided scope - path doesn't exist : " + repoPath.toString());
        }
        if (itemInfo.isFolder()) {
            throw new NotFoundException(
                    "Cannot create a token with provided scope - path is a directory : " + repoPath.toString());
        }
    }

    private boolean hasPermissionsOnRepoPath(String scopeToken) {
        RepoPath repoPath = RepoPathFactory.create(ArtifactoryRepoPathScopeToken.getPath(scopeToken));
        return READ_PERMISSION.equals(ArtifactoryRepoPathScopeToken.parse(scopeToken).getPermissions()) &&
                authorizationService.canRead(repoPath);
    }

    private boolean hasPermissionsOnRepoPathForUser(String scopeToken, UserInfo user) {
        RepoPath repoPath = RepoPathFactory.create(ArtifactoryRepoPathScopeToken.getPath(scopeToken));
        return READ_PERMISSION.equals(ArtifactoryRepoPathScopeToken.parse(scopeToken).getPermissions()) &&
                authorizationService.canRead(user, repoPath);
    }

    /**
     * for non-admin users - Check user is a member of requested groups
     */
    private void assertValidGroupsForNonAdmin(List<String> scope, UserInfo userInfo) {
        Set<String> requestedGroupNames = AccessTokenAuthenticationProvider.collectGroupNamesFromScope(scope);
        Set<String> userGroups = getUserGroupsNames(userInfo);
        requestedGroupNames.removeAll(userGroups);
        // In case the token scope is left with only 'member-of-groups:*', it is valid
        if (requestedGroupNames.size() == 1 && requestedGroupNames.contains("*")) {
            return;
        }
        if (!requestedGroupNames.isEmpty()) {
            throw new AuthorizationException(
                    "The user: '" + userInfo.getUsername() + "' is not a member of the following groups: " +
                            String.join(",", requestedGroupNames));
        }
    }

    private Set<String> getUserGroupsNames(UserInfo user) {
        Set<UserGroupInfo> userGroups = user.getGroups();
        return Optional.ofNullable(userGroups)
                .map(groups -> groups.stream()
                        .map(UserGroupInfo::getGroupName)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }

    void assertValidExpiresInForNonAdmin(TokenRequest tokenRequest, String currentUsername,
            boolean skipUserCanCreateLongLivedTokenAssertion) {
        long maxExpiresInSeconds = ConstantValues.accessTokenNonAdminMaxExpiresIn.getLong(artifactoryHome);
        if (maxExpiresInSeconds != USER_TOKEN_MAX_EXPIRES_UNLIMITED) {
            long expiresIn = Optional.ofNullable(tokenRequest.getExpiresIn()).orElse(Long.MAX_VALUE);
            if (!skipUserCanCreateLongLivedTokenAssertion && (expiresIn > maxExpiresInSeconds || expiresIn <= 0)) {
                throw new AuthorizationException(
                        "The user: '" + currentUsername + "' can only create user token with " +
                                "expires in larger than 0 and smaller than " + maxExpiresInSeconds +
                                " seconds (requested: " +
                                tokenRequest.getExpiresIn() + ")");
            }
        }
    }

    @Nonnull
    @Override
    public CreatedTokenInfo refreshToken(@Nonnull TokenSpec tokenSpec, @Nonnull String tokenValue,
            @Nonnull String refreshToken) {
        JwtAccessToken accessToken = parseToken(tokenValue);
        // parseToken() decodes without verifying the signature; verify it explicitly so a forged
        // access_token cannot be used to escalate privileges (RTDEV-92030).
        // We allow the EXPIRED reason because refreshing an expired token is the purpose of this flow.
        // Any other failure — including SIGNATURE — means the token cannot be trusted and must be rejected.
        // Note: TokenVerifyResult.getReason() returns a String, not a typed enum; VerifyFailureReason
        // (access-common-core) is the library's own stable set of reason codes and is the most reliable
        // reference available from this API.
        TokenVerifyResult signatureCheck = verifyAndGetResult(accessToken);
        if (!signatureCheck.isSuccessful() &&
                !VerifyFailureReason.EXPIRED.label().equals(signatureCheck.getReason())) {
            throw new AuthorizationException(
                    "Cannot refresh token: access token signature is invalid (" + signatureCheck.getReason() + ")");
        }
        assertTokenCreatedByThisService(accessToken);
        List<String> effectiveScope =
                isNotEmpty(tokenSpec.getScope()) ? tokenSpec.getScope() : accessToken.getScope();
        assertValidScopeForNonAdmin(accessToken, effectiveScope);
        assertValidExpiryForRefreshToken(tokenSpec, accessToken);
        try {
            List<String> scope = effectiveScope;
            //false if specified, true otherwise (a refreshable token keeps to be refreshable, unless specified
            //otherwise)
            boolean refreshable = Boolean.FALSE.equals(tokenSpec.getRefreshable());
            List<String> audience =
                    isNotEmpty(tokenSpec.getAudience()) ? tokenSpec.getAudience() : accessToken.getAudience();
            TokenResponse tokenResponse = ensureAuth(() -> {
                TokenRequest tokenRequest = new TokenRequest(scope, refreshable, accessToken.getSubject(),
                        null, tokenSpec.getExpiresIn(), null, audience);
                return getAccessClient().token().refresh(refreshToken, tokenRequest);
            });
            return toCreatedTokenInfo(tokenResponse, scope);
        } catch (AccessClientHttpException e) {
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                throw new TokenNotFoundException("Invalid access token or refresh token", e);
            } else if (e.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
                throw new AuthorizationException("Refresh token operation rejected", e);
            }
            throw e;
        } catch (AccessClientException e) {
            log.error("Failed to refresh token for subject '{}': {}", accessToken.getSubject(), e.getMessage());
            log.debug("Failed to refresh token with id '{}' for subject '{}'",
                    accessToken.getTokenId(), accessToken.getSubject(), e);
            throw new AccessClientException(
                    "Failed to refresh token for subject '" + accessToken.getSubject() + "'.", e);
        }
    }

    private void assertValidExpiryForRefreshToken(@Nonnull TokenSpec tokenSpec, @Nonnull JwtAccessToken accessToken) {
        if (accessToken.getExpirationTime() == null || tokenSpec.getExpiresIn() == null) {
            return;
        }

        Long requestedExpiresIn = tokenSpec.getExpiresIn();
        long accessTokenExpiresIn =
                TimeUnit.MILLISECONDS.toSeconds(accessToken.getExpirationTime() - accessToken.getIssuedAt());

        if (requestedExpiresIn <= 0 || requestedExpiresIn > accessTokenExpiresIn) {
            throw new AuthorizationException("To refresh a token, set the 'expires_in' value to "
                    + accessTokenExpiresIn + " or lower (requested: " + requestedExpiresIn + ")");
        }
    }

    /**
     * Asserts that the given token was created by this service (artifactory instance/cluster). This method accepts both
     * access tokens and refresh tokens, to support revoke by both. In case the token is not a valid access token (e.g.
     * a refresh token) the token is not checked.
     *
     * @param tokenValue the token to check, can be either an access token or a refresh token
     */
    private void assertTokenCreatedByThisService(@Nonnull String tokenValue) {
        try {
            JwtAccessToken accessToken = parseToken(tokenValue);
            assertTokenCreatedByThisService(accessToken);
        } catch (IllegalArgumentException e) {
            log.debug("Could not parse token value, it might be a refresh token, ignoring.", e);
        }
    }

    /**
     * Asserts that the given token was created by this service (artifactory instance/cluster).
     * This is currently important for refreshing and revoking tokens because it can only be done by the same service.
     *
     * @param accessToken the access token to check
     */
    private void assertTokenCreatedByThisService(@Nonnull JwtAccessToken accessToken) {
        Issuer issuer = new Issuer(accessToken.getIssuer());
        ServiceId issuerServiceId = issuer.getServiceId();
        if (!issuerServiceId.equals(this.serviceId)) {
            throw new TokenIssuedByOtherServiceException("Provided access token with ID '" + accessToken.getTokenId() +
                    "' was issued by a different service with ID '" + issuerServiceId + "' (current service ID: '" +
                    this.serviceId + "')", this.serviceId, issuerServiceId);
        }
    }

    private CreatedTokenInfo toCreatedTokenInfo(TokenResponse tokenResponse, List<String> scopeTokens) {
        String scope = String.join(SCOPE_DELIMITER, scopeTokens);
        OptionalLong expiresInOptional = tokenResponse.getExpiresIn();
        Long expiresIn = expiresInOptional.isPresent() ? expiresInOptional.getAsLong() : null;
        return new CreatedTokenInfoImpl(tokenResponse.getTokenValue(), tokenResponse.getTokenType(),
                tokenResponse.getRefreshToken().orElse(null), scope, expiresIn,
                tokenResponse.getReferenceToken().orElse(null));
    }

    private List<String> getEffectiveScope(TokenSpec tokenSpec, boolean skipAcceptedScopeAssertion) {
        List<String> effectiveScope = Lists.newArrayList(tokenSpec.getScope());
        addUserGroupsToScopeIfNeeded(effectiveScope, tokenSpec);
        if (!skipAcceptedScopeAssertion) {
            assertAcceptedScope(effectiveScope);
        }
        return effectiveScope;
    }

    @VisibleForTesting
    void assertAcceptedScope(List<String> scope) {
        scope.stream()
                .filter(scopeToken ->
                        acceptedScopePatterns.stream().noneMatch(pattern -> pattern.matcher(scopeToken).matches() ||
                                PermissionScopeMatcher.tryParse(scopeToken).isPresent()))
                .findFirst()
                .ifPresent(scopeToken -> {
                    throw new IllegalArgumentException("Unaccepted scope: '" + scopeToken + "'");
                });
        if (scope.isEmpty() || scope.equals(singletonList(SCOPE_API))) {
            throw new IllegalArgumentException("Insufficient scope: '" + String.join(SCOPE_DELIMITER, scope) + "'");
        }
    }

    private void addUserGroupsToScopeIfNeeded(List<String> scope, TokenSpec tokenSpec) {
        if (tokenSpec instanceof UserTokenSpec) {
            UserInfo user = userGroupStore.findUser(((UserTokenSpec) tokenSpec).getUsername());
            if (user != null) {
                //Add user's assigned groups by default
                if (scope.isEmpty() || scope.equals(singletonList(SCOPE_API))) {
                    Set<UserGroupInfo> groups = user.getGroups();
                    if (groups != null && !groups.isEmpty()) {
                        List<String> groupNames = groups.stream()
                                .map(UserGroupInfo::getGroupName)
                                .collect(Collectors.toList());
                        String groupsConcat = String.join(",", groupNames);
                        if (groupsConcat.contains(" ")) {
                            groupsConcat = "\"" + groupsConcat + "\"";
                        }
                        scope.add(APPLIED_PERMISSIONS_GROUPS_PREFIX + groupsConcat);
                    }
                }
            }
        }
    }

    /**
     * collect all ArtifactoryRepoPathScopeToken repoPaths from scope
     */
    private List<String> collectRepoPathsFromScope(List<String> scope) {
        return scope.stream()
                .filter(ArtifactoryRepoPathScopeToken::accepts)
                .map(ArtifactoryRepoPathScopeToken::getPath)
                .collect(Collectors.toList());
    }

    private List<String> getEffectiveAudience(TokenSpec tokenSpec) {
        List<String> effectiveAudience = Lists.newArrayList(tokenSpec.getAudience());
        // If audience was not specified - use this service by default, otherwise use the specified audience,
        // even if it does not contain this service.
        if (effectiveAudience.isEmpty()) {
            String thisServiceIdName = serviceId.getFormattedName();
            effectiveAudience.add(thisServiceIdName);
        } else {
            // Replace "any-type" with the artifactory type (creating tokens through artifactory allows targeting
            // only artifactory.)
            effectiveAudience = effectiveAudience.stream()
                    .map(aud -> {
                        if ("*".equals(aud) || "*@*".equals(aud)) {
                            return ARTIFACTORY_SERVICE_TYPE + "@*";
                        }
                        return aud;
                    }).collect(Collectors.toList());
        }
        return effectiveAudience;
    }

    private String getIssuer() {
        return serviceId.getFormattedName() + "/" + USERS_NAME_PART + "/" + securityService.currentUsername();
    }

    @Nullable
    @Override
    public String extractSubjectUsername(@Nonnull JwtAccessToken accessToken) {
        try {
            return UserTokenSpec.extractUsername(accessToken.getSubject());
        } catch (Exception e) {
            log.debug("Failed to extract subject username from access token: {}", accessToken, e);
            return null;
        }
    }

    @Override
    public void revokeToken(@Nonnull String tokenValue) {
        assertTokenCreatedByThisService(tokenValue);
        try {
            runWithFallback(() -> getAccessClient().token().revoke(tokenValue));
        } catch (AccessClientHttpException e) {
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                throw new TokenNotFoundException("Invalid access token or refresh token", e);
            } else if (e.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
                throw new AuthorizationException("Revoke token operation rejected", e);
            }
            throw e;
        } catch (AccessClientException e) {
            String tokenId = getTokenIdFromTokenValueSafely(tokenValue, "UNKNOWN");
            log.error("Failed to revoke token with id '{}': {}", tokenId, e.getMessage());
            log.debug("Failed to revoke token with id '{}'", tokenId, e);
            throw new AccessClientException("Failed to revoke token.", e);
        }
    }

    @Override
    public void revokeTokenById(@Nonnull String tokenId) {
        try {
            boolean found = ensureAuth(() -> getAccessClient().token().revokeById(tokenId));
            if (!found) {
                throw new TokenNotFoundException("Token not found with id: " + tokenId);
            }
        } catch (AccessClientHttpException e) {
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                throw new TokenNotFoundException("Token not found with id: " + tokenId, e);
            } else if (e.getStatusCode() == HttpStatus.SC_FORBIDDEN) {
                throw new AuthorizationException("Revoke token operation rejected", e);
            } else {
                throw e;
            }
        } catch (AccessClientException e) {
            log.error("Failed to revoke token by id '{}': {}", tokenId, e.getMessage());
            log.debug("Failed to revoke token by id '{}'", tokenId, e);
            throw new AccessClientException("Failed to revoke token by id '" + tokenId + "'", e);
        }
    }

    @Nullable
    private String getTokenIdFromTokenValueSafely(@Nonnull String tokenValue, @Nullable String defaultValue) {
        try {
            return parseToken(tokenValue).getTokenId();
        } catch (IllegalArgumentException e) {
            log.debug("Failed to parse token value, returning default value '{}' instead of the token ID.",
                    defaultValue, e);
            return defaultValue;
        }
    }

    @Override
    @Nonnull
    public JwtAccessToken parseToken(@Nonnull String tokenValue) throws IllegalArgumentException {
        requireNonNull(tokenValue, "Token value is required");
        return ensureAuth(() -> getAccessClient().token().parse(tokenValue));
    }

    @Override
    public boolean verifyToken(@Nonnull JwtAccessToken accessToken) {
        TokenVerifyResult result = verifyAndGetResult(accessToken);
        if (result.isSuccessful()) {
            return true;
        } else {
            log.debug("Token with id '{}' failed verification, reason: {}", accessToken.getTokenId(),
                    result.getReason());
        }
        return false;
    }

    @Override
    public boolean verifyBackOfficeToken(@Nonnull JwtAccessToken accessToken) {
        try {
            TokenVerifyResponse response = verifyBackOfficeTokenAndGetResult(accessToken.getTokenValue());
            if (response.isSuccessful()) {
                return true;
            }
            log.debug("Token with id '{}' failed verification, reason: {}", accessToken.getTokenId(),
                    response.getReason());
        } catch (AccessClientException ex) {
            log.warn("Token with id '{}' failed client exception '{}'", accessToken.getTokenId(), ex.getMessage(), ex);
        }
        return false;
    }

    @Override
    @Nonnull
    public AccessTokenType getAccessTokenType(@Nonnull String tokenValue) throws IllegalArgumentException {
        requireNonNull(tokenValue, "Token value is required");
        return ensureAuth(() -> getAccessClient().token().getAccessTokenType(tokenValue));
    }

    @Override
    public boolean verifyTokenIfServiceIdChanged(@Nonnull JwtAccessToken accessToken) {
        boolean serviceIdChanged = false;
        rwAccessClientLock.writeLock().lock();
        try {
            String originalServiceId = serviceId.getFormattedName();
            String currentId = configsService.getConfig(ARTIFACTORY_SERVICE_ID);
            if (StringUtils.isNotBlank(currentId)) {
                log.debug("Checking if service_id changed");
                serviceIdChanged = setServiceIdAndCheckIfChanged(originalServiceId, currentId);
            }
            if (serviceIdChanged) {
                configStore = new ArtifactoryAccessClientConfigStore(serviceId);
                bootstrapAccessClient();
            }
            boolean tokenValid = verifyAndGetResult(accessToken).isSuccessful();
            if (tokenValid) {
                log.debug("Token reloaded successfully and verified.");
            }
            return tokenValid;
        } finally {
            rwAccessClientLock.writeLock().unlock();
        }
    }

    @Override
    public TokenVerifyResult verifyAndGetResult(@Nonnull JwtAccessToken accessToken) {
        requireNonNull(accessToken, "Access token is required");
        try {
            return ensureAuth(() -> getAccessClient().token().verify(accessToken.getTokenValue()));
        } catch (AccessClientException e) {
            String tokenId = accessToken.getTokenId();
            log.error("Failed to verify access token with id '{}': {}", tokenId, e.getMessage());
            log.debug("Failed to verify access token with id '{}'", tokenId, e);
            throw new AccessClientException("Failed to verify access token with id '" + tokenId + "'", e);
        }
    }

    @Override
    public TokenVerifyResult verifyAndGetResult(@Nonnull String accessToken) {
        requireNonNull(accessToken, "Access token is required");
        try {
            return ensureAuth(() -> getAccessClient().token().verify(accessToken));
        } catch (AccessClientException e) {
            throw new AccessClientException("Failed to verify access token", e);
        }
    }

    @Nonnull
    @Override
    public ServiceId getArtifactoryServiceId() {
        return serviceId;
    }

    @Override
    public boolean isTokenAppliesScope(@Nonnull JwtAccessToken accessToken, @Nonnull String requiredScope) {
        //TODO [YA] this is currently enough, but will probably need to be more sophisticated in the near future...
        return accessToken.getScope().stream().anyMatch(scope -> scope.equals(requiredScope));
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        log.info("Reloading Access Service");
    }

    @Override
    public void unregisterFromRouterIfNeeded() {
        if (artifactoryHome.getSysConfig().getBoolean(SysConfigConstants.KEY_ROUTER_ENABLED, true)) {
            ArtifactoryRouterRegistrationData artifactoryRouterRegistrationData = new ArtifactoryRouterRegistrationData(
                    getArtifactoryServiceId(),
                    artifactoryHome.getHaAwareHostId(),
                    context.getContextPath());
            RouterGrpcClient router = getAccessClient().router();
            router.unregisterService(artifactoryRouterRegistrationData.build());
        }
    }

    public void pingUsingAdminToken(String adminToken) {
        runWithFallback(() -> {
            AccessClient accessHttpClient = getAccessClient().useAuth(new AccessAuthToken(adminToken));
            accessHttpClient.ping();
        });
    }

    @VisibleForTesting
    public void updateAccessClient(AccessClient newAccessClient) {
        rwAccessClientLock.writeLock().lock();
        try {
            this.accessClient = newAccessClient;
        } finally {
            rwAccessClientLock.writeLock().unlock();
        }
        accessClientUpdateListeners.forEach(AccessClientUpdateListener::onClientUpdate);
    }

    @Override
    public void subscribeForClientChanges(AccessClientUpdateListener listener) {
        accessClientUpdateListeners.add(listener);
    }

    private void bootstrapAccessClient() {
        rwAccessClientLock.writeLock().lock();
        try {
            AccessClient oldAccessClient = getAccessClient();
            AtomicBoolean shouldCloseOldClient = new AtomicBoolean(false);

            Try.of(this::buildAccessClient)
                    .map(client -> bootstrapAccessClient(client.useAuth(null)).getAccessClient())
                    .onFailure(IllegalStateException.class, e -> traceThreadDump())
                    .onFailure(AccessClientException.class, e -> traceThreadDump())
                    .onSuccess(newAccessClient1 -> shouldCloseOldClient.set(renewClient(newAccessClient1)))
                    .andFinally(() -> Optional.of(shouldCloseOldClient)
                            .filter(AtomicBoolean::get)
                            .ifPresent(closeOldClient -> closeOldAccessClient(oldAccessClient)))
                    .get();
        } finally {
            rwAccessClientLock.writeLock().unlock();
        }
    }

    private void traceThreadDump() {
        if (log.isTraceEnabled()) {
            StringBuilder message = new StringBuilder("Access client might got stuck  ");
            ThreadDumpUtils.builder()
                    .count(3)
                    .build()
                    .dumpThreads(message);
            log.trace(message.toString());
        }
    }

    private boolean renewClient(AccessClient newAccessClient) {
        updateAccessClient(newAccessClient);
        configStore.invalidateAdminCredentialsCache();
        return true;
    }

    private AccessClientBootstrap bootstrapAccessClient(AccessClient accessClientWithoutAuth) {
        AccessClientBootstrap clientBootstrap;
        if (artifactoryHome.getSysConfig().getBoolean(SysConfigConstants.KEY_ROUTER_ENABLED, true)) {
            Supplier<RouterRegistrationData> routerRegistrationDataSupplier =
                    () -> new ArtifactoryRouterRegistrationData(
                            getArtifactoryServiceId(),
                            artifactoryHome.getHaAwareHostId(),
                            context.getContextPath())
                            .build();
            clientBootstrap = new AccessClientBootstrap(accessClientWithoutAuth,
                    () -> configStore,
                    joinKeyBootstrapper::getJoinKey,
                    routerRegistrationDataSupplier);
        } else {
            clientBootstrap = new AccessClientBootstrap(accessClientWithoutAuth,
                    () -> configStore,
                    joinKeyBootstrapper::getJoinKey);
        }
        clientBootstrap.setRejoinEnabled(true);
        return clientBootstrap;
    }

    @Override
    public void updateConfigurationModel(Map<String, Object> accessConfiguration) throws AccessClientException {
        if (!accessConfiguration.isEmpty()) {
            getAccessClient().config().updateConfigurationModel(accessConfiguration);
            logAnonAccessChangesIfAny(accessConfiguration);
        }
    }

    private void logAnonAccessChangesIfAny(Map<String, Object> accessConfiguration) {
        if (accessConfiguration.containsKey(SECURITY_MAP)) {
            Map<String, Object> securityMap = (Map<String, Object>) accessConfiguration.get(SECURITY_MAP);
            if (securityMap.containsKey(SECURITY_ANONYMOUS_ACCESS_ENABLED)) {
                log.info("Anonymous access set to: {}", securityMap.get(SECURITY_ANONYMOUS_ACCESS_ENABLED));
            }
        }
    }

    private boolean updateLdapConfiguration(List<LdapSetting> ldapSettingList,
            List<LdapGroupSetting> ldapGroupSettingList) {
        boolean ldapSettingsUpdated = false;
        if (!ldapSettingList.isEmpty()) {
            upsertLdapSettings(ldapSettingList);
            ldapSettingsUpdated = true;
        }

        if (!ldapGroupSettingList.isEmpty()) {
            upsertLdapGroupSettings(ldapGroupSettingList);
            ldapSettingsUpdated = true;
        }
        return ldapSettingsUpdated;
    }

    private boolean updateOAuthSettings(List<OAuthSetting> oAuthSettingsList) {
        boolean oauthSettingsUpdated = false;
        if (!oAuthSettingsList.isEmpty()) {
            upsertOAuthSettings(oAuthSettingsList);
            oauthSettingsUpdated = true;
        }
        return oauthSettingsUpdated;
    }

    @Override
    public int moveYamlConfigResourcesToAccess(Map<String, Object> yamlConfigMap) {
        return yamlAccessConfigMigrationService.migrate(yamlConfigMap);
    }

    private void updateAccessAdoptionConfig() {
        boolean isFeatureAdoptionEnabled = FeatureAdoptionUtils.isFeatureAdoptionEnabled();
        getPlatformConfigClient().updateConfig(ACCESS_ADOPTION_CONFIG,
                oldConfig -> Map.of(ACCESS_ADOPTION_CONFIG_DISABLED, !isFeatureAdoptionEnabled),
                Map.class, false);
    }

    @Override
    public UserLockPolicyConfig getUserLockPolicyConfig() {
        return ensureAuth(() -> getAccessClient().config().getUserLockPolicy());
    }

    @Override
    public boolean isAnonymousAccessEnabled() {
        return ensureAuth(() -> getAccessClient().config().getAnonymousAccessEnabled().isEnabled());
    }

    @Override
    public void destroy() {
        IOUtils.closeQuietly(getAccessClient(), null);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {

    }

    @Override
    public void ping() {
        try {
            runWithFallback(() -> getAccessClient().useAuth(null).ping());
        } catch (AccessClientException e) {
            throw new AccessClientException("Access service is unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public void readiness() {
        try {
            runWithFallback(() -> getAccessClient().useAuth(null).readiness());
        } catch (AccessClientException e) {
            throw new AccessClientException("Access service is unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public void exportTo(ExportSettings settings) {
        try {
            exportServiceId(settings);
        } catch (IOException e) {
            log.debug("Error during serviceId backup", e);
            settings.getStatusHolder().error("Error during serviceId backup", e, log);
        }

        try {
            log.info("Triggering export in access server...");
            getAccessClient().system().exportAccessServer();
            Path exportEtcFolder = settings.getBaseDir().toPath().resolve(ETC_FOLDER);
            File accessServerBackupDir = artifactoryHome.getSysLayout().getServiceBackup(ACCESS_SUB_DIR);
            try (Stream<Path> backupListStream = Files.list(accessServerBackupDir.toPath())) {
                backupListStream
                        .filter(p -> !p.toFile().isDirectory())
                        .filter(p -> p.toFile().getName().matches("access\\.backup\\..+\\.json"))
                        .max(Comparator.comparing(p -> p.toFile().lastModified()))
                        .ifPresent(path -> copyFile(path, exportEtcFolder));
            }
        } catch (Exception e) {
            log.debug("Error during access server backup", e);
            settings.getStatusHolder().error("Error during access server backup", e, log);
        }
    }

    private void exportServiceId(ExportSettings settings) throws IOException {
        log.info("Exporting serviceId to file");
        File targetBackupEtcDir = new File(settings.getBaseDir(), ETC_FOLDER);
        File serviceIdFile = new File(targetBackupEtcDir, getServiceIdFilePathUnderEtc());
        FileUtils.forceMkdir(serviceIdFile.getParentFile());
        Files.write(serviceIdFile.toPath(), serviceId.toString().getBytes());
    }

    private Path copyFile(Path srcFile, Path targetFolder) {
        Path trgFile = null;
        try {
            Files.createDirectories(targetFolder);
            trgFile = targetFolder.resolve(ACCESS_BOOTSTRAP_JSON);
            Path destination_path = Files.copy(srcFile, trgFile, COPY_ATTRIBUTES, REPLACE_EXISTING);
            // Set permissions to 644 in order to be able to read the file on import (Access sets permission to 700)
            setPermissionsOnSecurityFile(destination_path, PERMISSIONS_MODE_644);
            return destination_path;
        } catch (IOException e) {
            String error = "Unable to copy the file from '" + srcFile + "' to '" + trgFile + "'";
            log.debug(error, e);
            throw new RuntimeException(error, e);
        }
    }

    @Override
    public void importSecurityEntities(SecurityInfo securityInfo, boolean override) {
        try {
            runWithFallback(() ->
                    getAccessClient().imports().importSecurityEntities(buildImportRequest(securityInfo), override));
        } finally {
            configStore.invalidateAdminCredentialsCache();
        }
    }

    private ImportEntitiesRequest buildImportRequest(SecurityInfo securityInfo) {
        ImportEntitiesRequest.Builder entities = ImportEntitiesRequest.builder();
        List<GroupInfo> groups = securityInfo.getGroups();

        if (groups != null) {
            for (GroupInfo group : groups) {
                entities.addGroup(GroupMapper.toFullAccessGroup(group));
            }
        }
        List<UserInfo> users = securityInfo.getUsers();
        if (users != null) {
            for (UserInfo user : users) {
                entities.addUser(UserMapper.toFullAccessUser(user));
            }
        }
        String serviceId = getArtifactoryServiceId().getFormattedName();
        List<RepoAcl> repoAcls = securityInfo.getRepoAcls();
        if (repoAcls != null) {
            for (RepoAcl acl : repoAcls) {
                entities.addPermission(toFullAccessPermission(acl, serviceId, ResourceType.REPO));
            }
        }
        List<BuildAcl> buildAcls = securityInfo.getBuildAcls();
        if (buildAcls != null) {
            for (BuildAcl acl : buildAcls) {
                entities.addPermission(toFullAccessPermission(acl, serviceId, ResourceType.BUILD));
            }
        }
        List<ReleaseBundleAcl> releaseBundleAcls = securityInfo.getReleaseBundleAcls();
        if (releaseBundleAcls != null) {
            for (ReleaseBundleAcl acl : releaseBundleAcls) {
                entities.addPermission(toFullAccessPermission(acl, serviceId, ResourceType.RELEASE_BUNDLE));
            }
        }
        return entities.build();
    }

    @Override
    public void importFrom(ImportSettings settings) {
        copyServiceIdFile(settings);
        initServiceIdAndReturnOldValue();
        configStore = new ArtifactoryAccessClientConfigStore(serviceId);
        importAccessServer(settings);
    }

    private void copyServiceIdFile(ImportSettings settings) {
        File serviceIdFile =
                Paths.get(settings.getBaseDir().getAbsolutePath(), ETC_FOLDER, SECURITY_DIR, ACCESS_SUB_DIR,
                        ACCESS_KEYS_DIR_NAME, SERVICE_ID_FILE_NAME).toFile();
        Path targetPath = getAccessServiceIdFile().toPath();
        try {
            if (serviceIdFile.exists()) {
                FileUtils.forceMkdir(targetPath.toFile().getParentFile());
                Files.copy(serviceIdFile.toPath(), targetPath, COPY_ATTRIBUTES, REPLACE_EXISTING);
            } else {
                log.info("serviceId file doesn't exist");
            }
        } catch (IOException e) {
            log.debug("Failed to import access serviceId file: {}", serviceIdFile.getAbsolutePath(), e);
            settings.getStatusHolder()
                    .error("Failed to import access serviceId file: " + serviceIdFile.getAbsolutePath()
                            + " Please check file permissions", e, log);
        }
    }

    @Override
    public void afterImport(ImportSettings settings) {
        // the client is responsible for re-joining the router
        accessServerName = getAccessServerName();
        ensureAuth(this::sendLicenseToAccess);
        // We call the registration here as well since the default registration happens on afterContextCreated,
        // which will not happen after import.
        registerInFederation(null, true);
    }

    private void importAccessServer(ImportSettings settings) {
        log.info("Triggering import in access server...");
        File bootstrapFile =
                Paths.get(settings.getBaseDir().getAbsolutePath(), ETC_FOLDER, ACCESS_BOOTSTRAP_JSON).toFile();
        if (bootstrapFile.exists()) {
            boolean failed = false;
            try {
                if (tryPerformImportToSaaSWithPayload(settings, bootstrapFile)) {
                    return;
                }

                Path targetPath =
                        Paths.get(artifactoryHome.getSysLayout().getServiceEtc(ACCESS_SUB_DIR).getAbsolutePath(),
                                ACCESS_BOOTSTRAP_JSON);
                FileUtils.forceMkdir(targetPath.toFile().getParentFile());
                Files.copy(bootstrapFile.toPath(), targetPath, COPY_ATTRIBUTES, REPLACE_EXISTING);
            } catch (IOException e) {
                failed = true;
                log.debug("Failed to import access bootstrap file: {}", bootstrapFile.getAbsolutePath(), e);
                settings.getStatusHolder()
                        .error("Failed to import access bootstrap file: " + bootstrapFile.getAbsolutePath()
                                + " Please check file permissions", e, log);
            }
            if (!failed) {
                performImport(settings);
            }
        }
    }

    @VisibleForTesting
    void performImport(ImportSettings settings) {
        try {
            if (settings.getImportKind().equals(ImportKind.STANDARD)) {
                getAccessClient().system().importAccessServer();
            } else {
                getAccessClient().system().importAccessServerSaasMigration();
            }
            publisher.publishEvent(new InvalidateCacheEvent(this, CacheType.ACL));
        } catch (Exception e) {
            log.debug("Error during access server restore", e);
            settings.getStatusHolder().error("Error during access server restore", e, log);
        }
    }

    boolean tryPerformImportToSaaSWithPayload(ImportSettings settings, File toImport) throws IOException {
        if (settings.getImportKind().equals(ImportKind.STANDARD)) {
            return false;
        }
        byte[] fileAsBytes = FileUtils.readFileToByteArray(toImport);

        log.debug("Trying to import to access by sending import file as payload");
        try {
            getAccessClient().system().importAccessServerSaasMigration(fileAsBytes);
        } catch (AccessClientHttpException e) {
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                log.debug("Import to access using payload endpoint is not available. Will use import from disk", e);
                return false;
            }
            throw e;
        } catch (AccessClientException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketException && cause.getMessage().equals("Broken pipe")) {
                log.debug(
                        "Import to access using payload endpoint got broken pipe, probably since it is not available." +
                                " Will use import from disk",
                        e);
                return false;
            }
            throw e;
        }
        return true;
    }

    @Override
    public void revokeAllForUserAndScope(String username, String scope) {
        getAccessClient().token().revokeAllForUserAndScope(username, scope);
    }

    @Override
    public boolean isAdminUsingOldDefaultPassword() {
        long timeDifference = ConstantValues.accessAdminDefaultPasswordCheckPeriod.getLong();
        long now = System.currentTimeMillis();
        if (BooleanUtils.isNotFalse(hasAdminSecurityIssue)
                && lastAdminSecurityIssueCheck < now - timeDifference) {
            LoginRequest loginRequest = new LoginRequest()
                    .username(ADMIN_DEFAULT_USERNAME)
                    .password(DEFAULT_ADMIN_PASSWORD);
            lastAdminSecurityIssueCheck = now;
            try {
                getAccessClient().cachedAuth().authenticate(loginRequest);
                hasAdminSecurityIssue = true;
            } catch (AccessClientException e) {
                hasAdminSecurityIssue = false;
            } catch (Exception e) {
                log.error("Caught exception while authenticating against Access using admin credentials: {}",
                        e.getMessage(), e);
                return true;
            }
        }
        return hasAdminSecurityIssue;
    }

    @Override
    public void invalidateAdminUsingOldDefaultPasswordCache() {
        lastAdminSecurityIssueCheck = 0;
        log.debug("Admin using old default password cache was invalidated");
    }

    @Override
    public void onLicenseLoaded() {
        if (!addonsManager.isPartnerLicense()) {
            sendLicenseToAccess();
        }
    }

    @Override
    public void onContextCreated() {
        contextStateDependantActionRunner.onContextCreated();
        // Reason for the registration being here and not after init of the Access client is that we don't want to
        // screw up the startup and it's converters in case the registration fails.
        registerInFederation(null, true);
    }

    @Override
    public void activateUserWithOAuthProvider(@Nonnull String activationToken, @Nonnull String oauthProviderKey,
            @Nonnull String oauthProviderUserId) {
        try {
            Pair<String, String> searchableProp = getSearchableProp(oauthProviderKey, oauthProviderUserId);
            getAccessClient().cachedUsers().register(UserRegistrationRequestImpl.builder()
                    .isSSO(true)
                    .registrationToken(activationToken)
                    .customData(Map.of(
                            oauthProviderKey, new CustomDatumRequestImpl(oauthProviderUserId, true),
                            searchableProp.getKey(), new CustomDatumRequestImpl(searchableProp.getValue())))
                    .build());
        } catch (AccessClientException e) {
            throw new IllegalStateException("Failed activating user with invitation token", e);
        }
    }

    @Override
    public boolean isMtlsEnabled() {
        if (!addonsManager.isEntitled(EntitlementType.MTLS)) {
            log.debug("MTls is not entitled");
            return false;
        }
        MTlsConfigurationModel mTlsConfigurationModel =
                ensureAuth(() -> getAccessClient().config().getMTlsConfiguration());
        if (mTlsConfigurationModel == null || !mTlsConfigurationModel.isEnabled()) {
            log.debug("MTls is disabled");
            return false;
        }
        return true;
    }

    @Override
    public String extractMtlsUsername(@Nonnull HttpServletRequest httpServletRequest) {
        if (addonsManager.isEntitled(EntitlementType.MTLS)) {
            AuthenticationResult<String> authenticationResult =
                    ensureAuth(() -> getAccessClient().cachedAuth().mtls()
                            .extractUsername(new ServletHttpRequestImpl(httpServletRequest)));
            if (authenticationResult.isSuccessful()) {
                return authenticationResult.getPrincipal();
            } else {
                log.debug(authenticationResult.getReason());
            }
        }
        return null;
    }

    @Override
    public MTlsConfigurationModel getMtlsConfigurationModel() {
        return ensureAuth(() -> getAccessClient().config().getMTlsConfiguration());
    }

    @Override
    public boolean isBasicAuthEnabled() {
        return ensureAuth(() -> getAccessClient().config().getBasicAuthenticationEnabled()).isEnabled();
    }

    @Override
    public boolean isEncryptionRequired() {
        return getEncryptionPolicy() == EncryptionPolicy.REQUIRED;
    }

    @Override
    public boolean isEncryptionEnabled() {
        EncryptionPolicy encryptionPolicy = getEncryptionPolicy();
        return encryptionPolicy == EncryptionPolicy.REQUIRED || encryptionPolicy == EncryptionPolicy.SUPPORTED;
    }

    private boolean updateDefaultProxy(ProxyDescriptor proxySettings) {
        if (proxySettings == null) {
            getAccessAuthSettingsClient().upsertProxySetting(ProxySetting.builder().build());
            return false;
        }
        getAccessAuthSettingsClient().upsertProxySetting(toAccessProxy(proxySettings));
        return true;
    }

    private ProxySetting toAccessProxy(ProxyDescriptor proxyDescriptor) {
        return ProxySetting.builder()
                .key(proxyDescriptor.getKey())
                .host(proxyDescriptor.getHost())
                .port(proxyDescriptor.getPort())
                .username(proxyDescriptor.getUsername())
                .password(proxyDescriptor.getPassword())
                .ntHost(proxyDescriptor.getNtHost())
                .domain(proxyDescriptor.getDomain())
                .platformDefault(proxyDescriptor.isPlatformDefault())
                .redirectedToHosts(proxyDescriptor.getRedirectedToHosts())
                .services(proxyDescriptor.getServices())
                .build();
    }

    /**
     * Mirror the Artifactory descriptor's full <proxies> list into Access under the
     * platform-config key {@value org.jfrog.access.client.proxy.GrpcProxyClientImpl#PROXY_SETTINGS_ALL_KEY}.
     * <p>
     * Artifactory remains the source of truth for proxy CRUD and runtime proxy consumption; this
     * sync runs from {@link #updateConfigurationToAccess()} on every descriptor save (parallel to
     * the existing default-proxy push to {@code crowd.proxy.setting}). The list passed in is
     * drained from {@link AccessConfigHolder#drainAllProxies()}: {@code null} means no descriptor
     * save staged a value in this cycle and we skip; an empty list means the descriptor has no
     * proxies and Access is overwritten with an empty list.
     * <p>
     * Failures are logged and swallowed so the local descriptor write (already committed) is
     * never blocked by Access.
     */
    private boolean updateAllProxies(List<ProxyDescriptor> proxies) {
        if (proxies == null) {
            return false;
        }
        List<ProxySetting> payload = proxies.stream()
                .filter(p -> p != null && p.getKey() != null && !p.getKey().isBlank())
                .map(this::toAccessProxy)
                .toList();
        try {
            ensureAuth(() -> {
                getPlatformConfigClient().setConfig(PROXY_SETTINGS_ALL_KEY, payload, false);
                return null;
            });
            return true;
        } catch (Exception e) {
            log.warn("Failed to mirror proxies to Access under {}; Artifactory descriptor remains authoritative",
                    PROXY_SETTINGS_ALL_KEY, e);
            return false;
        }
    }

    @Override
    public EncryptionPolicy getEncryptionPolicy() {
        String encryptionPolicy =
                ensureAuth(() -> getAccessClient().config().getPasswordEncryptionPolicy().getPasswordEncryption());
        return EncryptionPolicy.valueOf(encryptionPolicy.toUpperCase(Locale.ROOT));
    }

    @Override
    public boolean isRealmAuthenticationEnabled(Realm realm) {
        return ensureAuth(
                () -> getAccessClient().config().getEnabledRealms().getEnabledRealms().contains(realm.getName()));
    }

    @Override
    public boolean isAnyExternalAuthEnabled() {
        return ensureAuth(() -> getAccessAuthSettingsClient().isAnyExternalProviderEnabled());
    }

    @Override
    public boolean shouldDelegateTokenOrApiKeyAuthenticationToAccess() {
        return isRealmAuthenticationEnabled(Realm.INTERNAL) &&
                (!isLdapEnabled() ||
                        isRealmAuthenticationEnabled(Realm.LDAP) ||
                        !ConstantValues.ldapApplyHeadlessRealmValidation.getBoolean());
    }

    void runAfterContextCreated(Runnable action) {
        contextStateDependantActionRunner.runAfterContextCreated(action);
    }

    private void initAccessAdminDefaultPasswordCheck() {
        if (isAdminUsingOldDefaultPassword()) {
            log.warn(
                    "Your Artifactory server is configured with the default admin credentials." +
                            " It is highly recommended to change the default admin password." +
                            " You can change the password from the User Management page.");
        }
    }

    private String getAccessServerName() {
        return ensureAuth(() -> getAccessClient().system().getAccessServerName());
    }

    private Void sendLicenseToAccess() {
        if (StringUtils.isBlank(accessServerName)) {
            accessServerName = getAccessServerName();
        }
        if (addonsManager.isLicenseInstalled()) {
            String licenseKey = addonsManager.getLicenseKey() == null ? "" : addonsManager.getLicenseKey();
            List<Map<String, String>> licenses =
                    List.of(Map.of("server-name", accessServerName, "key", licenseKey));
            updateConfigurationModel(Map.of("licenses", licenses));
            licenseClient.add(accessServerName, licenseKey);
            setSubscriptionTypeOnAccess();
        }
        return null;
    }

    @Override
    public void setSubscriptionTypeOnAccess() {
        if (AolUtils.isAol()) {
            SubscriptionType subscriptionType =
                    ContextHelper.get().beanForType(SubscriptionsService.class).getSubscriptionType();
            if (subscriptionType == null) {
                log.debug("Got NULL subscription type; ignoring, not informing access");
                return;
            }
            licenseClient.setSubscriptionType(subscriptionType);
        }
    }

    @Override
    public void upsertCrowdSettings(CrowdSetting crowdSetting) {
        getAccessAuthSettingsClient().upsertCrowdSetting(crowdSetting);
    }

    @Override
    public Optional<CrowdSetting> getCrowdSettings() {
        return ensureAuth(() -> getAccessAuthSettingsClient().getCrowdSetting());
    }

    @Override
    public Optional<HttpSsoSetting> getHttpSsoSettings() {
        return ensureAuth(() -> getAccessAuthSettingsClient().getHttpSsoSetting());
    }

    @Override
    public void upsertHttpSsoSettings(HttpSsoSetting httpSsoSetting) {
        getAccessAuthSettingsClient().upsertHttpSsoSetting(httpSsoSetting);
    }

    @Override
    public boolean isLdapEnabled() {
        return ensureAuth(
                () -> getAccessAuthSettingsClient()
                        .getAllLdapSettings()).stream().anyMatch(org.jfrog.access.client.model.LdapSetting::isEnabled);
    }

    @Override
    public boolean isLdapSettingsExist(String key) {
        Optional<LdapSetting> ldapSetting = Optional.of(ensureAuth(
                () -> getAccessAuthSettingsClient()
                        .getAllLdapSettings()).stream()
                .map(LdapArtifactoryAndAccessConvertor::convertLdapAccessSettingToArtiFactoryLdapSetting)
                .filter(setting -> setting.getKey().equals(key)).findFirst()).orElse(null);
        return ldapSetting.isPresent();
    }

    @Override
    public void upsertLdapSettings(List<LdapSetting> ldapSettings) {
        List<org.jfrog.access.client.model.LdapSetting> convertedLdapSetting =
                ldapSettings.stream()
                        .map(LdapArtifactoryAndAccessConvertor::convertLdapArtifactorySettingToAccessLdapSetting)
                        .toList();
        getAccessAuthSettingsClient()
                .upsertLdapSetting(convertedLdapSetting);
    }

    @Override
    public void upsertLdapSetting(LdapSetting ldapSetting) {
        getAccessAuthSettingsClient().upsertLdapSetting(convertLdapArtifactorySettingToAccessLdapSetting(ldapSetting));
    }

    @Override
    public List<LdapSetting> getLdapSettings() {
        List<org.jfrog.access.client.model.LdapSetting> allLdapSetting = ensureAuth(
                () -> getAccessAuthSettingsClient()
                        .getAllLdapSettings());
        return allLdapSetting.stream()
                .map(LdapArtifactoryAndAccessConvertor::convertLdapAccessSettingToArtiFactoryLdapSetting).toList();
    }

    @Override
    public LdapSetting removeLdapSetting(String ldapKey) {
        getAccessAuthSettingsClient().deleteLdapSetting(ldapKey);
        removeLdapGroupsByEnabledLdapKey(ldapKey);
        return null;
    }

    @Override
    public LdapSetting getLdapSettings(String ldapKey) {
        LdapSetting result;
        Optional<LdapSetting> ldapSetting = Optional.of(ensureAuth(
                () -> getAccessAuthSettingsClient()
                        .getAllLdapSettings()).stream()
                .map(LdapArtifactoryAndAccessConvertor::convertLdapAccessSettingToArtiFactoryLdapSetting)
                .filter(setting -> setting.getKey().equals(ldapKey)).findFirst()).orElse(null);
        if (ldapSetting.isEmpty()) {
            result = new LdapSetting();
            result.setSearch(new SearchPattern());
        } else {
            result = ldapSetting.get();
        }
        return result;
    }

    @Override
    public List<LdapSetting> getEnabledLdapSettings() {
        List<org.jfrog.access.client.model.LdapSetting> allLdapSetting = ensureAuth(
                () -> getAccessAuthSettingsClient()
                        .getAllLdapSettings());

        return allLdapSetting.stream()
                .map(LdapArtifactoryAndAccessConvertor::convertLdapAccessSettingToArtiFactoryLdapSetting)
                .filter(LdapSetting::isEnabled)
                .toList();
    }

    @Override
    public List<LdapGroupSetting> getLdapGroupSettings() {
        List<org.jfrog.access.client.model.LdapGroupSetting> allGroupSetting = ensureAuth(
                () -> getAccessAuthSettingsClient()
                        .getAllLdapGroupSettings());

        return allGroupSetting.stream()
                .map(LdapArtifactoryAndAccessConvertor::convertAccessLdapGroupSettingToArtifactoryLdapGroupSetting)
                .toList();
    }

    @Override
    public LdapGroupSetting getLdapGroupSettings(String name) {
        LdapGroupSetting result;
        Optional<LdapGroupSetting> group =
                getLdapGroupSettings().stream().filter(groupSetting -> groupSetting.getName().equals(name)).findAny();
        if (group.isEmpty()) {
            result = new LdapGroupSetting();
        } else {
            result = group.get();
        }
        return result;
    }

    @Override
    public void upsertLdapGroupSetting(LdapGroupSetting ldapGroupSetting) {
        getAccessAuthSettingsClient()
                .upsertLdapGroupSetting(convertArtifactoryLdapGroupSettingTOAccessGroupSetting(ldapGroupSetting));
    }

    @Override
    public void upsertLdapGroupSettings(List<LdapGroupSetting> ldapGroupSettings) {
        List<org.jfrog.access.client.model.LdapGroupSetting> groupSetting = ldapGroupSettings.stream()
                .map(LdapArtifactoryAndAccessConvertor::convertArtifactoryLdapGroupSettingTOAccessGroupSetting)
                .toList();
        getAccessAuthSettingsClient()
                .upsertLdapGroupSettings(groupSetting);
    }

    @Override
    public LdapGroupSetting removeLdapGroupOnly(String ldapGroupName) {
        LdapGroupSetting groupSetting = getLdapGroupSettings(ldapGroupName);
        getAccessAuthSettingsClient().deleteLdapGroupSetting(ldapGroupName);
        return groupSetting;
    }

    @Override
    public void removeLdapGroupsByEnabledLdapKey(String ldapSettingKey) {
        List<LdapGroupSetting> ldapGroupSettings = getLdapGroupSettings();
        for (LdapGroupSetting ldapGroupSetting : ldapGroupSettings) {
            if (ldapGroupSetting.getEnabledLdap().equals(ldapSettingKey)) {
                removeLdapGroupOnly(ldapGroupSetting.getName());
            }
        }
    }

    @Override
    public void subscribeToAuthSettingChanges(Runnable run, Realm realm) {
        if (realm.equals(Realm.CROWD)) {
            getAccessAuthSettingsClient().subscribeToCrowdChanges(run);
        } else if (realm.equals(Realm.HTTPSSO)) {
            getAccessAuthSettingsClient().subscribeToHttpSsoChanges(run);
        } else if (realm.equals(Realm.LDAP)) {
            getAccessAuthSettingsClient().subscribeToLdapChanges(run);
        } else if (realm.equals(Realm.SAML)) {
            getAccessAuthSettingsClient().subscribeToSamlChanges(run);
        }
    }

    @Override
    public boolean isSamlEnabled() {
        return isSamlEnabled(false);
    }

    @Override
    public boolean isSamlEnabled(boolean checkGlobalSettings) {
        return ensureAuth(
                () -> getAccessAuthSettingsClient()
                        .getSamlSetting(checkGlobalSettings).map(SamlSetting::isEnableIntegration).orElse(false));
    }

    @Nonnull
    @Override
    public SamlSetting getSamlSettings() {
        return ensureAuth(
                () -> getAccessAuthSettingsClient()
                        .getSamlSetting().orElse(SAML_DEFAULT_SETTINGS));
    }

    @Override
    public void upsertSamlSetting(@Nonnull SamlSetting samlSetting) {
        ensureAuth(() -> {
            getAccessAuthSettingsClient().upsertSamlSetting(samlSetting);
            return null;
        });
    }

    @Override
    public boolean isOAuthEnabled() {
        boolean isOAuthEnabled = ensureAuth(
                () -> getAccessAuthSettingsClient().getOAuthGeneralSettings().map(OAuthGeneralSettings::isEnable)
                        .orElse(false));
        boolean isOAuthSettingsEnabled = ensureAuth(
                () -> getAccessAuthSettingsClient().getAllOAuthSettings().stream().anyMatch(OAuthSetting::isEnabled));
        return isOAuthEnabled && isOAuthSettingsEnabled;
    }

    @Override
    public OAuthGeneralSettings getOAuthGeneralSettings() {
        return ensureAuth(
                () -> getAccessAuthSettingsClient()
                        .getOAuthGeneralSettings().orElse(OAUTH_DEFAULT_SETTINGS));
    }

    @Override
    public List<OAuthSetting> getAllOAuthSettings() {
        return ensureAuth(() -> getAccessAuthSettingsClient().getAllOAuthSettings());
    }

    @Override
    public Optional<OAuthSetting> getOAuthSetting(String name) {
        return ensureAuth(() -> getAccessAuthSettingsClient().getOAuthSetting(name));
    }

    @Override
    public void upsertOAuthGeneralSetting(@Nonnull OAuthGeneralSettings oAuthGeneralSettings) {
        ensureAuth(() -> {
            getAccessAuthSettingsClient().upsertOAuthGeneralSettings(oAuthGeneralSettings);
            return null;
        });
    }

    public void upsertOAuthSettings(@Nonnull List<OAuthSetting> oAuthSettingsList) {
        ensureAuth(() -> {
            getAccessAuthSettingsClient().upsertOAuthSettings(oAuthSettingsList);
            return null;
        });
    }

    public void removeOAuthSetting(@Nonnull String name) {
        ensureAuth(() -> {
            getAccessAuthSettingsClient().deleteOAuthSettings(name);
            return null;
        });
    }

    @Override
    public List<LdapGroupSetting> getEnabledLdapGroupSettings() {
        return getLdapGroupSettings().stream().filter(LdapGroupSetting::isEnabled).toList();
    }


    private TokenVerifyResponse verifyBackOfficeTokenAndGetResult(@Nonnull String accessToken) {
        requireNonNull(accessToken, "Access token is required");
        try {
            return ensureAuth(() -> getAccessClient().token()
                    .verifyBackOfficeOnServer(accessToken, serviceId.getFormattedName()));
        } catch (AccessClientException e) {
            throw new AccessClientException("Failed to verify access token", e);
        }
    }

    /**
     * Register/Unregister Artifactory's ServiceId in Access Federation.
     * In case *registration* fails we log an error, however if the license is Edge or Enterprise+ we stop the startup.
     * In case of *un-register* fails we don't check the license type, only log an error.
     *
     * @param register TRUE for register, FALSE for un-register
     */
    private void registerInFederation(@Nullable String originalServiceId, boolean register) {
        if (!register && originalServiceId == null) {
            log.debug("No original serviceId detected, no need to unregister in Access Federation");
            return;
        }
        String action = register ? "register" : "unregister";
        String serviceId = register ? getArtifactoryServiceId().getFormattedName() : originalServiceId;
        try {
            if (register) {
                runWithFallback(() -> getAccessClient().registry().registerServiceId(getArtifactoryServiceId()));
            } else {
                runWithFallback(() -> getAccessClient().registry().unregisterServiceId(createServiceId(serviceId)));
            }
            log.info("Successful {} of Artifactory serviceId {} in Access Federation", action, serviceId);
        } catch (AccessClientException | IllegalArgumentException e) {
            log.error("Failed to {} Artifactory serviceId {}: {}", action, serviceId, e.getMessage());
            log.debug("Failed to {} Artifactory serviceId", action, e);
            // For registration if the license is edge or e+ we stop the instance
            if (register && addonsManager.isEntitled(EntitlementType.ACCESS_FEDERATION)) {
                throw new IllegalStateException("Can't start Artifactory without registering in Access Federation", e);
            }
        }
    }

    private void pause() {
        try {
            log.debug("Pinging access server did not succeed, waiting for 2000ms before retrying...");
            sleep(2000);
        } catch (InterruptedException e) {
            log.warn("Waiting for access server got interrupted. Continue.", e);
            throw new RuntimeException("Waiting for access server got interrupted.", e);
        }
    }

    private static class ContextStateDependantActionRunner implements ContextCreationListener {

        private final List<Runnable> onContextCreatedActions = Lists.newArrayList();
        private boolean contextCreated = false;

        @Override
        public void onContextCreated() {
            contextCreated = true;
            onContextCreatedActions.forEach(Runnable::run);
            onContextCreatedActions.clear();
        }

        void runAfterContextCreated(Runnable action) {
            if (contextCreated) {
                action.run();
            } else {
                onContextCreatedActions.add(action);
            }
        }
    }

    @Override
    public void upsertSamlEncryptedAssertion(SamlEncryptedAssertion samlEncryptedAssertion) {
        getAccessAuthSettingsClient().upsertSamlEncryptedAssertion(samlEncryptedAssertion);
    }

    @Override
    public void updateConfigurationToAccess() {
        AccessConfigHolder accessConfigHolder = AccessConfigHolder.getInstance();
        List<String> updatedConfigTypes = Lists.newArrayList();
        if (!accessConfigHolder.isEmpty()) {
            updateConfigurationModel(accessConfigHolder.drainConfig());
            updatedConfigTypes.add("Descriptor");
        }

        if (!accessConfigHolder.isNullLdapConfig()) {
            ensureAuth(() -> {
                getAccessAuthSettingsClient().updateLdapConfig(accessConfigHolder.drainLdapConfig());
                return null;
            });
            updatedConfigTypes.add("LdapConfig");
        }
        boolean ldapSettingsUpdated = updateLdapConfiguration(accessConfigHolder.drainLdapSetting(),
                accessConfigHolder.drainLdapGroupSetting());
        if (ldapSettingsUpdated) {
            updatedConfigTypes.add("LdapSettings");
        }

        if (!accessConfigHolder.isNullHttpSsoSetting()) {
            ensureAuth(() -> {
                getAccessAuthSettingsClient().upsertHttpSsoSetting(accessConfigHolder.drainHttpSsoSetting());
                return null;
            });
            updatedConfigTypes.add("HttpSsoSetting");
        }

        if (!accessConfigHolder.isNullCrowdSetting()) {
            ensureAuth(() -> {
                getAccessAuthSettingsClient().upsertCrowdSetting(accessConfigHolder.drainCrowdSetting());
                return null;
            });
            updatedConfigTypes.add("CrowdSetting");
        }

        if (!accessConfigHolder.isNullSamlSetting()) {
            upsertSamlSetting(accessConfigHolder.drainSamlSetting());
            updatedConfigTypes.add("SamlSetting");
        }

        if (!accessConfigHolder.isNullSamlEncryption()) {
            try {
                upsertSamlEncryptedAssertion(accessConfigHolder.drainSamlEncryptedAssertion());
                updatedConfigTypes.add("SamlEncryption");
            } catch (Exception e) {
                log.debug("failed to read Saml Encryption files");
            }
        }

        boolean proxySettingsUpdated = updateDefaultProxy(accessConfigHolder.drainProxySettings());
        if (proxySettingsUpdated) {
            updatedConfigTypes.add("ProxySetting ");
        }

        boolean allProxiesUpdated = updateAllProxies(accessConfigHolder.drainAllProxies());
        if (allProxiesUpdated) {
            updatedConfigTypes.add("AllProxies");
        }

        if (!accessConfigHolder.isNullOAuthGeneralSetting()) {
            upsertOAuthGeneralSetting(accessConfigHolder.drainOAuthGeneralSettings());
            updatedConfigTypes.add("OAuthGeneralSetting");
        }

        boolean isOAuthSettingUpdated = updateOAuthSettings(accessConfigHolder.drainOAuthSetting());
        if (isOAuthSettingUpdated) {
            updatedConfigTypes.add("OAuthSetting");
        }

        if (!updatedConfigTypes.isEmpty()) {
            log.debug("Updated Access config: {}", Arrays.toString(updatedConfigTypes.toArray()));
        }
    }
}
