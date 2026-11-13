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

package org.artifactory.security;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.oauth.OAuthHandler;
import org.artifactory.addon.sso.CentralOAuthSSOAddon;
import org.artifactory.addon.sso.HttpSsoAddon;
import org.artifactory.addon.sso.crowd.CrowdAddon;
import org.artifactory.addon.sso.saml.SamlSsoAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.CentralOAuthSettings;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.mail.MailService;
import org.artifactory.api.repo.RepoActionType;
import org.artifactory.api.security.AuthorizationException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.PasswordEncryptionFailureException;
import org.artifactory.api.security.PasswordExpiryUser;
import org.artifactory.api.security.ResetPasswordException;
import org.artifactory.api.security.SecurityListener;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.api.security.ldap.LdapService;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.bundle.BundleNameAndRepo;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.config.ConfigurationException;
import org.artifactory.config.InternalCentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.security.PasswordExpirationPolicy;
import org.artifactory.descriptor.security.PasswordResetPolicy;
import org.artifactory.descriptor.security.PasswordSettings;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.UserLockPolicy;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.oauth.OAuthProviderSettings;
import org.artifactory.event.CacheType;
import org.artifactory.event.InvalidateCacheEvent;
import org.artifactory.exception.InvalidNameException;
import org.artifactory.exception.ValidationException;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.model.security.UserImpl;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.schedule.CachedThreadPoolTaskExecutor;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.access.emigrate.AccessConverter;
import org.artifactory.security.access.emigrate.AccessConverters;
import org.artifactory.security.exceptions.LoginDisabledException;
import org.artifactory.security.exceptions.PasswordChangeException;
import org.artifactory.security.exceptions.PasswordExpireException;
import org.artifactory.security.exceptions.UserDisabledException;
import org.artifactory.security.exceptions.UserLockedException;
import org.artifactory.security.interceptor.SecurityConfigurationChangesInterceptors;
import org.artifactory.security.jobs.CredentialsWatchJob;
import org.artifactory.security.jobs.PasswordExpireNotificationJob;
import org.artifactory.security.permissionscope.PermissionScopeToken;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.security.props.auth.PropsAuthenticationToken;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.init.migration.MarkerMigrationHelper;
import org.artifactory.storage.db.security.service.access.UserMapper;
import org.artifactory.storage.db.security.service.access.UserPropertiesSearchHelper;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.storage.security.service.UserLockInMemoryService;
import org.artifactory.ui.rest.service.state.GlobalStateProviderService;
import org.artifactory.update.security.SecurityInfoReader;
import org.artifactory.update.security.SecurityVersion;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.EmailException;
import org.artifactory.util.Files;
import org.artifactory.util.ImportValidationUtils;
import org.artifactory.util.NameValidator;
import org.artifactory.util.PathMatcher;
import org.artifactory.util.SecurityUtils;
import org.artifactory.util.SerializablePair;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.model.CrowdSetting;
import org.jfrog.access.client.model.HttpSsoSetting;
import org.jfrog.access.client.model.MessageModel;
import org.jfrog.access.client.model.OAuthSetting;
import org.jfrog.access.client.model.SamlSetting;
import org.jfrog.access.client.permission.GrpcAffectedEntitiesResponse;
import org.jfrog.access.model.Realm;
import org.jfrog.access.rest.group.Group;
import org.jfrog.access.rest.system.EnabledRealmsConfig;
import org.jfrog.access.rest.user.UpdateUserRequest;
import org.jfrog.access.rest.user.UserRequest;
import org.jfrog.access.token.AccessTokenType;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.TokenType;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.security.crypto.DecodedKeyPair;
import org.jfrog.security.crypto.EncodedKeyPair;
import org.jfrog.security.crypto.EncodingType;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.EncryptionWrapperFactory;
import org.jfrog.security.crypto.JFrogCryptoHelper;
import org.joda.time.DateTime;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static org.artifactory.sapi.security.SecurityConstants.DEFAULT_REALM;
import static org.artifactory.security.props.auth.OauthManager.OAUTH_INFO_PROPERTY;
import static org.artifactory.storage.db.security.service.access.AccessUserGroupStoreService.GroupFilter;
import static org.jfrog.access.util.AccessCredsFileHelper.ADMIN_DEFAULT_USERNAME;

@Slf4j
@Service
@Reloadable(beanClass = InternalSecurityService.class,
        initAfter = {InternalCentralConfigService.class, DbService.class, AccessService.class},
        listenOn = CentralConfigKey.security)
public class SecurityServiceImpl implements InternalSecurityService {

    private static final String ERR_CAUSE = "Cause: ";
    private static final String ERR_USER = "User ";
    private static final int MAX_SOURCES_TO_TRACK = 10000;
    private static final int MIN_DELAY_BETWEEN_FORGOT_PASSWORD_ATTEMPTS_PER_SOURCE = 500; // ms
    private static final int MAX_USERS_TO_TRACK = 10000; // max locked users to keep in cache
    static final String PASSWORD_EXPIRY_NOTIFICATION_PROPERTY_DEPRECATED =
            "artifactory.security.password.expiry.passwordExpireNotificationJobIntervalSecs";

    // cache meaning <userName, incorrect-login-timestamps>
    private final Cache<String, List<Long>> unknownUsersCache = CacheBuilder.newBuilder()
            .maximumSize(MAX_USERS_TO_TRACK)
            .expireAfterWrite(1, TimeUnit.HOURS).build();
    private final TreeSet<SecurityListener> securityListeners = new TreeSet<>();
    private final AtomicBoolean forceEnabledBasicAuth = new AtomicBoolean(false);
    private Cache<String, List<Long>> resetPasswordAttemptsBySourceCache;
    /**
     * Injected instead of read through {@link ConstantValues#getBoolean()} because the only consumer runs on an
     * Access gRPC callback thread, which has no {@link ArtifactoryHome} bound for that thread-local lookup.
     * Resolved once at bean creation, so changing the property takes effect on the next restart.
     * {@code SecurityServiceImplTest} pins this key to the constant.
     */
    @Value("${artifactory.security.authentication.cache.immediate.invalidation.enabled}")
    private boolean authCacheImmediateInvalidationEnabled;
    private InternalArtifactoryContext context;
    private MarkerMigrationHelper markerMigrationHelper;
    private AccessService accessService;
    private UserGroupStoreService userGroupStoreService;
    private AuthorizationService authorizationService;
    private CentralConfigService centralConfig;
    private MailService mailService;
    private AddonsManager addons;
    private LdapService ldapService;
    private SecurityConfigurationChangesInterceptors interceptors;
    private CachedThreadPoolTaskExecutor executor;
    private TaskService taskService;
    private UserPassAuthenticationProvider userPassAuthenticationProvider;
    private AccessConverters accessConverters;
    private UserLockInMemoryService userLockInMemoryService;
    private ApiKeyManager apiKeyManager;
    private BuildFacade buildFacade;
    private ArtifactoryGroupCachingRepoImpl groupCachingRepo;
    private ApplicationEventPublisher publisher;
    private GlobalStateProviderService globalStateProviderService;
    private ProjectsService projectsService;

    private static boolean isAdmin(Authentication authentication) {
        return isAuthenticated(authentication) && getSimpleUser(authentication).isEffectiveAdmin();
    }

    private static boolean isAuthenticatedAsSystem(Authentication authentication) {
        return authentication instanceof SystemAuthenticationToken;
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated();
    }

    private static SimpleUser getSimpleUser(Authentication authentication) {
        return (SimpleUser) authentication.getPrincipal();
    }

    /**
     * Generates a root {@link RepoPath} in given Build info/Release bundle repository according to given
     * build/release-bundle name.
     **/
    private static RepoPath getRootPathInRepo(String name, String repoKey) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Cannot generate path - name is missing");
        }
        return RepoPathFactory.create(repoKey, name + "/");
    }

    @Autowired
    public void setAccessService(AccessService accessService) {
        this.accessService = accessService;
    }

    @Autowired
    public void setUserGroupStoreService(UserGroupStoreService userGroupStoreService) {
        this.userGroupStoreService = userGroupStoreService;
    }

    @Autowired
    public void setAuthorizationService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Autowired
    public void setCentralConfig(CentralConfigService centralConfig) {
        this.centralConfig = centralConfig;
    }

    @Autowired
    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

    @Autowired
    public void setAddons(AddonsManager addons) {
        this.addons = addons;
    }

    @Autowired
    public void setLdapService(LdapService ldapService) {
        this.ldapService = ldapService;
    }

    @Autowired
    public void setInterceptors(SecurityConfigurationChangesInterceptors interceptors) {
        this.interceptors = interceptors;
    }

    @Autowired
    public void setExecutor(CachedThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Autowired
    public void setUserPassAuthenticationProvider(UserPassAuthenticationProvider userPassAuthenticationProvider) {
        this.userPassAuthenticationProvider = userPassAuthenticationProvider;
    }

    @Autowired
    public void setAccessConverters(AccessConverters accessConverters) {
        this.accessConverters = accessConverters;
    }

    @Autowired
    public void setUserLockInMemoryService(UserLockInMemoryService userLockInMemoryService) {
        this.userLockInMemoryService = userLockInMemoryService;
    }

    @Autowired
    public void setApiKeyManager(ApiKeyManager apiKeyManager) {
        this.apiKeyManager = apiKeyManager;
    }

    @Autowired
    public void setBuildFacade(BuildFacade buildFacade) {
        this.buildFacade = buildFacade;
    }

    @Autowired
    public void setGroupCachingRepo(ArtifactoryGroupCachingRepoImpl groupCachingRepo) {
        this.groupCachingRepo = groupCachingRepo;
    }

    @Autowired
    public void setPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Autowired
    public void setGlobalStateProviderService(GlobalStateProviderService globalStateProviderService) {
        this.globalStateProviderService = globalStateProviderService;
    }

    @Autowired
    public void setProjectsService(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Autowired
    protected void setApplicationContext(ApplicationContext context) throws BeansException {
        this.context = (InternalArtifactoryContext) context;
    }

    @Override
    public void init() {
        //Locate and import external configuration file
        checkForExternalConfiguration();

        // start CredentialsWatchJob
        Task credentialsWatchJob = TaskUtils.createRepeatingTask(CredentialsWatchJob.class,
                TimeUnit.SECONDS.toMillis(ConstantValues.passwordExpireJobIntervalSecs.getLong()),
                TimeUnit.SECONDS.toMillis(30L));
        taskService.startTask(credentialsWatchJob, false);

        // start PasswordExpireNotificationJob
        Task passwordExpireNotificationJob = createPasswordExpireNotificationJob();
        taskService.startTask(passwordExpireNotificationJob, false);

        initResetPasswordCache(getPasswordResetPolicy(centralConfig.getDescriptor()));
    }

    @VisibleForTesting
    Task createPasswordExpireNotificationJob() {
        String property = System.getProperty(PASSWORD_EXPIRY_NOTIFICATION_PROPERTY_DEPRECATED);
        if (StringUtils.isNotBlank(property)) { // for backwards compatibility
            log.warn("Property artifactory.security.password.expiry.passwordExpireNotificationJobIntervalSecs " +
                    "is deprecated and prone to synchronization issues. Please consider using property " +
                    "artifactory.security.password.expiry.passwordExpireNotificationJobCron instead.");
            return TaskUtils.createRepeatingTask(PasswordExpireNotificationJob.class,
                    TimeUnit.SECONDS.toMillis(Long.parseLong(property)),
                    TimeUnit.SECONDS.toMillis(60L));
        } else {
            return TaskUtils.createCronTask(PasswordExpireNotificationJob.class,
                    ConstantValues.passwordExpireNotificationJobCron.getString());
        }
    }

    @Override
    public void onContextCreated() {
        accessService.subscribeForClientChanges(this);
        accessService.getAccessClient().grpcPermissions().onPermissionsAffected(this::onPermissionsUpdateEvent);
    }

    @Override
    public void onClientUpdate() {
        accessService.getAccessClient().grpcPermissions().onPermissionsAffected(this::onPermissionsUpdateEvent);
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        // Need to check if security conf changed then clear security caches
        SecurityDescriptor securityDescriptor = centralConfig.getDescriptor().getSecurity();

        if (!securityDescriptor.equals(oldDescriptor.getSecurity())) {
            clearSecurityListeners();
            // Need to check if password reset policy changed
            PasswordResetPolicy passwordResetPolicy = getPasswordResetPolicy(centralConfig.getDescriptor());
            if (!passwordResetPolicy.equals(getPasswordResetPolicy(oldDescriptor))) {
                initResetPasswordCache(passwordResetPolicy);
            }
            SecurityUtils.forceEnableBasicAuthIfNeeded();
        }
    }

    private void initResetPasswordCache(PasswordResetPolicy passwordResetPolicy) {
        resetPasswordAttemptsBySourceCache = CacheBuilder.newBuilder()
                .maximumSize(MAX_SOURCES_TO_TRACK)
                .expireAfterWrite(passwordResetPolicy.getTimeToBlockInMinutes(), TimeUnit.MINUTES).build();
    }

    @Override
    public void destroy() {
    }

    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        List<String> uniqueNames = StreamSupportUtils.stream(accessConverters.getAll())
                .map(accessConverter -> accessConverter.getMarkerMigrationConverter().uniqueName())
                .collect(Collectors.toList());
        boolean anyUnfinished = CollectionUtils.notNullOrEmpty(markerMigrationHelper.getUnfinished(uniqueNames));
        boolean anyMarkerFileExists = StreamSupportUtils.stream(accessConverters.getAll())
                .map(AccessConverter::getMarkerFile)
                .anyMatch(File::exists);

        return anyUnfinished || anyMarkerFileExists ||
                InternalSecurityService.super.isInterested(source, target);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        for (AccessConverter accessConverter : accessConverters.getAll()) {
            convertAndUpdateMarkerMigration(accessConverter);
        }
    }

    private void convertAndUpdateMarkerMigration(AccessConverter accessConverter) {
        String uniqueName = accessConverter.getMarkerMigrationConverter().uniqueName();

        if (accessConverter.getMarkerFile().exists()) {
            markerMigrationHelper.reset(uniqueName);
        }
        if (markerMigrationHelper.shouldConvert(uniqueName)) {
            log.debug("Converting access converter '{}'",
                    accessConverter.getClass().getSimpleName());
            accessConverter.convert();
            markerMigrationHelper.finish(uniqueName);
            deleteMarkerFileIfExists(accessConverter);
        } else {
            log.debug("Skip converting access converter '{}'",
                    accessConverter.getClass().getSimpleName());
        }
        deleteMarkerFileIfExists(accessConverter);
    }

    private void deleteMarkerFileIfExists(AccessConverter accessConverter) {
        boolean deleted = accessConverter != null &&
                FileUtils.deleteQuietly(accessConverter.getMarkerFile());
        if (deleted) {
            log.debug("Marker file deleted");
        }
    }

    /**
     * Checks for an externally supplied configuration file ($ARTIFACTORY_HOME/etc/security.xml). If such a file is
     * found, it will be deserialized to a security info (descriptor) object and imported to the system. This option is
     * to be used in cases like when an administrator is locked out of the system, etc'.
     */
    private void checkForExternalConfiguration() {
        ArtifactoryContext ctx = ContextHelper.get();
        File etcDir = ctx.getArtifactoryHome().getEtcDir();
        File configurationFile = new File(etcDir, "security.import.xml");
        //Work around Jackrabbit state visibility issues within the same tx by forking a separate tx (RTFACT-4526)
        Callable callable = () -> {
            String configAbsolutePath = configurationFile.getAbsolutePath();
            if (configurationFile.isFile()) {
                if (!configurationFile.canRead() || !configurationFile.canWrite()) {
                    throw new ConfigurationException(
                            "Insufficient permissions. Security configuration import requires " +
                                    "both read and write permissions for " + configAbsolutePath
                    );
                }
                try {
                    SecurityInfo descriptorToSave = new SecurityInfoReader().read(configurationFile);
                    //InternalSecurityService txMe = ctx.beanForType(InternalSecurityService.class);
                    getAdvisedMe().importSecurityData(descriptorToSave);
                    Files.switchFiles(configurationFile, new File(etcDir, "security.bootstrap.xml"));
                    log.info("Security configuration imported successfully from {}", configAbsolutePath);
                } catch (Exception e) {
                    throw new IllegalArgumentException("An error has occurred while deserializing the file " +
                            configAbsolutePath +
                            ". Please assure it's validity or remove it from the 'etc' folder.", e);
                }
            }
            return null;
        };
        @SuppressWarnings("unchecked") Future<Set<String>> future = executor.submit(callable);
        try {
            future.get();
        } catch (Exception e) {
            throw new RuntimeException("Could not import external security config.", e);
        }
    }

    @Override
    public boolean isAnonymous() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        return authentication != null && isAnonymousUser(authentication.getName());
    }

    @Override
    public boolean isProfileUpdatable(UserInfo userInfo) {
        String realm = userInfo.getRealm();
        return userInfo.isUpdatableProfile() ||
                canUpdateWithCrowd(realm) ||
                canUpdateWithLdap(userInfo) ||
                canUpdateWithOAuth(realm) ||
                canUpdateWithSaml(realm) ||
                canUpdateWithSso(realm);
    }

    private boolean canUpdateWithOAuth(String realm) {
        return realm.equals(OAuthHandler.REALM) &&
                ((!accessService.getAllOAuthSettings().isEmpty() &&
                        accessService.getAllOAuthSettings().getFirst().isAllowUserToAccessProfile()) ||
                        isLoggedInWithCentralOAuthProvider());
    }

    private boolean canUpdateWithSso(String realm) {
        return realm.equals(HttpSsoAddon.REALM) && accessService.getAccessAuthSettingsClient()
                .getHttpSsoSetting().map(HttpSsoSetting::isAllowUserToAccessProfile).orElse(false);
    }

    private boolean canUpdateWithSaml(String realm) {
        return realm.equals(SamlSsoAddon.REALM) && accessService.getSamlSettings().isAllowUserToAccessProfile();
    }

    private boolean canUpdateWithCrowd(String realm) {
        return realm.equals(CrowdAddon.REALM) && accessService.getAccessAuthSettingsClient()
                .getCrowdSetting().map(CrowdSetting::isAllowUserToAccessProfile).orElse(false);
    }

    private boolean canUpdateWithLdap(UserInfo userInfo) {
        String userLdapSetting = ldapService.getUserLdapSetting(userInfo.getUsername());
        if (userLdapSetting == null) {
            return false;
        }

        boolean isAllowToAccessProfile = false;
        LdapSetting setting = accessService.getLdapSettings(userLdapSetting);
        if (null != setting) {
            isAllowToAccessProfile = setting.isAllowUserToAccessProfile();
        }
        return LdapService.REALM.equals(userInfo.getRealm()) && isAllowToAccessProfile;
    }

    @Override
    public boolean requireProfileUnlock(@Nullable String overrideRealm, @Nullable String providerName) {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        boolean isAccessTokenAuth = AuthenticationHelper.getAuthentication() instanceof AccessTokenAuthentication;
        boolean allowSsoAuth = isAllowSsoAuth(addonsManager, isAccessTokenAuth, overrideRealm);
        boolean allowSamlAuth = isAllowSamlAuth(addonsManager, isAccessTokenAuth, overrideRealm);
        boolean allowPropsAuth = isAllowPropsAuth(isAccessTokenAuth, overrideRealm);
        boolean allowCrowdAuth = isAllowCrowdAuth(addonsManager, isAccessTokenAuth, overrideRealm);
        boolean overrideAllowCentralOAuth = OAuthHandler.REALM.equals(overrideRealm) &&
                ofNullable(providerName)
                        .map(this::isCentralOAuthProviderEnabled)
                        .orElse(false);
        return !(allowPropsAuth || allowSamlAuth || allowSsoAuth || allowCrowdAuth || overrideAllowCentralOAuth);
    }

    private boolean isAllowPropsAuth(boolean isAccessTokenAuth, String overrideRealm) {
        List<OAuthSetting> oAuthSettingList = accessService.getAllOAuthSettings();
        boolean allowUserToAccessProfileOauth = (oAuthSettingList != null && !oAuthSettingList.isEmpty()) &&
                oAuthSettingList.getFirst().isAllowUserToAccessProfile();
        boolean isPropsAuth = AuthenticationHelper.getAuthentication() instanceof PropsAuthenticationToken;
        boolean isCentralOAuthProvider = isLoggedInWithCentralOAuthProvider();
        boolean accessTokenOauth = isAccessTokenAuth && OAuthHandler.REALM.equals(overrideRealm);
        return (accessTokenOauth || isPropsAuth) && (allowUserToAccessProfileOauth || isCentralOAuthProvider);
    }

    private boolean isLoggedInWithCentralOAuthProvider() {
        return ofNullable(AuthenticationHelper.getAuthentication())
                .filter(Objects::nonNull)
                .filter(PropsAuthenticationToken.class::isInstance)
                .map(auth -> ((PropsAuthenticationToken) auth).getPropsKey())
                .filter(String.class::isInstance)
                .filter(prop -> StringUtils.startsWith((String) prop, OAUTH_INFO_PROPERTY))
                .map(prop -> StringUtils.removeStart((String) prop, OAUTH_INFO_PROPERTY))
                .map(this::isCentralOAuthProviderEnabled)
                .orElse(false);
    }

    private boolean isCentralOAuthProviderEnabled(@Nonnull String providerName) {
        CentralOAuthSettings centralOAuthSettings = addons.addonByType(CentralOAuthSSOAddon.class).getConfiguration();
        if (!centralOAuthSettings.isEnabled()) {
            return false;
        }
        return centralOAuthSettings.getProviders().stream()
                .filter(OAuthProviderSettings::getEnabled)
                .anyMatch(p -> p.getName().equals(providerName));
    }

    private boolean isAllowSamlAuth(AddonsManager addonsManager,
            boolean isAccessTokenAuth, String overrideRealm) {
        UserInfo userInfo = currentUser();
        if (accessService.isRealmAuthenticationEnabled(Realm.SAML) &&
                (Realm.SAML.getName().equalsIgnoreCase(userInfo.getRealm()))) {
            return !userInfo.isTransientUser() && isProfileUpdatable(userInfo);
        }
        SamlSetting samlSetting = accessService.getSamlSettings();
        boolean allowUserToAccessProfileSaml = samlSetting.isAllowUserToAccessProfile();
        boolean isAccessSaml = isAccessTokenAuth && SamlSsoAddon.REALM.equals(overrideRealm);
        return (isAccessSaml || addonsManager.addonByType(SamlSsoAddon.class).isSamlAuthentication()) &&
                allowUserToAccessProfileSaml;
    }

    private boolean isAllowSsoAuth(AddonsManager addonsManager, boolean isAccessTokenAuth, String overrideRealm) {
        boolean allowUserToAccessProfileSso = accessService.getAccessAuthSettingsClient()
                .getHttpSsoSetting().map(HttpSsoSetting::isAllowUserToAccessProfile).orElse(false);
        boolean isAccessSso = isAccessTokenAuth && HttpSsoAddon.REALM.equals(overrideRealm);
        return (isAccessSso || addonsManager.addonByType(HttpSsoAddon.class).isHttpSsoAuthentication()) &&
                allowUserToAccessProfileSso;
    }

    private boolean isAllowCrowdAuth(AddonsManager addonsManager, boolean isAccessTokenAuth,
            @Nullable String overrideRealm) {
        boolean allowUserToAccessProfileCrowd = accessService.getAccessAuthSettingsClient()
                .getCrowdSetting().map(CrowdSetting::isAllowUserToAccessProfile).orElse(false);
        boolean isAccessCrowd = isAccessTokenAuth && CrowdAddon.REALM.equals(overrideRealm);
        boolean isCrowd = addonsManager.addonByType(CrowdAddon.class)
                .isCrowdAuthenticationSupported(AuthenticationHelper.getAuthentication().getClass()) &&
                CrowdAddon.REALM.equals(overrideRealm);
        return (isAccessCrowd || isCrowd) &&
                allowUserToAccessProfileCrowd;
    }

    @Override
    public boolean requireProfilePassword() {
        UserInfo userInfo = findUser(currentUsername(), false);
        return requireProfilePassword(userInfo);
    }

    @Override
    public boolean requireProfilePassword(UserInfo userInfo) {
        boolean userHasPassword = false;
        if (userInfo != null && userInfo.getPassword() != null) {
            userHasPassword = !userInfo.getPassword().isEmpty();
        }
        boolean allowUserToAccessProfileSso = accessService.getAccessAuthSettingsClient()
                .getHttpSsoSetting().map(HttpSsoSetting::isAllowUserToAccessProfile).orElse(false);
        SamlSetting samlSetting = accessService.getSamlSettings();
        boolean allowUserToAccessProfileSaml = samlSetting.isAllowUserToAccessProfile();
        boolean allowUserToAccessProfileOauth =
                (!accessService.getAllOAuthSettings().isEmpty() &&
                        accessService.getAllOAuthSettings().getFirst().isAllowUserToAccessProfile()) ||
                        isLoggedInWithCentralOAuthProvider();
        Authentication authentication = AuthenticationHelper.getAuthentication();
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        SamlSsoAddon samlSsoAddon = addonsManager.addonByType(SamlSsoAddon.class);
        HttpSsoAddon httpSsoAddon = addonsManager.addonByType(HttpSsoAddon.class);
        return !doNotRequirePasswordFromExtUser(userHasPassword, allowUserToAccessProfileSso,
                allowUserToAccessProfileSaml, allowUserToAccessProfileOauth,
                authentication, samlSsoAddon, httpSsoAddon);
    }

    private boolean doNotRequirePasswordFromExtUser(boolean userHasPassword, boolean allowUserToAccessProfileSso,
            boolean allowUserToAccessProfileSaml, boolean allowUserToAccessProfileOauth, Authentication authentication,
            SamlSsoAddon samlSsoAddon, HttpSsoAddon httpSsoAddon) {
        return ((authentication instanceof PropsAuthenticationToken && !allowUserToAccessProfileOauth) ||
                (samlSsoAddon.isSamlAuthentication() && !allowUserToAccessProfileSaml) ||
                (httpSsoAddon.isHttpSsoAuthentication() && !allowUserToAccessProfileSso)) && !userHasPassword;
    }

    @Override
    public boolean isAnonAccessEnabled() {
        return accessService.isAnonymousAccessEnabled();
    }

    @Override
    public boolean isAuthenticated() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        return isAuthenticated(authentication);
    }

    @Override
    public boolean isAdmin() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        return isAdmin(authentication);
    }

    @Override
    public boolean isAuthenticatedAsSystem() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        return isAuthenticatedAsSystem(authentication);
    }

    @Override
    public boolean hasMcScope() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication instanceof AccessTokenAuthentication accessToken) {
            return accessToken.getAccessToken()
                    .hasScopeMatching(s -> s.contains(PermissionScopeToken.INTERNAL_MISSION_CONTROL_EXECUTE));
        }
        return false;
    }

    @Override
    public boolean isUpdatableProfile() {
        UserInfo simpleUser = currentUser();
        return simpleUser != null && simpleUser.isUpdatableProfile();
    }

    @Override
    public boolean isTransientUser() {
        UserInfo simpleUser = currentUser();
        return simpleUser != null && simpleUser.isTransientUser();
    }

    @Nullable
    @Override
    public String getAuthenticatedRealm() {
        return currentUser() != null ? currentUser().getRealm() : null;
    }

    @Override
    @Nonnull
    public String currentUsername() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        //Do not return a null username or this will cause a constraint violation
        return (authentication != null ? authentication.getName() : SecurityService.USER_SYSTEM);
    }

    @Override
    public UserInfo currentUser() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication == null) {
            return null;
        }
        SimpleUser user = getSimpleUser(authentication);
        return user.getDescriptor();
    }

    @Override
    @Nonnull
    public UserInfo findUser(String username) {
        return findUser(username, true);
    }

    /**
     * Returns the user details for the given username.
     *
     * @param username       The unique username
     * @param errorOnAbsence throw error if user is not found
     * @return UserInfo if user with the input username exists
     */
    @Override
    @Nullable
    public UserInfo findUser(String username, boolean errorOnAbsence) {
        UserInfo user = userGroupStoreService.findUser(username);
        if (errorOnAbsence && user == null) {
            throw new UsernameNotFoundException(ERR_USER + username + " does not exist!");
        }
        return user;
    }

    @Override
    public boolean isUserExists(String userName) {
        return userGroupStoreService.userExists(userName);
    }

    @Override
    public Map<String, Boolean> getAllUsersAndAdminStatus(boolean justAdmins) {
        Collection<String> groups = new ArrayList<>();
        if (justAdmins) {
            groups.addAll(groupCachingRepo.getGroupsByFilter(GroupFilter.ADMIN).keySet());
        } else {
            groups.addAll(groupCachingRepo.fetchAllGroups().keySet());
        }
        return userGroupStoreService.getAllUsersAndAdminStatus(justAdmins, groups);
    }

    @Override
    public Map<String, Boolean> filterUsersByAdminStatus(boolean justAdmins, List<UserInfo> allUsers) {
        return userGroupStoreService.filterUsersByAdminStatus(justAdmins, allUsers);
    }

    @Override
    public List<UserInfo> getAllUsers(boolean includeAdmins) {
        return getAllUsersInternal(includeAdmins, false, true);
    }

    @Override
    public List<UserInfo> getAllUsersWithUserInfoWithOutPasswords(boolean includeAdmins) {
        Collection<String> groups = new ArrayList<>(groupCachingRepo.getGroupsByFilter(GroupFilter.ADMIN).keySet());
        return userGroupStoreService.getAllUsers(includeAdmins, false, true, groups);
    }

    private List<UserInfo> getAllUsersInternal(boolean includeAdmins, boolean includePasswordsAndEncrypted,
            boolean decryptUserInfoProperties) {
        return userGroupStoreService
                .getAllUsers(includeAdmins, includePasswordsAndEncrypted, decryptUserInfoProperties);
    }

    @Override
    public boolean isReleaseBundleInPermission(List<String> includePatterns, List<String> excludePatterns,
            BundleNameAndRepo bundle) {
        return isRepoPathInAclPermissions(includePatterns, excludePatterns,
                getRootPathInRepo(bundle.getName(), bundle.getStoringRepo()));
    }

    /**
     * Checks if the permission target include/exclude match the build name
     */
    private boolean isRepoPathInAclPermissions(List<String> includePatterns, List<String> excludePatterns,
            RepoPath repoPath) {
        String path = getPathToCheckPermissionFor(repoPath);
        return PathMatcher.matches(path, includePatterns, excludePatterns, repoPath.isFolder());
    }

    @Override
    public org.artifactory.md.Properties findPropertiesForUser(String username) {
        return userGroupStoreService.findPropertiesForUser(username);
    }

    @Override
    public org.artifactory.md.Properties findPropertiesForUser(UserInfo userInfo) {
        return userGroupStoreService.findPropertiesForUser(userInfo);
    }

    @Override
    public boolean addUserProperty(String username, String key, String value) {
        return userGroupStoreService.addUserProperty(username, key, value);
    }

    @Override
    public boolean updateUserProperty(String username, String key, String value) {
        boolean isUpdateSucceeded = userGroupStoreService.deleteUserProperty(username, key);
        if (isUpdateSucceeded) {
            isUpdateSucceeded = userGroupStoreService.addUserProperty(username, key, value);
        }
        return isUpdateSucceeded;
    }

    @Override
    public String getUserProperty(String username, String key) {
        return userGroupStoreService.findUserProperty(username, key);
    }

    @Override
    public void deleteUserProperty(String userName, String propertyKey) {
        userGroupStoreService.deleteUserProperty(userName, propertyKey);
    }

    @Override
    public void deletePropertyFromAllUsers(String propertyKey) {
        userGroupStoreService.deletePropertyFromAllUsers(propertyKey);
    }

    @Override
    public String getPropsToken(String userName, String propsKey) {
        return userGroupStoreService.findUserProperty(userName, propsKey);
    }

    @Override
    public boolean revokePropsToken(String userName, String propsKey, boolean notifyListeners) {
        if (notifyListeners) {
            invalidateAuthCacheEntries(userName);
        }
        return userGroupStoreService.deleteUserProperty(userName, propsKey);
    }

    /**
     * Adds a props token. An API key ({@link ApiKeyManager#API_KEY}) is refused (returns false) for a user whose
     * password must be changed first ({@link UserInfo#isMustChangePassword()}).
     */
    @Override
    public boolean createPropsToken(String userName, String propsKey, String propsValue) {
        if (isApiKeyRefused(userName, propsKey)) {
            return false;
        }
        boolean isPropsAddSucceeded = false;
        try {
            isPropsAddSucceeded = userGroupStoreService.addUserProperty(userName, propsKey, propsValue);
        } catch (Exception e) {
            log.debug("Error adding {}: {} to db", propsKey, propsValue);
        }
        return isPropsAddSucceeded;
    }

    @Override
    public void revokeAllPropsTokens(String propsKey, boolean notifyListeners) {
        userGroupStoreService.deletePropertyFromAllUsers(propsKey);
        if (notifyListeners) {
            invalidateAuthCacheEntriesForAllUsers();
        }
    }

    /**
     * Replaces a props token. Like {@link #createPropsToken}, an API key is refused (returns false, the existing value
     * is kept) for a user whose password must be changed first.
     */
    @Override
    public boolean updatePropsToken(String userName, String propsKey, String propsValue) {
        if (isApiKeyRefused(userName, propsKey)) {
            return false;
        }
        boolean isUpdateSucceeded = userGroupStoreService.deleteUserProperty(userName, propsKey);
        if (isUpdateSucceeded) {
            isUpdateSucceeded = userGroupStoreService.addUserProperty(userName, propsKey, propsValue);
            invalidateAuthCacheEntries(userName);
        }
        return isUpdateSucceeded;
    }

    private boolean isApiKeyRefused(String userName, String propsKey) {
        if (!ApiKeyManager.API_KEY.equals(propsKey)
                || MustChangePasswordRules.mayIssueCredential(userGroupStoreService.findUser(userName))) {
            return false;
        }
        log.warn("Refusing to create an API key for user '{}': password change required", userName);
        return true;
    }

    @Override
    public void setMustChangePassword(@Nonnull String userName, boolean mustChangePassword) {
        userGroupStoreService.setMustChangePassword(userName, mustChangePassword);
        invalidateAuthCacheEntries(userName);
        log.info("Must-change-password flag for user '{}' {}", userName, mustChangePassword ? "set" : "cleared");
    }

    /**
     * Marks {@code user} as having to change its password when the current, authenticated administrator writes a
     * new password for another user ({@link MustChangePasswordRules#setsFlag}).
     *
     * @param storedPassword the password held by the stored user, null for a new user
     */
    private void flagIfPasswordSetByAnotherAdministrator(MutableUserInfo user, String storedPassword) {
        if (MustChangePasswordRules.setsFlag(currentUsername(), isAdmin(), user.getUsername(), user.getPassword(),
                storedPassword, user.isPasswordDisabled())) {
            user.setMustChangePassword(true);
        }
    }

    /**
     * Locks user upon incorrect login attempt
     */
    @Override
    public void lockUser(@Nonnull String userName) {
        userName = userName.toLowerCase();
        if (!isAnonymousUser(userName)) {
            log.debug("Locking user due to incorrect login attempts");
            userGroupStoreService.lockUser(userName);
            userLockInMemoryService.lockUser(userName);
        }
    }

    /**
     * Unlocks locked in user
     */
    @Override
    public void unlockUser(@Nonnull String userName) {
        userName = userName.toLowerCase();
        if (!isAnonymousUser(userName)) {
            log.debug("Unlocking user: '{}'", userName);
            userLockInMemoryService.unlockUser(userName);
            userGroupStoreService.unlockUser(userName);
            resetUserIncorrectLoginAttempt(userName);
        }
    }

    /**
     * Unlocks all locked in users
     */
    @Override
    public void unlockAllUsers() {
        log.debug("Unlocking all users");
        userGroupStoreService.unlockAllUsers();
        userLockInMemoryService.unlockAllUsers();
        unknownUsersCache.invalidateAll();
        getAllUsers(true).forEach(user -> resetUserIncorrectLoginAttempt(user.getUsername()));
    }

    /**
     * Registers incorrect login attempt
     */
    @Override
    public void registerIncorrectLoginAttempt(@Nonnull String userName, long accessTime) {
        if (delegateTempLoginToAccess()) {
            log.trace("Temp login is delegated to access");
            return;
        }
        userName = userName.toLowerCase();
        if (!isAnonymousUser(userName)) {
            log.debug("Registering incorrect login attempt for user: {}", userName);
            userLockInMemoryService.updateUserAccess(userName, isUserLockPolicyEnabled(), accessTime);
            userLockInMemoryService.registerIncorrectLoginAttempt(userName);
            log.debug("Incorrect login attempts for user: {} are now: {}", userName,
                    userLockInMemoryService.getIncorrectLoginAttempts(userName));
        }
    }

    /**
     * @return Set of locked in users
     */
    @Override
    public Set<String> getLockedUsers() {
        return userGroupStoreService.getLockedUsers();
    }

    @Override
    public ImmutableSetMultimap<String, String> getAllUsersInGroups() {
        return userGroupStoreService.getAllUsersInGroups();
    }

    @Override
    public boolean adminUserExists() {
        return userGroupStoreService.adminUserExists();
    }

    @Override
    public boolean isValidGenPassword(String genPassword) {
        if (StringUtils.isNotBlank(genPassword)) {
            log.debug("Verifying the given generated password");
            String decodedPassword = new String(Base64.decodeBase64(genPassword), Charsets.UTF_8);
            String createdTimeStamp = StringUtils.substringBetween(decodedPassword, ":", ":");
            try {
                boolean valid = TimeUnit.MILLISECONDS
                        .toMinutes(System.currentTimeMillis() - Long.parseLong(createdTimeStamp)) <=
                        ConstantValues.generatedPasswordExpirationPeriodMinutes.getLong();
                if (!valid) {
                    log.debug("The given generated password is expired, pls generate a new one");
                }
                return valid;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    @Override
    public ImmutableSetMultimap<String, String> getAllUsersInGroups(List<UserInfo> allUsers) {
        return userGroupStoreService.getAllUsersInGroups(allUsers);
    }

    @Override
    public UserMFAStatus getMfaStatus() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication == null) {
            return UserMFAStatus.NONE;
        }

        SimpleUser user = (SimpleUser) authentication.getPrincipal();
        if (user == null || user.getDescriptor() == null) {
            return UserMFAStatus.NONE;
        }
        return user.getDescriptor().getMfaStatus();
    }

    private void resetUserIncorrectLoginAttempt(@Nonnull String userName) {
        userName = userName.toLowerCase();
        if (!isAnonymousUser(userName)) {
            log.debug("Resetting incorrect login attempt");
            userLockInMemoryService.resetIncorrectLoginAttempts(userName);
            unknownUsersCache.invalidate(userName);
        }
    }

    /**
     * Triggered when user success to login
     *
     * @param userName user to intercept
     */
    @Override
    public void interceptLoginSuccess(@Nonnull String userName) {
        resetUserIncorrectLoginAttempt(userName);
    }

    /**
     * Triggered when user fails to login and
     * locks it if amount of login failures exceeds
     * {@see LockPolicy#loginAttempts}
     *
     * @param userName   user to intercept
     * @param accessTime session creation time
     */
    @Override
    public void interceptLoginFailure(@Nonnull String userName, long accessTime) {
        if (!isAnonymousUser(userName)) {
            log.debug("Registering login attempt failure");
            registerIncorrectLoginAttempt(userName, accessTime);
            UserInfo user = userGroupStoreService.findUser(userName);
            if (user == null) {
                interceptUnknownUserLoginFailure(userName, accessTime);
            } else {
                interceptKnownUserLoginFailure(user);
            }
        }
    }

    /**
     * Intercepts login failure for (known to artifactory) user
     *
     * @param user user to intercept failure for
     */
    private void interceptKnownUserLoginFailure(UserInfo user) {
        if (delegateTempLoginToAccess()) {
            return;
        }
        log.debug("Number of incorrect login attempts: " +
                userLockInMemoryService.getIncorrectLoginAttempts(user.getUsername()) +
                ", Max allowed login attempts: " + getAllowedMaxLoginAttempts());
        if (isUserLockPolicyEnabled() && !ArtifactoryUserStatus.LOCKED.equals(user.getStatus()) &&
                userLockInMemoryService.getIncorrectLoginAttempts(user.getUsername()) >= getAllowedMaxLoginAttempts()) {
            lockUser(user.getUsername());
        }
    }

    /**
     * Intercepts login failure for (unknown to artifactory) user
     *
     * @param userName   user to intercept failure for
     * @param accessTime access time
     */
    private void interceptUnknownUserLoginFailure(String userName, long accessTime) {
        if (delegateTempLoginToAccess()) {
            return;
        }
        log.trace("Memorizing (not a user) for blocking");
        if (!isUserLocked(userName)) {
            List<Long> incorrectLoginAttempts = unknownUsersCache.getIfPresent(userName);
            if (incorrectLoginAttempts == null) {
                log.trace("Registering incorrect login attempt for unknown user: '{}'", userName);
                // memorize incorrect login attempt
                unknownUsersCache.put(userName, Lists.newArrayList(accessTime));
            } else {
                incorrectLoginAttempts.add(accessTime);
                if (isUserLockPolicyEnabled() &&
                        incorrectLoginAttempts.size() >= getAllowedMaxLoginAttempts()) {
                    lockUser(userName);
                    unknownUsersCache.invalidate(userName); // no need to track this user as it got locked
                }
            }
        }
    }

    private boolean delegateTempLoginToAccess() {
        return accessService.getUserLockPolicyConfig().isClientLoginSuspensionEnabled();
    }

    /**
     * @return whether {@link UserLockPolicy} is enabled
     */
    @Override
    public boolean isUserLockPolicyEnabled() {
        return accessService.getUserLockPolicyConfig().getAttempts() > 0;
    }

    /**
     * @return whether {@link PasswordExpirationPolicy} is enabled
     */
    @Override
    public boolean isPasswordExpirationPolicyEnabled() {
        if (centralConfig.getDescriptor().getSecurity().getPasswordSettings().getExpirationPolicy() != null) {
            return centralConfig.getDescriptor()
                    .getSecurity()
                    .getPasswordSettings()
                    .getExpirationPolicy()
                    .getEnabled();
        }
        return false;
    }

    /**
     * @return MaxLoginAttempts allowed before user gets locked out
     */
    private int getAllowedMaxLoginAttempts() {
        return accessService.getUserLockPolicyConfig().getAttempts();
    }

    /**
     * @return Number of days for password to get expired
     */
    private int getPasswordExpirationDays() {
        PasswordExpirationPolicy passwordExpirationPolicy =
                centralConfig.getMutableDescriptor()
                        .getSecurity().getPasswordSettings().getExpirationPolicy();
        return passwordExpirationPolicy.getPasswordMaxAge();
    }

    /**
     * @return Max number of attempts for requesting password reset
     */
    private PasswordResetPolicy getPasswordResetPolicy(CentralConfigDescriptor centralConfig) {
        return ofNullable(centralConfig)
                .map(CentralConfigDescriptor::getSecurity)
                .map(SecurityDescriptor::getPasswordSettings)
                .map(PasswordSettings::getResetPolicy)
                .orElseGet(PasswordResetPolicy::new);
    }

    /**
     * Throws UserDisabledException if user is disabled
     */
    @Override
    public void ensureUserIsNotDisabled(@Nonnull String userName) throws UserDisabledException {
        log.debug("Checking if user is not disabled");
        if (isUserDisabled(userName)) {
            throw UserDisabledException.instance();
        }
    }

    /**
     * Throws LockedException if user is locked
     */
    @Override
    public void ensureUserIsNotLocked(@Nonnull String userName) throws UserLockedException {
        log.debug("Checking if user is not locked");
        if (!isAnonymousUser(userName) &&
                isUserLockPolicyEnabled() && isUserLocked(userName)) {
            log.debug("User is locked, denying login");
            throw UserLockedException.userLocked();
        }
    }

    /**
     * Checks whether given user is locked
     * <p>
     * note: this method using caching in sake
     * of DB load preventing
     *
     * @return boolean
     */
    @Override
    public boolean isUserLocked(String userName) {
        return !isAnonymousUser(userName) &&
                userGroupStoreService.isUserLocked(userName);
    }

    @Override
    public boolean isUserDisabled(String userName) {
        return userGroupStoreService.isUserDisabled(userName);
    }

    /**
     * @throws LoginDisabledException if user has performed
     *                                incorrect login in the past and now should wait before performing another login
     *                                attempt
     */
    @Override
    public void ensureLoginShouldNotBeDelayed(@Nonnull String userName) {
        if (delegateTempLoginToAccess()) {
            return;
        }
        if (!isAnonymousUser(userName)) {
            log.debug("Ensuring that user should not be blocked");
            long now = System.currentTimeMillis();
            long nextLogin = userLockInMemoryService.getNextLoginTime(userName);
            List<Long> incorrectLoginTimestamps = unknownUsersCache.getIfPresent(userName);
            if (nextLogin < 0 && incorrectLoginTimestamps != null) {
                // check frontend cache for unknown users
                Long lastAccessTimeMillis = incorrectLoginTimestamps.get(incorrectLoginTimestamps.size() - 1);
                nextLogin = userLockInMemoryService.getNextLoginTime(incorrectLoginTimestamps.size(),
                        lastAccessTimeMillis, userName);
            }

            if (nextLogin > now) {
                log.warn("User {} is blocked due to incorrect login attempts till {}", userName, nextLogin);
                throw LoginDisabledException.userLocked(nextLogin);
            }
        }
    }

    /**
     * Performs check whther given user is anonymous
     *
     * @return true/false
     */
    @VisibleForTesting
    protected boolean isAnonymousUser(String userName) {
        return UserInfo.isAnonymous(userName);
    }

    @Override
    public boolean createUser(MutableUserInfo user) {
        return createUser(user, false);
    }

    @Override
    public boolean createUserWithProperties(MutableUserInfo user) {
        return createUser(user, true);
    }

    private boolean createUser(MutableUserInfo user, boolean addUserProperties) {
        user.setUsername(user.getUsername().toLowerCase());
        flagIfPasswordSetByAnotherAdministrator(user, null);
        try {
            boolean userCreated = userGroupStoreService.createUserWithProperties(user, addUserProperties);
            if (userCreated) {
                interceptors.onUserAdd(user.getUsername());
            }
            return userCreated;
        } catch (AccessClientHttpException e) {
            List<MessageModel> errors = e.getErrorsModel().getErrors();
            if (CollectionUtils.notNullOrEmpty(errors)) {
                String error = errors.get(0).getMessage();
                log.error("Unable to create user: '{}', {}", user.getUsername(), error);
                throw new IllegalArgumentException(error, e);
            }
            log.error("Unable to create user: '{}'", user.getUsername(), e);
            return false;
        }
    }

    @Override
    public boolean createUserWithNoUIAccess(MutableUserInfo user) {
        if (createUser(user)) {
            userGroupStoreService.addUserProperty(user.getUsername(), UI_VIEW_BLOCKED_USER_PROP, "true");
            return true;
        }
        return false;
    }

    /**
     * Changes user password. The holder must supply the current password, and the new one must differ from it. On
     * success the must-change-password flag ({@link UserInfo#isMustChangePassword()}) is cleared, so this is how a
     * holder of a temporary password regains full use of the account.
     *
     * @param userName     user name
     * @param oldPassword  old password
     * @param newPassword1 new password
     * @param newPassword2 replication of new password
     */
    @Override
    public void changePassword(String userName, String oldPassword, String newPassword1, String newPassword2)
            throws PasswordChangeException {
        try {
            UserInfo user = findUser(userName);

            // perform user account validity check
            ensureUserIsNotLocked(userName);
            ensureLoginShouldNotBeDelayed(userName);

            if (isOldPasswordValid(findUser(userName), oldPassword, newPassword1, newPassword2)) {
                changePasswordWithoutValidation(user, newPassword1);
                if (user.isMustChangePassword()) {
                    // isOldPasswordValid refused a new password equal to the temporary one
                    setMustChangePassword(user.getUsername(), false);
                }
            }

            if (ADMIN_DEFAULT_USERNAME.equals(userName)) {
                accessService.invalidateAdminUsingOldDefaultPasswordCache();
                globalStateProviderService.invalidateCache();
            }
        } catch (RuntimeException e) {
            log.error(e.getMessage());
            log.debug(ERR_CAUSE, e);
            throw new PasswordChangeException("Incorrect username/password or the user might be locked");
        } catch (Exception e) {
            log.error(e.getMessage());
            log.debug(ERR_CAUSE, e);
            throw new PasswordChangeException("Changing password has failed, see logs for more details");
        }
    }

    /**
     * Changes user password without validating old password. When the current user is an administrator setting
     * another user's password, the must-change-password flag is set on that user. It is never cleared here.
     *
     * @param user        user to change password to
     * @param newPassword new password
     */
    @Override
    public void changePasswordWithoutValidation(@Nonnull UserInfo user, String newPassword)
            throws PasswordChangeException {
        try {
            if (user.isPasswordDisabled()) {
                throw new IllegalStateException("The specified user is not permitted to reset his password.");
            }

            userGroupStoreService.changePassword(user, generateSaltedPassword(newPassword), newPassword);
            if (MustChangePasswordRules.setsFlag(currentUsername(), isAdmin(), user.getUsername(), newPassword,
                    null, false)) {
                userGroupStoreService.setMustChangePassword(user.getUsername(), true);
            }
            invalidateAuthCacheEntries(user.getUsername());
            log.info("Password for user: '{}' has been successfully changed", user.getUsername());
        } catch (Exception e) {
            log.error(e.getMessage());
            log.debug(ERR_CAUSE, e);
            throw new PasswordChangeException("", e);
        }
    }

    /**
     * Checks whether old password is valid
     *
     * @param user         a user to getAndCheckSidPermissions password details for
     * @param oldPassword  old password
     * @param newPassword1 new password
     * @param newPassword2 replication of new password
     * @return true/false
     */
    private boolean isOldPasswordValid(UserInfo user, String oldPassword, String newPassword1, String newPassword2) {
        if (!userPassAuthenticationProvider.canUserLogin(user.getUsername(), oldPassword)) {
            throw new PasswordChangeException("Incorrect username/password");
        }

        if ((newPassword1 != null && !newPassword1.equals(newPassword2)) ||
                (newPassword2 != null && !newPassword2.equals(newPassword1))) {
            throw new PasswordChangeException("New passwords do not match");
        }

        if (oldPassword != null && oldPassword.equals(newPassword1)) {
            throw new PasswordChangeException("New password has to be different from the old one");
        }

        if (newPassword1 == null) {
            throw new PasswordChangeException("New passwords cannot be empty");
        }
        return true;
    }

    /**
     * Updates a user. When the current user is an administrator writing a new password for another user, the
     * must-change-password flag is set on that user. A full update never clears the flag.
     */
    @Override
    public void updateUser(MutableUserInfo user, boolean activateListeners) {
        String userName = user.getUsername().toLowerCase();
        user.setUsername(userName);
        if (MustChangePasswordRules.isWrittenByAnotherAdministrator(currentUsername(), isAdmin(), userName)
                && MustChangePasswordRules.carriesNewPassword(user.getPassword(), null, user.isPasswordDisabled())) {
            UserInfo stored = userGroupStoreService.findUser(userName);
            flagIfPasswordSetByAnotherAdministrator(user, stored != null ? stored.getPassword() : null);
        }
        userGroupStoreService.updateUser(user);
        if (activateListeners) {
            invalidateAuthCacheEntries(user.getUsername());
        }
    }

    @Override
    public void deleteUser(String username) {
        authorizationService.removeAllUserAces(username);
        userGroupStoreService.deleteUser(username);
        interceptors.onUserDelete(username);
        invalidateAuthCacheEntries(username);
    }

    /**
     * Removes the user's cache entries from the non-ui and Docker auth caches
     *
     * @param userName user to remove
     */
    private void invalidateAuthCacheEntries(String userName) {
        for (SecurityListener listener : securityListeners) {
            listener.onUserUpdate(userName);
        }
        //Also invalidate docker auth cache entries
    }

    /**
     * Removes cache entries from the non-ui and Docker auth caches for all users
     */
    private void invalidateAuthCacheEntriesForAllUsers() {
        //Clear the entire non-ui cache
        clearSecurityListeners();
    }

    @Override
    public Group updateGroup(MutableGroupInfo groupInfo) {
        Group updatedGroup = userGroupStoreService.updateGroup(groupInfo);
        interceptors.onGroupUpdate(groupInfo.getGroupName(), groupInfo.getRealm());
        return updatedGroup;
    }

    @Override
    public Group createGroup(MutableGroupInfo groupInfo) {
        Group createdGroup = userGroupStoreService.createGroup(groupInfo);
        interceptors.onGroupAdd(groupInfo.getGroupName(), groupInfo.getRealm());
        return createdGroup;
    }

    @Override
    public void updateGroupUsers(MutableGroupInfo group, List<String> usersInGroup) {
        List<String> oldGroupUsers = findUsersInGroup(group.getGroupName());
        Set<String> usersToRemove = new HashSet<>(oldGroupUsers);
        Set<String> usersToAdd = new HashSet<>(usersInGroup);

        usersToRemove.removeAll(usersInGroup); // users to remove
        usersToAdd.removeAll(oldGroupUsers); // users to add

        // remove users from groups
        removePrevGroupUsers(group, usersToRemove);

        // add users to group
        addUserToGroup(usersToAdd, group.getGroupName());
    }

    @Override
    public void deleteGroup(String groupName) {
        authorizationService.removeAllGroupAces(groupName);
        if (userGroupStoreService.deleteGroup(groupName)) {
            interceptors.onGroupDelete(groupName);
        }
    }

    @Override
    public List<GroupInfo> getAllGroups() {
        return new ArrayList<>(groupCachingRepo.fetchAllGroups().values());
    }

    @Override
    public List<String> getAllAdminGroupsNames() {
        return new ArrayList<>(groupCachingRepo.getGroupsByFilter(GroupFilter.ADMIN).keySet());
    }

    @Override
    public Map<String, GroupInfo> getAllExternalGroups() {
        return groupCachingRepo.getGroupsByFilter(GroupFilter.EXTERNAL);
    }

    @Override
    public Map<String, GroupInfo> getAllGroupsByGroupNames(List<String> groupNames) {
        return groupCachingRepo.getGroupInfosByNames(groupNames).entrySet()
                .stream()
                .filter(e -> e.getValue().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    @Override
    public List<GroupInfo> getNewUserDefaultGroups() {
        return userGroupStoreService.getNewUserDefaultGroups();
    }

    @Override
    public List<GroupInfo> getInternalGroups() {
        return userGroupStoreService.getInternalGroups();
    }

    @Override
    public Set<String> getNewUserDefaultGroupsNames() {
        return userGroupStoreService.getNewUserDefaultGroupsNames();
    }

    @Override
    public void addUsersToGroup(String groupName, Collection<String> usernames) {
        userGroupStoreService.addUsersToGroup(groupName, usernames);
        interceptors.onAddUsersToGroup(groupName, usernames);
        for (String username : usernames) {
            invalidateAuthCacheEntries(username);
        }
    }

    @Override
    public void removeUsersFromGroup(String groupName, Collection<String> usernames) {
        userGroupStoreService.removeUsersFromGroup(groupName, usernames);
        interceptors.onRemoveUsersFromGroup(groupName, usernames);
        for (String username : usernames) {
            invalidateAuthCacheEntries(username);
        }
    }

    @Override
    public List<String> findUsersInGroup(String groupName) {
        return userGroupStoreService.findUsersInGroup(groupName);
    }

    @Override
    public String resetPassword(String userName, String remoteAddress) {
        validateResetPasswordAttempt(remoteAddress);
        UserInfo userInfo = null;
        try {
            userInfo = findUser(userName);
        } catch (UsernameNotFoundException e) {
            //Alert in the log when trying to reset a password of an unknown user
            log.warn("An attempt has been made to reset a password of unknown user: '{}'", userName);
        }

        //If the user is found, and has an email address
        if (userInfo != null && !StringUtils.isEmpty(userInfo.getEmail())) {
            //If the user hasn't got sufficient permissions
            if (!userInfo.isUpdatableProfile() || userInfo.isPasswordDisabled()) {
                log.warn("The specified user is not permitted to reset his password.");
            } else {
                //Generate and send a password reset key
                try {
                    generatePasswordResetKey(userName, remoteAddress);
                } catch (EmailException ex) {
                    String message = ex.getMessage() + " Please contact your administrator.";
                    throw new RuntimeException(message);
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage());
                }
            }
        } else if (userInfo != null) {
            log.warn("Reset password e-mail was not really sent, e-mail not set for user: '{}'", userName);
        }
        return "If you have permissions to reset a password, we will email you a code to reset your password. " +
                "Copy the code to the code verification page.";
    }

    /**
     * Validates a reset password attempt from the given remote address.
     *
     * @throws ResetPasswordException on an invalid attempt (e.g. too many/frequent attempts)
     */
    void validateResetPasswordAttempt(String remoteAddress) {
        PasswordResetPolicy policy = getPasswordResetPolicy(centralConfig.getDescriptor());
        if (policy.isEnabled()) {
            try {
                List<Long> attempts = resetPasswordAttemptsBySourceCache.get(remoteAddress, Lists::newArrayList);
                if (!attempts.isEmpty()) {
                    if (attempts.size() >= policy.getMaxAttemptsPerAddress()) {
                        throw ResetPasswordException.tooManyAttempts(remoteAddress);
                    }
                    if (System.currentTimeMillis() - attempts.get(attempts.size() - 1) <
                            MIN_DELAY_BETWEEN_FORGOT_PASSWORD_ATTEMPTS_PER_SOURCE) {
                        throw ResetPasswordException.tooFrequentAttempts(remoteAddress);
                    }
                }
                attempts.add(System.currentTimeMillis());
            } catch (ExecutionException e) {
                String errorMessage =
                        "Unexpected error while verifying legitimacy of password reset requested from remote address " +
                                remoteAddress;
                log.error(errorMessage, e);
                throw new RuntimeException(errorMessage, e);
            }
        }
    }

    @Override
    public UserInfo findOrCreateExternalAuthUser(String userName, boolean transientUser) {
        return findOrCreateExternalUser(userName, transientUser, false);
    }

    @Override
    public UserInfo findOrCreateExternalAuthUser(String userName, boolean transientUser, boolean updateProfile) {
        return findOrCreateExternalUser(userName, transientUser, updateProfile);
    }

    /**
     * find or create external user in db
     *
     * @param userName      - user name to find
     * @param transientUser - if true ,  user mark as transient (not created in db)
     * @param updateProfile - if true ,  user will be able to update it own profile
     * @return {@link UserInfo}
     */
    private UserInfo findOrCreateExternalUser(String userName, boolean transientUser, boolean updateProfile) {
        UserInfo userInfo;
        try {
            userInfo = findUser(userName.toLowerCase());
        } catch (UsernameNotFoundException nfe) {
            try {
                userInfo = autoCreateUser(userName, transientUser, updateProfile);
            } catch (ValidationException ve) {
                log.error("Auto-Creation of '" + userName + "' has failed, " + ve.getMessage());
                throw new InvalidNameException(userName, ve.getMessage(), ve.getIndex());
            }
        }
        return userInfo;
    }

    /**
     * remove group users before update
     *
     * @param group - group data
     */
    private void removePrevGroupUsers(MutableGroupInfo group, Collection<String> usersToRemove) {
        if (usersToRemove != null && !usersToRemove.isEmpty()) {
            removeUsersFromGroup(group.getGroupName(), usersToRemove);
        }
    }

    /**
     * @param users     - user list to be added to group
     * @param groupName - group name
     */
    private void addUserToGroup(Collection<String> users, String groupName) {
        if (users != null && !users.isEmpty()) {
            addUsersToGroup(groupName, users);
        }
    }

    /**
     * Auto create user
     *
     * @return {@link UserInfo}
     * @throws ValidationException if userName is invalid
     */
    private UserInfo autoCreateUser(String userName, boolean transientUser,
            boolean updateProfile) throws ValidationException {
        UserInfo userInfo;
        log.debug("Creating new external user: '{}'", userName);

        // make sure username answer artifactory standards RTFACT-8259
        NameValidator.validate(userName);

        //set All new external users to have a disabled internal password
        UserInfoBuilder userInfoBuilder = new UserInfoBuilder(userName.toLowerCase()).updatableProfile(updateProfile)
                .passwordDisabled(true);
        userInfoBuilder.internalGroups(getNewUserDefaultGroupsNames());
        if (transientUser) {
            userInfoBuilder.transientUser();
        }
        userInfo = userInfoBuilder.build();

        // Save non transient user
        if (!transientUser) {
            boolean success = userGroupStoreService.createUser(userInfo);
            if (!success) {
                log.error("The user: '{}' was not created!", userInfo);
            }
        }
        return userInfo;
    }

    @Override
    @Nullable
    public GroupInfo findGroup(String groupName) {
        return groupCachingRepo.getGroupInfoByName(groupName);
    }

    @Override
    public String createEncryptedPasswordIfNeeded(UserInfo user, String password) {
        if (isPasswordEncryptionEnabled() && StringUtils.isNotBlank(password)) {
            // Checking if the password sent by user is an API key. If so we don't encrypt it, just return it.
            if (EncodingType.ARTIFACTORY_API_KEY.isEncodedByMe(password)) {
                log.debug("Returning API Key as encrypted password.");
                return password;
            }
            // Checking if the password sent by user is a reference token. If so we don't encrypt it, just return it.
            if (accessService.getAccessTokenType(password) == AccessTokenType.REFERENCE_TOKEN) {
                log.debug("Returning Reference Token as encrypted password.");
                return password;
            }
            // Checking if the password sent by user is an access token. If so we don't encrypt it, just return it.
            if (accessService.getAccessTokenType(password) == AccessTokenType.JWT_ACCESS_TOKEN) {
                log.debug("Returning Jwt Access Token as encrypted password.");
                return password;
            }

            EncodedKeyPair encodedKeyPair;
            EncryptionWrapper encryptionWrapper;
            try {
                if (StringUtils.isBlank(user.getPrivateKey())) {
                    encodedKeyPair = createKeyPairForUser(user);
                } else {
                    encodedKeyPair = convertOldKeyFormatIfNeeded(user);
                }
                encryptionWrapper = EncryptionWrapperFactory.createKeyWrapper(null, encodedKeyPair);
                return encryptionWrapper.encryptIfNeeded(password);
            } catch (Exception e) {
                String err = String.format(
                        "Error inferring keypair for user: '%s': %s generating a new keypair if called from profile " +
                                "page.",
                        user.getUsername(), e.getMessage());
                if (log.isDebugEnabled()) {
                    log.error(err, e);
                } else {
                    log.error(err);
                }
                //Throw PasswordEncryptionFailureException. The UnlockUserProfileService#updateUserInfo should fallback
                //and re-invoke the password
                throw new PasswordEncryptionFailureException("Failed to encrypt password. " +
                        "To retrieve a new encrypted password unlock your user profile page again.");
            }
        }
        return password;
    }

    /**
     * Creates and saves a new keypair for {@param user} using {@param masterWrapper}
     */
    private EncodedKeyPair createKeyPairForUser(UserInfo user) {
        EncodedKeyPair encodedKeyPair;
        log.info("Creating keys for user: '{}'", user.getUsername());
        MutableUserInfo mutableUser = InfoFactoryHolder.get().copyUser(user);
        DecodedKeyPair decodedKeyPair = new DecodedKeyPair(JFrogCryptoHelper.generateKeyPair());
        encodedKeyPair = new EncodedKeyPair(decodedKeyPair, null);
        saveEncodedKeyPairInUser(mutableUser, encodedKeyPair);
        return encodedKeyPair;
    }

    /**
     * Converts old format keypair to new format for {@param user} using {@param masterWrapper} if required,
     * May generate a new keypair if old format encoding is used which we don't support any more.
     */
    private EncodedKeyPair convertOldKeyFormatIfNeeded(UserInfo user) {
        EncodedKeyPair encodedKeyPair;
        encodedKeyPair = new EncodedKeyPair(user.getPrivateKey(), user.getPublicKey());
        EncodedKeyPair toSaveEncodedKeyPair = encodedKeyPair
                .toSaveEncodedKeyPair(ArtifactoryHome.get().getArtifactoryEncryptionWrapper());
        if (toSaveEncodedKeyPair != null) {
            log.info("Reformatting keys for user: '{}'", user.getUsername());
            MutableUserInfo mutableUser = InfoFactoryHolder.get().copyUser(user);
            saveEncodedKeyPairInUser(mutableUser, toSaveEncodedKeyPair);
            encodedKeyPair = toSaveEncodedKeyPair;
        }
        return encodedKeyPair;
    }

    private void saveEncodedKeyPairInUser(MutableUserInfo mutableUser, EncodedKeyPair encodedKeyPair) {
        mutableUser.setPrivateKey(encodedKeyPair.getEncodedPrivateKey());
        mutableUser.setPublicKey(encodedKeyPair.getEncodedPublicKey());
        updateUser(mutableUser, false);
    }

    /**
     * Generates a password recovery key for the specified user and send it by mail
     *
     * @param username      User to rest his password
     * @param remoteAddress The IP of the client that sent the request
     * @throws IllegalArgumentException in case no user found for provided username
     * @throws AuthorizationException   in case user is not allowed to update profile
     * @throws EmailException           in case of failure while sending password reset email
     */
    @Override
    public void generatePasswordResetKey(String username, String remoteAddress) throws IOException {
        UserInfo userInfo;
        try {
            userInfo = findUser(username);
        } catch (UsernameNotFoundException e) {
            //If can't find user
            throw new IllegalArgumentException("Could not find specified username.", e);
        }

        //If user has valid email
        if (!StringUtils.isEmpty(userInfo.getEmail())) {
            if (!userInfo.isUpdatableProfile() || userInfo.isPasswordDisabled()) {
                //If user is not allowed to update his profile
                throw new AuthorizationException("User is not permitted to reset his password.");
            }

            //Build key by UUID + current time millis + client ip -> encoded in B64
            UUID uuid = UUID.randomUUID();
            String passwordKey = uuid.toString() + ":" + System.currentTimeMillis() + ":" + remoteAddress;
            byte[] encodedKey = Base64.encodeBase64URLSafe(passwordKey.getBytes(Charsets.UTF_8));
            String encodedKeyString = new String(encodedKey, Charsets.UTF_8);

            userGroupStoreService.changeUserGenPassword(userInfo.getUsername(), encodedKeyString);

            //If there are any admins with valid email addresses, add them to the list that the message will contain
            //String adminList = getAdminListBlock(userInfo);
            InputStream stream = null;
            try {
                //Get message body from properties and substitute variables
                stream = getClass().getResourceAsStream("/org/artifactory/email/messages/resetPassword.properties");
                ResourceBundle resourceBundle = new PropertyResourceBundle(stream);
                String body = resourceBundle.getString("body");
                body = MessageFormat
                        .format(body, username, encodedKeyString,
                                ConstantValues.generatedPasswordExpirationPeriodMinutes.getLong());
                mailService.sendMail(new String[] {userInfo.getEmail()}, "Reset password request", body);
            } catch (EmailException e) {
                log.error("Error while resetting password for user: '{}'.", username, e);
                throw e;
            } finally {
                IOUtils.closeQuietly(stream);
            }
            log.info("The user: '{}' has been sent a password reset message by mail.", username);
        }
    }

    @Override
    public SerializablePair<Date, String> getPasswordResetKeyInfo(String username) {
        UserInfo userInfo = findUser(username);
        String passwordKey = userInfo.getGenPasswordKey();
        if (StringUtils.isEmpty(passwordKey)) {
            return null;
        }

        byte[] decodedKey = Base64.decodeBase64(passwordKey.getBytes(Charsets.UTF_8));
        String decodedKeyString = new String(decodedKey, Charsets.UTF_8);
        String[] splitKey = decodedKeyString.split(":");

        //Key must be in 3 parts
        if (splitKey.length < 3) {
            throw new IllegalArgumentException("Password reset key must contain 3 parts - 'UUID:Date:IP'");
        }

        String time = splitKey[1];
        String ip = splitKey[2];

        Date date = new Date(Long.parseLong(time));

        return new SerializablePair<>(date, ip);
    }

    @Override
    public SerializablePair<String, Long> getUserLastLoginInfo(String username) {
        UserInfo userInfo;
        try {
            userInfo = findUser(username);
        } catch (UsernameNotFoundException e) {
            //If can't find user (might be transient user)
            log.trace("Could not retrieve last login info for user: '{}'.", username);
            return null;
        }

        SerializablePair<String, Long> pair = null;
        String lastLoginClientIp = userInfo.getLastLoginClientIp();
        long lastLoginTimeMillis = userInfo.getLastLoginTimeMillis();
        if (!StringUtils.isEmpty(lastLoginClientIp) && (lastLoginTimeMillis != 0)) {
            pair = new SerializablePair<>(lastLoginClientIp, lastLoginTimeMillis);
        }
        return pair;
    }

    @Override
    public boolean isHasPriorLogin() {
        return userGroupStoreService.getAllUsers(true, false, true)
                .stream()
                .anyMatch(user -> user.getLastLoginTimeMillis() != 0);
    }

    @Override
    public void updateUserLastLogin(String username, long loginTimeMillis, String clientIp) {
        long lastLoginBufferTimeSecs = ConstantValues.userLastAccessUpdatesResolutionSecs.getLong();
        if (lastLoginBufferTimeSecs < 1) {
            log.debug("Skipping the update of the last login time for the user: '{}': tracking is disabled.", username);
            return;
        }
        long lastLoginBufferTimeMillis = TimeUnit.SECONDS.toMillis(lastLoginBufferTimeSecs);
        UserInfo userInfo = userGroupStoreService.findUser(username);
        if (userInfo == null) {
            // user not found (might be a transient user)
            log.trace("Could not update non-exiting user: {}'.", username);
            return;
        }
        long timeSinceLastLogin = loginTimeMillis - userInfo.getLastLoginTimeMillis();
        if (timeSinceLastLogin < lastLoginBufferTimeMillis) {
            log.debug("Skipping the update of the last login time for the user: '{}': " +
                    "was updated less than {} seconds ago.", username, lastLoginBufferTimeSecs);
            return;
        }

        userGroupStoreService.updateUserLastLogin(username, loginTimeMillis, clientIp);
    }

    /**
     * Updates user last access time, if user is not exist in artifactory
     * keeps track of it in volatile cache
     *
     * @param userName         Name of user that performed an action
     * @param clientIp         The IP of the client that has accessed
     * @param accessTimeMillis The time of access
     */
    @Override
    public void updateUserLastAccess(String userName, String clientIp, long accessTimeMillis) {
        log.debug("Updating access details for user, time={}, ip={}", accessTimeMillis, clientIp);
        userLockInMemoryService.updateUserAccess(userName, isUserLockPolicyEnabled(), accessTimeMillis);
    }

    @Override
    public boolean isHttpSsoProxied() {
        return accessService.getAccessAuthSettingsClient()
                .getHttpSsoSetting().map(HttpSsoSetting::isHttpSsoProxied).orElse(false);
    }

    @Override
    public boolean isNoHttpSsoAutoUserCreation() {
        return accessService.getAccessAuthSettingsClient()
                .getHttpSsoSetting().map(setting -> !setting.isAutoCreateUser()).orElse(false);
    }

    @Override
    public String getHttpSsoRemoteUserRequestVariable() {
        return accessService.getAccessAuthSettingsClient()
                .getHttpSsoSetting().map(HttpSsoSetting::getRemoteUserRequestVariable).orElse(null);
    }

    @Override
    public boolean isDisableInternalPassword() {
        UserInfo simpleUser = currentUser();
        return (simpleUser == null);
    }

    @Override
    public String currentUserEncryptedPassword() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if ((authentication != null) && authentication.isAuthenticated()) {
            String authUsername = ((UserDetails) authentication.getPrincipal()).getUsername();
            String password = (String) authentication.getCredentials();
            if (StringUtils.isNotBlank(password)) {
                UserInfo user = userGroupStoreService.findUser(authUsername);
                if (user == null) {
                    log.warn("Can't return the encrypted password of the unfound user: '{}'", authUsername);
                } else {
                    return createEncryptedPasswordIfNeeded(user, password);
                }
            }
        }
        return null;
    }

    @Override
    public boolean isApiKeyAuthentication() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication == null) {
            return false;
        }
        try {
            String password = (String) authentication.getCredentials();
            return CryptoHelper.isApiKey(password);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String currentUserEncryptedPasswordOrApiKey() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return handleAuthenticatedUser(authentication);
        }
        log.warn(authentication == null ? "authentication is null" : "the request is not authenticated");
        return null;
    }

    private String handleAuthenticatedUser(Authentication authentication) {
        String authUsername = ((UserDetails) authentication.getPrincipal()).getUsername();
        UserInfo user = userGroupStoreService.findUser(authUsername);
        String password = (String) authentication.getCredentials();
        if (user == null) {
            return handlePasswordWhenUserInfoNotFound(authUsername, password);
        }
        return retrievePasswordFromAuthenticationContext(user, password, authentication);
    }

    private String retrievePasswordFromAuthenticationContext(UserInfo user,
            String password,
            Authentication authentication) {
        if (StringUtils.isNotBlank(password) && isNotFrontendToken(authentication)) {
            return createEncryptedPasswordIfNeeded(user, password);
        }
        warnForAuthenticationIssue(user, password);
        return null;
    }

    private void warnForAuthenticationIssue(UserInfo user, String password) {
        String logMessage;
        if (user.isExternal()) {
            logMessage = "external user with out valid TokenKeyValue + authUsername" + user;
        } else if (StringUtils.isBlank(password)) {
            logMessage = "password is blank for user " + user;
        } else {
            logMessage = " is not frontend token for user " + user;
        }
        log.warn(logMessage);
    }

    @Nullable
    private String handlePasswordWhenUserInfoNotFound(String authUsername, String password) {
        if (ConstantValues.mvnOverrideSettingsXml.getBoolean() && isOIDCAuthenticatedUser(authUsername)){
            return password;
        }
        log.warn("Can't return the encrypted password of the unfound user: '{}'", authUsername);
        return null;
    }

    private boolean isOIDCAuthenticatedUser(String authUsername) {
        return authUsername != null && authUsername.startsWith("token:");
    }

    private static boolean isNotFrontendToken(Authentication authentication) {
        return !((authentication instanceof AccessTokenAuthentication accessTokenAuthentication) &&
                TokenType.isFrontendImpersonationToken(accessTokenAuthentication.getAccessToken().getIssuer(),
                        accessTokenAuthentication.getAccessToken().getScope()));
    }

    /**
     * If the path being checked belongs to the build-info repo the path being checked needs to be URL decoded, since
     * build names and numbers are specially encoded upon deployment using {@code BuildRepoPathElementsEncoder}
     */
    private String getPathToCheckPermissionFor(RepoPath repoPath) {
        String path = repoPath.getPath();
        if (buildFacade.isBuildInfoRepo(repoPath.getRepoKey())) {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        }
        return path;
    }

    @Override
    public void exportTo(ExportSettings settings) {
        throw new UnsupportedOperationException("Logic moved to access");
    }

    /**
     * @deprecated as access should manage security data
     */
    @Deprecated
    @Override
    public SecurityInfo getSecurityData() {
        return getSecurityData(true);
    }

    /**
     * @deprecated as access should manage security data
     */
    @Deprecated
    @Override
    public SecurityInfo getSecurityData(boolean decryptUserInfoProperties) {
        AolService aolService = ContextHelper.get().beanForType(AolService.class);
        List<UserInfo> users = getAllUsersInternal(true, true, decryptUserInfoProperties)
                .stream()
                .filter(user -> !aolService.isAolAdmin(user))
                .collect(Collectors.toList());
        List<GroupInfo> groups = getAllGroups();
        List<RepoAcl> repoAcls = authorizationService.getAllRepoAcls();
        List<BuildAcl> buildAcls = authorizationService.getAllBuildAcls(false);
        List<ReleaseBundleAcl> releaseBundleAcls = authorizationService.getAllReleaseBundleAcls(false);
        SecurityInfo descriptor = InfoFactoryHolder.get()
                .createSecurityInfo(users, groups, repoAcls, buildAcls, releaseBundleAcls);
        descriptor.setVersion(SecurityVersion.last().name());
        return descriptor;
    }

    @Override
    public void importFrom(ImportSettings settings) {
        if (!ImportValidationUtils.shouldImport(settings, "security")) {
            return;
        }
        MutableStatusHolder status = settings.getStatusHolder();
        status.status("Importing security...", log);
        invalidateAclAndGroupCaches();
        importSecurityXml(settings, status);
    }

    private void importSecurityXml(ImportSettings settings, MutableStatusHolder status) {
        //Import the new security definitions
        File baseDir = settings.getBaseDir();
        // First check for security.xml file
        File securityXmlFile = new File(baseDir, FILE_NAME);
        if (!securityXmlFile.exists()) {
            log.info("Security file {} does not exist no import of security will" +
                    " be done (access import should handle security for new imports)", securityXmlFile);
            return;
        }
        SecurityInfo securityInfo;
        try {
            securityInfo = new SecurityInfoReader().read(securityXmlFile);
        } catch (Exception e) {
            status.warn("Could not read security file", log);
            return;
        }
        SecurityService me = InternalContextHelper.get().beanForType(SecurityService.class);
        me.importSecurityData(securityInfo);
    }

    @Override
    public boolean shouldHideUnauthorizedResources() {
        return centralConfig.getDescriptor().getSecurity().isHideUnauthorizedResources();
    }

    private void invalidateAclAndGroupCaches() {
        publisher.publishEvent(new InvalidateCacheEvent(this, CacheType.ACL));
        publisher.publishEvent(new InvalidateCacheEvent(this, CacheType.GROUPS));
    }

    @Override
    public void importSecurityData(String securityXml) {
        importSecurityData(new SecurityInfoReader().read(securityXml), false);
    }

    @Override
    public void importSecurityData(String securityXml, boolean override) {
        importSecurityData(new SecurityInfoReader().read(securityXml), override);
    }

    @Override
    public void importSecurityData(SecurityInfo securityInfo) {
        importSecurityData(securityInfo, false);
    }

    private void importSecurityData(SecurityInfo securityInfo, boolean override) {
        interceptors.onBeforeSecurityImport(securityInfo);
        clearSecurityListeners();

        try {
            accessService.importSecurityEntities(securityInfo, override);
            decryptAllUserProps();
        } finally {
            invalidateAclAndGroupCaches();
        }
        interceptors.onAfterSecurityImport(securityInfo);
    }

    @Override
    public void decryptAllUserProps() {
        try {
            accessService.getAccessClient().cachedUsers().findUsers().getUsers().forEach(user -> {
                MutableBoolean updated = new MutableBoolean(false);
                UpdateUserRequest updateRequest = UpdateUserRequest.create();
                updateRequest.username(user.getUsername());

                // Converting only custom data that was changed because of cryptographic changes
                // Running through UserMapper to decrypt all relevant data
                UserRequest newUser = UserMapper.toAccessUser(UserMapper.toArtifactoryUser(user), true);

                ofNullable(newUser.getCustomData()).orElseGet(HashMap::new).forEach((propKey, newValue) -> {
                    if ((newValue.isSensitive() || UserPropertiesSearchHelper.hasSearchableSuffix(propKey)) &&
                            !newValue.getValue().equals(user.getCustomData(propKey))) {
                        log.debug("Updating property {} of user: '{}'", propKey, user.getUsername());
                        updateRequest.addCustomData(propKey, newValue.getValue(), newValue.isSensitive());
                        updated.setTrue();
                    }
                });

                if (updated.booleanValue()) {
                    accessService.getAccessClient().cachedUsers().updateUser(updateRequest);
                }
            });
        } catch (Exception e) {
            log.error("Could not decrypt user props/keys, cause: ", e);
            throw new StorageException("Could not decrypt user props/keys, see logs for more details");
        }
    }

    @Override
    public void addListener(SecurityListener listener) {
        securityListeners.add(listener);
    }

    @Override
    public void removeListener(SecurityListener listener) {
        securityListeners.remove(listener);
    }

    @Override
    public void authenticateAsSystem() {
        SecurityContextHolder.getContext().setAuthentication(new SystemAuthenticationToken());
    }

    public void authenticateAsAdmin() {
        if (isAdmin()) {return;}

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AccessTokenAuthentication accessTokenAuthentication = (AccessTokenAuthentication) authentication;

        JwtAccessToken authToken = accessTokenAuthentication.getAccessToken();
        SimpleUser principal = (SimpleUser) accessTokenAuthentication.getPrincipal();
        UserImpl descriptor = new UserImpl(principal.getDescriptor());
        descriptor.setAdmin(true);
        SimpleUser elevatedPrincipal = new SimpleUser(descriptor);
        AccessTokenAuthentication elevatedTokenAuthentication = new AccessTokenAuthentication(authToken,
                elevatedPrincipal, accessTokenAuthentication.getAuthorities());
        elevatedTokenAuthentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(elevatedTokenAuthentication);
    }

    @Override
    public void doAsSystem(@Nonnull Runnable runnable) {
        Authentication originalAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            authenticateAsSystem();
            runnable.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(originalAuthentication);
        }
    }

    @Override
    public <V> V callAsSystem(Callable<V> callable) throws Exception {
        Authentication originalAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            authenticateAsSystem();
            return callable.call();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(originalAuthentication);
        }
    }

    @Override
    public void doAsAdmin(@Nonnull Runnable runnable) {
        Authentication originalAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            authenticateAsAdmin();
            runnable.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(originalAuthentication);
        }
    }

    @Override
    public void nullifyContext() {
        SecurityContextHolder.getContext().setAuthentication(null);
    }

    @Override
    public SaltedPassword generateSaltedPassword(String rawPassword) {
        return generateSaltedPassword(rawPassword, getDefaultSalt());
    }

    @Override
    public SaltedPassword generateSaltedPassword(@Nonnull String rawPassword, @Nullable String salt) {
        return new SaltedPassword(rawPassword, salt);
    }

    @Override
    public String getDefaultSalt() {
        return ConstantValues.defaultSaltValue.getString();
    }

    @Override
    public BasicStatusHolder testLdapConnection(LdapSetting ldapSetting, String username, String password) {
        return ldapService.testLdapConnection(ldapSetting, username, password);
    }

    @Override
    public boolean isPasswordEncryptionEnabled() {
        return accessService.isEncryptionEnabled();
    }

    @Override
    public boolean userPasswordMatches(String passwordToCheck) {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        return authentication != null && passwordToCheck.equals(authentication.getCredentials());
    }

    private void clearSecurityListeners() {
        //Notify security listeners
        securityListeners.forEach(SecurityListener::onClearSecurity);
    }

    /**
     * Retrieves the Async advised instance of the service
     *
     * @return InternalSecurityService - Async advised instance
     */
    private InternalSecurityService getAdvisedMe() {
        return context.beanForType(InternalSecurityService.class);
    }

    /**
     * Makes user password expired
     */
    @Override
    public void expireUserCredentials(String userName) {
        try {
            if (unknownUsersCache.getIfPresent(userName) != null) {
                log.debug("The user: '{}' is registered in unknown users cache, no password to expire ...", userName);
                throw new UsernameNotFoundException(ERR_USER + userName + " does not exist");
            }
            UserInfo user = findUser(userName);
            if (!user.isCredentialsExpired()) {
                userGroupStoreService.expireUserPassword(userName);
                invalidateAuthCacheEntries(userName);
            }
        } catch (StorageException e) {
            throw new PasswordExpireException(
                    "Expiring password for \"" + userName + "\" has failed, " + e.getMessage(), e);
        } catch (UsernameNotFoundException e) {
            log.error(e.getMessage());
            log.debug(ERR_CAUSE, e);
            throw new PasswordExpireException("Expiring password has failed, " + e.getMessage());
        }
    }

    /**
     * Makes user password expired
     */
    @Override
    public void unexpirePassword(String userName) {
        try {
            if (unknownUsersCache.getIfPresent(userName) != null) {
                log.debug("The user: '{}' is registered in unknown users cache, no password to expire ...", userName);
                throw new UsernameNotFoundException(ERR_USER + userName + " does not exist");
            }
            findUser(userName); // todo: [mp] use plain user fetch (rather than heavy groups join)
            userGroupStoreService.revalidatePassword(userName);
        } catch (StorageException e) {
            throw new PasswordExpireException(
                    "Expiring password for \"" + userName + "\" has failed, " + e.getMessage(), e);
        } catch (UsernameNotFoundException e) {
            log.error(e.getMessage());
            log.debug("Cause: {}", e);
            throw new PasswordExpireException("Expiring password has failed, " + e.getMessage());
        }
    }

    /**
     * Makes all users passwords expired
     */
    @Override
    public void expireCredentialsForAllUsers() {
        try {
            userGroupStoreService.expirePasswordForAllUsers();
            //Invalidate all auth caches
            invalidateAuthCacheEntriesForAllUsers();
        } catch (StorageException e) {
            log.debug("Expiring all users credentials have failed, cause: {}", e);
            throw new PasswordExpireException("Expiring all users credentials have failed, see logs for more details");
        }
    }

    /**
     * Makes all users passwords not expired
     */
    @Override
    public void unexpirePasswordForAllUsers() {
        try {
            userGroupStoreService.revalidatePasswordForAllUsers();
        } catch (StorageException e) {
            log.debug("Un-expiring all users credentials have failed, cause: {}", e);
            throw new PasswordExpireException(
                    "Un-expiring all users credentials have failed, see logs for more details");
        }
    }

    /**
     * Fetches users with password is about to expire
     *
     * @return list of users
     */
    @Override
    public Set<PasswordExpiryUser> getUsersWhichPasswordIsAboutToExpire() {
        return userGroupStoreService.getUsersWhichPasswordIsAboutToExpire(
                ConstantValues.passwordDaysToNotifyBeforeExpiry.getInt(), getPasswordExpirationDays());
    }

    /**
     * Marks user.credentialsExpired=True where password has expired
     *
     * @param daysToKeepPassword after what period password should be changed
     */
    @Override
    public void markUsersCredentialsExpired(int daysToKeepPassword) {
        List<String> expiredUsers = userGroupStoreService.markUsersCredentialsExpired(daysToKeepPassword);
        expiredUsers.forEach(this::invalidateAuthCacheEntries);
    }

    /**
     * @return number of days left till password will expire
     * or negative value if password already expired
     * or NULL if password expiration feature is disabled
     */
    @Override
    public Integer getUserPasswordDaysLeft(String userName) {
        Integer daysLeft = null;
        if (isPasswordExpirationPolicyEnabled()) {
            UserInfo user = userGroupStoreService.findUser(userName);
            if (user != null && !user.isAnonymous() && !user.isPasswordDisabled()) {
                Long userPasswordCreationTime = userGroupStoreService.getUserPasswordCreationTime(userName);
                if (userPasswordCreationTime != null) {
                    daysLeft = getDaysLeftUntilPasswordExpires(userPasswordCreationTime);
                } else {
                    log.debug("Password creation time for user: '{}' returned no value", userName);
                }
            }
        }
        return daysLeft;
    }

    private Integer getDaysLeftUntilPasswordExpires(Long userPasswordCreationTime) {
        int daysLeft;
        DateTime created = new DateTime(userPasswordCreationTime.longValue());
        int expiresIn = getPasswordExpirationDays();
        DateTime now = DateTime.now();
        daysLeft = created.plusDays(expiresIn).minusDays(now.getDayOfYear()).getDayOfYear();
        if ((daysLeft == 365 || daysLeft == 366) && created.plusDays(expiresIn).dayOfYear().get() != daysLeft) {
            daysLeft = 0;
        }
        return daysLeft;
    }

    @PostConstruct
    private void initMarkerMigrationHelper() {
        // TODO [U.L] this service should not depend on JdbcHelper. Refactor the marker creation process.
        markerMigrationHelper = MarkerMigrationHelper.createFromArtifactoryHome(ArtifactoryHome.get(),
                ContextHelper.get().beanForType(ArtifactoryJdbcHelper.class));
    }

    @Override
    public boolean isProjectAdmin(String projectKey) {
        if (isAdmin()) {
            return true;
        }
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication)) {
            return false;
        }
        return projectsService.isProjectAdmin(projectKey, getSimpleUser(authentication))
                || isProjectAdminByToken(authentication, projectKey);
    }

    @Override
    public boolean isResourceManager() {
        if (isAdmin()) {
            return true;
        }
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication)) {
            return false;
        }
        return projectsService.isResourceManager(getSimpleUser(authentication));
    }

    @Override
    public boolean isProjectAdmin(RepoPath repoPath) {
        if (isAdmin()) {
            return true;
        }
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication)) {
            return false;
        }
        String projectKey = projectsService.getProjectKeyOfRepository(repoPath.getRepoKey());
        return projectsService.isProjectAdmin(projectKey, getSimpleUser(authentication))
                || isProjectAdminByToken(authentication, projectKey);
    }

    @Override
    public boolean canDoToRepoProjectResource(RepoActionType repoAction, String repoKey, RepoDetailsType repoType) {
        return projectsService.canDoToProjectRepoResource(repoAction, repoKey, repoType);
    }

    @Override
    public boolean canXrayIndexProjectRepoResource(String repoKey) {
        return projectsService.canXrayIndexProjectRepoResource(repoKey);
    }

    @Override
    public boolean isAnyProjectAdmin() {
        if (isAdmin()) {
            return true;
        }
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication)) {
            return false;
        }
        return !projectsService.getAllProjectsManagedByUser().isEmpty()
                || isAnyProjectAdminByToken(authentication);
    }

    private boolean isProjectAdminByToken(Authentication authentication, String projectKey) {
        if (authentication instanceof AccessTokenAuthentication accessTokenAuthentication) {
            if (projectKey == null) {
                return false;
            }
            return projectsService.isProjectAdmin(accessTokenAuthentication.getAccessToken(), projectKey);
        }
        return false;
    }

    private boolean isAnyProjectAdminByToken(Authentication authentication) {
        if (authentication instanceof AccessTokenAuthentication accessTokenAuthentication) {
            return projectsService.isAnyProjectAdmin(accessTokenAuthentication.getAccessToken());
        }
        return false;
    }

    @Override
    public boolean getAndResetForceEnabledBasicAuth() {
        return forceEnabledBasicAuth.getAndSet(false);
    }

    @Override
    public void setForceEnabledBasicAuth(boolean forceEnabledBasicAuth) {
        this.forceEnabledBasicAuth.set(forceEnabledBasicAuth);
    }

    @Override
    public void assertValidAuthInCaseMfaEnabled() {
        if (!isMfaVerified()) {
            return;
        }
        if (isAccessTokenAuthentication()) {
            return;
        }
        throw new ForbiddenException("When multi-factor authentication is enabled only bearer token is accepted");
    }

    @Override
    public void assertUserAllowedChangePassword(String userName) {
        if(ConstantValues.isMfaChangePasswordEnabled.getBoolean()) {
            return;
        }
        if (!isMfaEnabled(userName)) {
            return;
        }
        throw new ForbiddenException(
                "Unable to change user's password due to multi-factor authentication is enabled for the user %s"
                        .formatted(userName));
    }

    @Override
    public void assertUserAllowedToLoginWithMfaEnabled(String userName, boolean uiRequest) {
        if (!ConstantValues.loginMfaCheckEnabled.getBoolean()) {
            return;
        }
        if (uiRequest) {
            return;
        }
        EnabledRealmsConfig enabledRealms = accessService.getAccessClient().config().getEnabledRealms();
        if (isLegacyLoginFlowNotSupportingMfa(enabledRealms)) {
            return;
        }
        if (isAccessTokenAuthentication()) {
            return;
        }
        if (!isMfaEnabled(userName)) {
            return;
        }
        throw new ForbiddenException("When multi-factor authentication is enabled only bearer token is accepted");
    }

    private boolean isAccessTokenAuthentication() {
        return AuthenticationHelper.getAuthentication() instanceof AccessTokenAuthentication;
    }

    private boolean isMfaVerified() {
        UserInfo userInfo = currentUser();
        if (userInfo == null) {
            return false;
        }
        return UserMFAStatus.VERIFY.equals(userInfo.getMfaStatus());
    }

    @VisibleForTesting
    boolean isMfaEnabled(String userName) {
        UserInfo userInfo;
        try {
            userInfo = findUser(userName);
        } catch (UsernameNotFoundException e) {
            return false;
        }
        return userInfo.getMfaStatus() != UserMFAStatus.NONE;
    }

    private void onPermissionsUpdateEvent(GrpcAffectedEntitiesResponse grpcAffectedEntitiesResponse) {
        if (shouldInvalidateGroupsCache(grpcAffectedEntitiesResponse)) {
            log.debug("Received groups invalidation event");
            if (!CollectionUtils.isNullOrEmpty(grpcAffectedEntitiesResponse.getGroupNames())) {
                grpcAffectedEntitiesResponse.getGroupNames()
                        .forEach(groupName -> groupCachingRepo.invalidateGroupsCache(groupName));
            } else {
                groupCachingRepo.invalidateGroupsCache(null);
            }
        }
        invalidateAuthCacheFromPermissionsEvent(grpcAffectedEntitiesResponse);
    }

    /**
     * Evicts authentication cache entries for users affected by an Access permissions event, so that user
     * deletions and permission changes performed on the Access side (Platform UI, Access REST, SCIM) take effect
     * immediately on every node instead of lingering until the cache idle time elapses (RTDEV-95134).
     * <p>
     * Failures are swallowed: this runs on the same callback as the groups-cache invalidation above and must
     * never prevent it.
     */
    private void invalidateAuthCacheFromPermissionsEvent(GrpcAffectedEntitiesResponse grpcAffectedEntitiesResponse) {
        try {
            if (!authCacheImmediateInvalidationEnabled) {
                return;
            }
            if (grpcAffectedEntitiesResponse.isFullInvalidationEvent()) {
                log.debug("Received full invalidation event, clearing all authentication cache entries");
                invalidateAuthCacheEntriesForAllUsers();
            } else if (grpcAffectedEntitiesResponse.isInvalidationEvent() &&
                    !CollectionUtils.isNullOrEmpty(grpcAffectedEntitiesResponse.getUserNames())) {
                log.debug("Received users invalidation event");
                grpcAffectedEntitiesResponse.getUserNames().forEach(this::invalidateAuthCacheEntries);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate auth cache entries from permissions event, cause: {}", e.getMessage());
            log.debug(ERR_CAUSE, e);
        }
    }

    private boolean shouldInvalidateGroupsCache(GrpcAffectedEntitiesResponse grpcAffectedEntitiesResponse) {
        return grpcAffectedEntitiesResponse.isFullInvalidationEvent() ||
                (grpcAffectedEntitiesResponse.isInvalidationEvent() &&
                        !CollectionUtils.isNullOrEmpty(grpcAffectedEntitiesResponse.getGroupNames()));
    }

    /**
     * If realms does not contain 'internal' realm, meaning login flow is legacy and going to Artifactory
     * @param realmsConfig realms to check
     * @return true if realms does not contain 'internal' realm or are null (login is going to Artifactory),
     * false otherwise
     */
    @VisibleForTesting
    boolean isLegacyLoginFlowNotSupportingMfa(EnabledRealmsConfig realmsConfig) {
        if (realmsConfig != null && realmsConfig.getEnabledRealms() != null) {
            return realmsConfig.getEnabledRealms()
                    .stream().noneMatch(DEFAULT_REALM::equalsIgnoreCase);
        }
        return true;
    }
}
