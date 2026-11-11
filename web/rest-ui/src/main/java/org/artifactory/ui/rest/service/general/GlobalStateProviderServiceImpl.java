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

package org.artifactory.ui.rest.service.general;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.federated.FederatedRepoAddon;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.storage.StorageQuotaInfo;
import org.artifactory.api.subscriptions.SubscriptionsService;
import org.artifactory.common.ConstantValues;
import org.artifactory.customer.updates.CustomerUpdatesModel;
import org.artifactory.customer.updates.CustomerUpdatesService;
import org.artifactory.customer.updates.UsageUpdateModel;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.FederationClientService;
import org.artifactory.repo.FederationMigrationStatus;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.EntitlementsEventListener;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.checksum.replication.StorageChecksumReplicationConfig;
import org.artifactory.storage.checksum.replication.StorageChecksumReplicationService;
import org.artifactory.storage.db.conversion.version.v213.V213ConversionFailFunction;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.ui.rest.model.general.GlobalStateModelImpl;
import org.artifactory.ui.rest.service.state.FooterLink;
import org.artifactory.ui.rest.service.state.FooterMessage;
import org.artifactory.ui.rest.service.state.GlobalStateModel;
import org.artifactory.ui.rest.service.state.GlobalStateProviderService;
import org.artifactory.ui.rest.service.state.State;
import org.artifactory.ui.rest.service.utils.state.DecorateUtil;
import org.artifactory.util.AolUtils;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.Files;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.artifactory.ui.rest.service.state.FooterLink.URL_PLACEHOLDER;
import static org.artifactory.ui.rest.service.state.FooterMessage.FooterMessageVisibility.admin;
import static org.artifactory.ui.rest.service.state.FooterMessage.FooterMessageVisibility.isVisible;
import static org.artifactory.ui.rest.service.state.FooterMessage.FooterMessageVisibility.user;

/**
 * @author Gidi Shabat
 */
@Service
class GlobalStateProviderServiceImpl implements GlobalStateProviderService, EntitlementsEventListener {
    private static final Logger log = LoggerFactory.getLogger(GlobalStateProviderServiceImpl.class);
    public static final String UNSTABLE_FED_REPOS_MSG =
            "Please review the repository settings and select \"Push Configuration\" to resynchronize. " +
                    "The Federated repository settings are not synchronized between these repositories: %1$s.";

    public static final String IMPORT_FED_REPOS_MSG =
            "The following Federated repositories were created but are not part of a Federation: %1$s. They can be " +
                    "added by one of the existing members.";
    private static final String MIGRATION_CLI_TOOL_URL =
            "https://jfrog.com/help/r/jfrog-artifactory-documentation/migrate-to-the-artifactory-federation-service";

    public static final String RTFS_MIGRATION_NEEDED_MSG = "The new & improved Artifactory Federation service is " +
            "now available. To migrate your existing Federations to the new service using the JFrog CLI, " +
            "click " + " <a href='" + MIGRATION_CLI_TOOL_URL + "'> here</a> for instructions." ;

    private AddonsManager addonsManager;
    private StorageService storageService;
    private ConfigsService configsService;
    private AccessService accessService;
    private SecurityService securityService;
    private CustomerUpdatesService updatesService;
    private RepositoryConfigService repositoryConfigService;
    private StorageChecksumReplicationService checksumReplicationService;
    private FederationClientService federationClientService;

    private final ReentrantLock lock = new ReentrantLock();
    private long lastUpdateTime = 0;
    private List<FooterMessage> footerMessagesCache = Lists.newArrayList();
    private State stateCache;
    private String footerMessageRawJson = null;
    private long lastFooterMessageFileChecked;

    @Override
    public void onLicenseLoaded() {
        invalidateCache();
    }

    @Override
    public void invalidateCache() {
        lastUpdateTime = 0;
        log.debug("Global messages cache was invalidated");
    }

    @Override
    public GlobalStateModel getGlobalStateAndMessages() {
        GlobalStateModel globalStateModel = new GlobalStateModelImpl();
        boolean admin = securityService.isAdmin();
        boolean notAnonymous = !securityService.isAnonymous();
        // Try to update the cache if needed
        triggerCacheUpdateProcessIfNeeded(addonsManager, storageService, configsService, accessService,
                repositoryConfigService);
        // update response header with message in cache
        globalStateModel.setMessages(this.footerMessagesCache.stream()
                .filter(message -> isVisible(message.getVisibility(), admin, notAnonymous))
                .collect(Collectors.toList()));
        globalStateModel.setState(stateCache);
        return globalStateModel;
    }

    private void triggerCacheUpdateProcessIfNeeded(AddonsManager addonsManager, StorageService storageService,
            ConfigsService configsService, AccessService accessService,
            RepositoryConfigService repositoryConfigService) {
        long currentTime = System.currentTimeMillis();
        // update the cache every 10 seconds
        int maxCacheAge = ConstantValues.globalMessageCacheAgeSecs.getInt();
        if (currentTime - lastUpdateTime > TimeUnit.SECONDS.toMillis(maxCacheAge)) {
            // Only one thread is allowed to update the cache
            // all the other requests will use the old cache value
            boolean acquireLock = lock.tryLock();
            try {
                if (acquireLock) {
                    List<FooterMessage> list = Lists.newArrayList();
                    decorateHeadersWithLicenseNotInstalled(list, addonsManager);
                    decorateHeadersWithNoIdentityMessage(list, addonsManager);
                    decorateHeadersWithUnsupportedUrls(list, addonsManager);
                    decorateHeaderWithQuotaMessage(list, storageService);
                    decorateHeadersWithXrayStatus(list, addonsManager);
                    decorateWithFreeTierMessages(list);
                    decorateWithCustomMessage(list);
                    decorateWithPsqlIndexMismatch(list, configsService);
                    decorateNeedToChangeAdminPassword(list, accessService);
                    decorateJcrAndEulaNotSigned(list, addonsManager);
                    decorateWithDockerhubCredentialsRecommendation(list, repositoryConfigService);
                    decorateNeedToEnableBasicAuth(list);
                    populateState();
                    decorateFederatedRepositoriesMessages(list, addonsManager);
                    decorateChecksumDeployMessages(list);
                    decorateRtfsMigrationNeeded(list);
                    // update the cache and the last update time
                    lastUpdateTime = currentTime;
                    footerMessagesCache = list;
                }
            } finally {
                if (acquireLock) {
                    lock.unlock();
                }
            }
        }
    }

    private void decorateRtfsMigrationNeeded(List<FooterMessage> list) {
        if (AolUtils.isAol()) {
            return;
        }
        boolean migrationCompleted = federationClientService.hasMigrationStateReached(
                FederationMigrationStatus.MIGRATION_COMPLETED);

        boolean rtfsExistsInTopology = federationClientService.federationNodeExistsInTopology();

        if (!migrationCompleted && rtfsExistsInTopology) {
            list.add(FooterMessage.createWarning(RTFS_MIGRATION_NEEDED_MSG, List.of(), admin));
        }

    }

    private void decorateWithDockerhubCredentialsRecommendation(List<FooterMessage> list,
            RepositoryConfigService repositoryConfigService) {

        //Skip dockerhub credentials recommendation for cloud customer
        if (AolUtils.isAol()) {
            return;
        }
        List<RemoteRepoConfig> reposWithMissingCredentials = repositoryConfigService.getRepoConfigsByType(
                        RepoDetailsType.REMOTE).stream()
                .map(RemoteRepoConfig.class::cast)
                .filter(this::isDockerHubRepo)
                .filter(this::isNoUsernameDefined)
                .collect(Collectors.toList());

        if (!reposWithMissingCredentials.isEmpty()) {
            String message =
                    "Please note, it is recommended to set a DockerHub account on your Docker remote repositories to " +
                            "work against Docker Hub. List of relevant repositories: " +
                            Joiner.on(", ").join(reposWithMissingCredentials) + ". For more information " +
                            dockerRateLimitingPage();
            FooterMessage footerMessage = FooterMessage
                    .createDismissibleError("Admin Notice", message, List.of(),
                            FooterMessage.FooterMessageVisibility.admin);
            list.add(footerMessage);
        }
    }

    private String dockerRateLimitingPage() {
        return "<a href='https://www.jfrog.com/confluence/display/JFROG/Docker+Registry#DockerRegistry" +
                "-RemoteDockerRepositories'>" +
                "click here" + "</a>";
    }

    private boolean isDockerHubRepo(RemoteRepoConfig remoteRepoConfig) {
        return remoteRepoConfig.getPackageType().isDockerGroup() &&
                remoteRepoConfig.getRepoTypeConfig().getUrl().contains("registry-1.docker.io");
    }

    private boolean isNoUsernameDefined(RemoteRepoConfig remoteRepoConfig) {
        return StringUtils.isBlank(remoteRepoConfig.getRepoTypeConfig().getUsername());
    }

    private void populateState() {
        SubscriptionType subscriptionTypeEnum =
                ContextHelper.get().beanForType(SubscriptionsService.class).getSubscriptionType();
        stateCache = new State(subscriptionTypeEnum);
    }

    /**
     * when using PostgreSQL, in some scenarios (i.e. upgrade of existing env), it might be that we have large
     * properties in the node_props that causing a conversion of node_props index to fail due to props too large. This
     * method should decorate and notify admin user that a manual operation of index trim is required, and re-run of
     * the index conversion is required as well.
     */
    private void decorateWithPsqlIndexMismatch(List<FooterMessage> list, ConfigsService configsService) {
        if (hasPostgresIncompatibleIndex(configsService)) {
            String message =
                    "An incompatible index has been found for the Artifactory ‘node_props’ database table. This may " +
                            "be caused by certain property values that exceed the allowed length limit, causing " +
                            "creation of the database index to fail during a version upgrade. " +
                            "For instructions on how to fix this error " + URL_PLACEHOLDER;
            FooterMessage psqlIndexWarn = FooterMessage.createError(message, List.of(psqlIndexMismatchLink()),
                    FooterMessage.FooterMessageVisibility.admin);
            list.add(psqlIndexWarn);
        }
    }

    private void decorateNeedToChangeAdminPassword(List<FooterMessage> list, AccessService accessService) {
        if (accessService.isAdminUsingOldDefaultPassword()) {
            String message =
                    "Your Artifactory server is configured with the default admin credentials." +
                            " It is highly recommended to change the default admin password." +
                            " You can change the password from the " + URL_PLACEHOLDER +
                            ".";
            FooterMessage footerMessage = FooterMessage
                    .createDismissibleError("Admin Notice", message, List.of(changeDefaultAdminPassword()),
                            FooterMessage.FooterMessageVisibility.admin);
            list.add(footerMessage);
        }
    }

    private void decorateNeedToEnableBasicAuth(List<FooterMessage> list) {
        if (securityService.getAndResetForceEnabledBasicAuth()) {
            String message =
                    "Before disabling the basic authentication method, a few necessary steps, such as creating an " +
                            "internal group granted with Admin permissions need to be taken to prevent a lock on " +
                            "your instance. " + URL_PLACEHOLDER + " before disabling" +
                            ".";
            FooterMessage footerMessage = FooterMessage
                    .createWarning(message, List.of(disableBasicAuthentication()), admin);
            list.add(footerMessage);
        }
    }

    private void decorateHeaderWithQuotaMessage(List<FooterMessage> list, StorageService storageService) {
        StorageQuotaInfo storageQuotaInfo = storageService.getStorageQuotaInfo(0);
        if (storageQuotaInfo != null) {
            boolean limitReached = storageQuotaInfo.isLimitReached();
            boolean warningReached = storageQuotaInfo.isWarningLimitReached();
            if (limitReached) {
                String errorMessage = storageQuotaInfo.getErrorMessage();
                list.add(FooterMessage.createError(errorMessage, List.of(), admin));
            } else if (warningReached) {
                String warningMessage = storageQuotaInfo.getWarningMessage();
                list.add(FooterMessage.createWarning(warningMessage, List.of(), admin));
            }
        }
    }

    private boolean hasPostgresIncompatibleIndex(ConfigsService configsService) {
        return configsService.hasConfig(V213ConversionFailFunction.PSQL_NODE_PROPS_INDEX_MISSING_MARKER);
    }

    private FooterLink psqlIndexMismatchLink() {
        return new FooterLink(
                "https://www.jfrog.com/confluence/display/RTF/Troubleshooting#Troubleshooting-RecoveringfromError" +
                        ":AnincompatibleindexhasbeenfoundfortheArtifactory%E2%80%98node_props%E2%80%99databasetable",
                "click here");
    }

    private FooterLink changeDefaultAdminPassword() {
        return new FooterLink(
                "/ui/admin/management/users/admin/edit",
                "User Management page");
    }

    private FooterLink disableBasicAuthentication() {
        return new FooterLink(
                "https://www.jfrog.com/confluence/display/JFROG/General+Security+Settings#GeneralSecuritySettings-" +
                        "DisableBasicAuthenticationMethod",
                "See here");
    }

    private void decorateHeadersWithNoIdentityMessage(List<FooterMessage> list, AddonsManager addonsManager) {
        FooterMessage identityMessage = addonsManager.getIdentityFooterMessage();
        if (identityMessage != null) {
            list.add(identityMessage);
        }
    }

    private void decorateHeadersWithLicenseNotInstalled(List<FooterMessage> list, AddonsManager addonsManager) {
        if (AolUtils.isAol()) {
            log.debug("Found Artifactory cloud version, skipping license check message.");
            return;
        }
        FooterMessage licenseMessage = addonsManager.getLicenseFooterMessage();
        if (licenseMessage != null) {
            list.add(licenseMessage);
        }
    }

    private void decorateJcrAndEulaNotSigned(List<FooterMessage> list, AddonsManager addonsManager) {
        FooterMessage licenseMessage = addonsManager.getEULAFooterMessage();
        if (licenseMessage != null) {
            list.add(licenseMessage);
        }
    }

    private void decorateHeadersWithUnsupportedUrls(List<FooterMessage> list, AddonsManager addonsManager) {
        List<FooterMessage> licenseMessages = addonsManager.getRemoteRepUnsupportedUrls();
        if (CollectionUtils.notNullOrEmpty(licenseMessages)) {
            list.addAll(licenseMessages);
        }
    }

    private void decorateWithFreeTierMessages(List<FooterMessage> list) {
        if (AolUtils.isAol() && ContextHelper.get().beanForType(SubscriptionsService.class).isFreeSubscription()) {
            if (!updatesService.isEmailValidated()) {
                list.add(FooterMessage.
                        createWarning(CustomerUpdatesService.EMAIL_NOT_VALIDATED_MESSAGE, List.of(), admin));
            }
            CustomerUpdatesModel customerUpdates = updatesService.getCustomerUpdates();
            if (customerUpdates == null || customerUpdates.getUsage() == null) {
                return;
            }
            UsageUpdateModel usage = customerUpdates.getUsage();
            if (usage.hasWarningReached()) {
                list.add(FooterMessage.
                        createDismissibleWarning(DecorateUtil.getWarningMessage(usage), List.of(), user));
            }
            if (usage.hasLimitReached()) {
                list.add(FooterMessage.createError(DecorateUtil.getErrorMessage(usage), List.of(), user));
            }
        }
    }

    private void decorateHeadersWithXrayStatus(List<FooterMessage> list, AddonsManager addonsManager) {
        XrayAddon xrayAddon = addonsManager.addonByType(XrayAddon.class);
        if (!xrayAddon.isXrayEnabled()) {
            return;
        }
        if (!xrayAddon.isXrayAliveByHeartbeat()) {
            list.add(FooterMessage.createWarning(
                    "JFrog Xray is unavailable. For details, please check Artifactory's log files.", List.of(), admin));
        } else if (!xrayAddon.isXrayVersionValidByHeartbeat()) {
            list.add(FooterMessage.createWarning("The version of Xray connected is incompatible with this" +
                    " version of Artifactory. Use Xray version 3.0.0 or higher.", List.of(), admin));
        }
    }

    /**
     * Decorates a list of footer messages with any unstable repository warnings or import
     * errors from the federated repository addon.
     *
     * @param list          The list of footer messages to decorate.
     * @param addonsManager The AddonsManager used to retrieve the FederatedRepoAddon.
     */
    private void decorateFederatedRepositoriesMessages(List<FooterMessage> list, AddonsManager addonsManager) {
        FederatedRepoAddon federatedRepoAddon = addonsManager.addonByType(FederatedRepoAddon.class);

        // Check for import errors
        Set<String> importErrors = federatedRepoAddon.getImportConflictsErrors();
        if (importErrors.size() > 0) {
            String repoKeys = StringUtils.join(importErrors.stream().sorted().iterator(), ", ");
            list.add(FooterMessage.createDismissibleWarning(String.format(IMPORT_FED_REPOS_MSG, repoKeys),
                    Collections.emptyList(), admin));
        }

        // Check for unstable repositories
        Set<String> repositoryKeys = federatedRepoAddon.getUnstableRepositories();
        if (repositoryKeys.size() > 0) {
            String repoKeys = StringUtils.join(repositoryKeys.stream().sorted().iterator(), ", ");
            list.add(FooterMessage.createWarning(String.format(UNSTABLE_FED_REPOS_MSG, repoKeys),
                    Collections.emptyList(), admin));
        }
    }

    /**
     * Gives us a chance to insert system messages in SaaS, currently the content is retrieved from artifactory
     * .system.properties
     * but a future version of this feature must support a REST call to a public-facing url we can insert messages in.
     *
     * These messages may be given a time range {@link FooterMessage#getShowFrom()},
     * {@link FooterMessage#getShowUntil()} that will
     * cause this mechanism to decide if they should be shown.
     */
    private void decorateWithCustomMessage(List<FooterMessage> list) {
        checkCustomMessage();
        if (footerMessageRawJson == null) {
            log.trace("Custom UI footer message set to null");
            return;
        }
        List<FooterMessage> messages;
        try {
            messages = JsonUtils.getInstance()
                    .readValueRef(footerMessageRawJson, new TypeReference<>() {
                    });
            if (CollectionUtils.isNullOrEmpty(messages)) {
                log.debug("Had a non-null value for custom message but couldn't deserialize: {}", footerMessageRawJson);
                return;
            }
        } catch (Exception e) {
            log.debug("Couldn't deserialize custom message from json: {}", footerMessageRawJson);
            log.debug("", e);
            return;
        }
        messages.forEach(msg -> addCustomMessageIfNeeded(list, msg));
    }

    private void decorateChecksumDeployMessages(List<FooterMessage> list) {
        StorageChecksumReplicationConfig config = checksumReplicationService.getConfigWithCache();
        if (config.canTrustClientChecksums()) {
            String message = ("Checksum replication flag is enabled with trust until %s. " +
                    "Make sure to disable it once migration is over")
                    .formatted(config.getTrustUntilMsg());
            FooterMessage footerMessage =
                    FooterMessage.createWarning(message, List.of(), FooterMessage.FooterMessageVisibility.admin);
            list.add(footerMessage);
        }
    }

    private void checkCustomMessage() {
        if (lastFooterMessageFileChecked + TimeUnit.HOURS.toMillis(1) <= System.currentTimeMillis()) {
            lastFooterMessageFileChecked = System.currentTimeMillis();
            String footerMessageFileLocation = ConstantValues.uiCustomFooterMessageFile.getString();
            if (footerMessageFileLocation == null) {
                log.trace("Custom UI footer message path set to null");
                return;
            }
            File footerMessageFile = new File(footerMessageFileLocation);
            if (!footerMessageFile.exists()) {
                log.trace("Custom UI footer message set to not existing file");
            }
            try {
                footerMessageRawJson = Files.readFileToString(footerMessageFile);
            } catch (RuntimeException e) {
                log.warn("Unable to read custom UI footer message file at {}", footerMessageFile.getAbsolutePath());
                log.debug("", e);
            }
        }
    }

    /**
     * Filters {@param customMessage} by date range (if given).
     * Will add to message list if date range validated (or no date range)
     */
    private void addCustomMessageIfNeeded(List<FooterMessage> list, FooterMessage customMessage) {
        //We're inside the specified time range, or no range was specified?
        if (customMessage.shouldShowMessage()) {
            //Clear the range prop, no need to output it to ui (and its not ignored since we deserialize the custom
            //messages)
            list.add(customMessage);
        }
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    public void setStorageService(StorageService storageService) {
        this.storageService = storageService;
    }

    @Autowired
    public void setConfigsService(ConfigsService configsService) {
        this.configsService = configsService;
    }

    @Autowired
    public void setAccessService(AccessService accessService) {
        this.accessService = accessService;
    }

    @Autowired
    public void setUpdatesService(CustomerUpdatesService updatesService) {
        this.updatesService = updatesService;
    }

    @Autowired
    public void setRepositoryConfigService(RepositoryConfigService repositoryConfigService) {
        this.repositoryConfigService = repositoryConfigService;
    }

    @Autowired
    public void setStorageChecksumReplicationService(StorageChecksumReplicationService checksumReplicationService) {
        this.checksumReplicationService = checksumReplicationService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setFederationClientService(FederationClientService federationClientService) {
        this.federationClientService = federationClientService;
    }
}
