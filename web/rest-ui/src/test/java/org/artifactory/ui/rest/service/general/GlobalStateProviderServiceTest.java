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
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import jakarta.annotation.Nonnull;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.federated.FederatedRepoAddon;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.storage.StorageQuotaInfo;
import org.artifactory.api.subscriptions.SubscriptionsService;
import org.artifactory.common.ConstantValues;
import org.artifactory.customer.updates.CustomerUpdatesModel;
import org.artifactory.customer.updates.CustomerUpdatesService;
import org.artifactory.customer.updates.UsageUpdateItem;
import org.artifactory.customer.updates.UsageUpdateModel;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.FederationClientService;
import org.artifactory.repo.FederationMigrationStatus;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.checksum.replication.StorageChecksumReplicationConfig;
import org.artifactory.storage.checksum.replication.StorageChecksumReplicationService;
import org.artifactory.storage.db.conversion.version.v213.V213ConversionFailFunction;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.general.GlobalStateModelImpl;
import org.artifactory.ui.rest.service.state.FooterMessage;
import org.artifactory.ui.rest.service.state.FooterMessage.FooterMessageVisibility;
import org.artifactory.util.AolUtils;
import org.glassfish.jersey.internal.util.collection.StringKeyIgnoreCaseMultivaluedMap;
import org.glassfish.jersey.server.ContainerResponse;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.artifactory.ui.rest.service.state.FooterLink.URL_PLACEHOLDER;
import static org.artifactory.ui.rest.service.state.FooterMessage.createError;
import static org.artifactory.ui.rest.service.state.FooterMessage.createWarning;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;


/**
 * @author Dan Feldman
 */
@Test
public class GlobalStateProviderServiceTest extends ArtifactoryHomeBoundTest {
    private static final FooterMessage WARN = createWarning("I'm a teapot!", List.of(), FooterMessageVisibility.admin);
    private static final FooterMessage ERR = createError(
            "I am putting myself to the fullest possible use, which is all I think that any conscious entity can ever" +
                    " hope to do.",
            List.of(), FooterMessageVisibility.admin);

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private StorageService storageService;
    @Mock
    private XrayAddon xrayAddon;
    @Mock
    private FederatedRepoAddon federatedRepoAddon;
    @Mock
    private ContainerResponse containerRes;
    @Mock
    private Response res;
    @Mock
    private ConfigsService configsService;
    @Mock
    private AccessService accessService;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private StorageChecksumReplicationService checksumReplicationService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private CustomerUpdatesService updatesService;
    @Mock
    private SecurityService securityService;
    @Mock
    private FederationClientService clientService;
    private GlobalStateProviderServiceImpl msgProvider;
    private MultivaluedMap<String, Object> responseMetadata;
    private File tempFile;
    private InternalArtifactoryContext context;
    private SubscriptionsService subscriptionsService;
    private AutoCloseable mocks;

    @BeforeClass
    public void setupMocks() throws IOException {
        tempFile = File.createTempFile("msg", ".tmp");
        // Setting the cache to be longer than usual so Jenkins slow slaves won't cry :(
        System.setProperty(ConstantValues.globalMessageCacheAgeSecs.getPropertyName(), String.valueOf(30));
        createContext();
    }

    @BeforeMethod
    public void beforeMethod() {
        mocks = MockitoAnnotations.openMocks(this);
        responseMetadata = new StringKeyIgnoreCaseMultivaluedMap<>();
        when(res.getMetadata()).thenReturn(responseMetadata);
        when(containerRes.getHeaders()).thenReturn(responseMetadata);
        when(accessService.isInitialSetupRequired()).thenReturn(false);
        when(centralConfigService.getDescriptor()).thenReturn(new CentralConfigDescriptorImpl());
        setupCustomerUpdatesService();
        createContext();
    }


    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    //Current default messages use the addon manager and storage service, apart from their own specific tests they
    //should
    //not return their respective messages.
    @BeforeMethod
    public void setupDefaultMockResponses() {
        //Need a new message provider each time to clear its cache.
        msgProvider = new GlobalStateProviderServiceImpl();
        msgProvider.setAddonsManager(addonsManager);
        msgProvider.setAccessService(accessService);
        msgProvider.setSecurityService(securityService);
        msgProvider.setConfigsService(configsService);
        msgProvider.setRepositoryConfigService(repositoryConfigService);
        msgProvider.setStorageService(storageService);
        msgProvider.setStorageChecksumReplicationService(checksumReplicationService);
        msgProvider.setUpdatesService(updatesService);
        msgProvider.setSecurityService(securityService);
        msgProvider.setFederationClientService(clientService);
        when(addonsManager.getLicenseFooterMessage()).thenReturn(null);
        when(addonsManager.getRemoteRepUnsupportedUrls()).thenReturn(null);
        when(storageService.getStorageQuotaInfo(0)).thenReturn(null);
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);
        when(addonsManager.addonByType(FederatedRepoAddon.class)).thenReturn(federatedRepoAddon);
        when(federatedRepoAddon.getUnstableRepositories()).thenReturn(Collections.emptySet());
        when(xrayAddon.isXrayEnabled()).thenReturn(false);
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.isAnonymous()).thenReturn(false);
        when(configsService.hasConfig(V213ConversionFailFunction.PSQL_NODE_PROPS_INDEX_MISSING_MARKER))
                .thenReturn(false);
        final CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        when(configDescriptor.getSecurity()).thenReturn(mock(SecurityDescriptor.class));
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        when(accessService.isBasicAuthEnabled()).thenReturn(true);
        StorageChecksumReplicationConfig mock = mock(StorageChecksumReplicationConfig.class);
        when(checksumReplicationService.getConfigWithCache()).thenReturn(mock);
    }

    @AfterMethod
    public void clean() {
        responseMetadata.clear();
        homeStub.setProperty(ConstantValues.uiCustomFooterMessageFile, null);
        reset(addonsManager, storageService, securityService, xrayAddon);
    }

    public void noMessagesByDefault() {
        assertNoMessages();
    }

    //Anonymous should not see admin messages, this test sets all admin messages (xray, pypi and quota)
    public void testNoAdminMessagesShownToAnon() {
        reset(securityService);
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.isAnonymous()).thenReturn(true);

        StorageQuotaInfo quota = mock(StorageQuotaInfo.class);
        when(quota.isLimitReached()).thenReturn(true);
        when(storageService.getStorageQuotaInfo(0)).thenReturn(quota);
        when(addonsManager.getRemoteRepUnsupportedUrls()).thenReturn(List.of(ERR));
        when(xrayAddon.isXrayEnabled()).thenReturn(true);
        when(xrayAddon.isXrayAliveByHeartbeat()).thenReturn(false);

        assertNoMessages();
    }


    @Test(dataProvider = "providePsqlMessageValues")
    public void testDecorateWithPsqlIndexMismatch(boolean isAdmin, boolean configExists, boolean messageExpected) {
        reset(securityService);
        when(securityService.isAdmin()).thenReturn(isAdmin);
        when(securityService.isAnonymous()).thenReturn(!isAdmin);
        when(configsService.hasConfig(V213ConversionFailFunction.PSQL_NODE_PROPS_INDEX_MISSING_MARKER))
                .thenReturn(configExists);
        if (messageExpected) {
            String globalMessages = toJsonFromList();
            List<FooterMessage> footerMessages = JsonUtils.getInstance()
                    .readValueRef(globalMessages, new TypeReference<>() {
                    });
            assertNotNull(footerMessages);
            Assert.assertEquals(footerMessages.get(0).getMessage(),
                    "An incompatible index has been found for the Artifactory ‘node_props’ database table. This may " +
                            "be caused by certain property values that exceed the allowed length limit, causing " +
                            "creation of the database index to fail during a version upgrade. " +
                            "For instructions on how to fix this error " + URL_PLACEHOLDER);
            Assert.assertEquals(footerMessages.get(0).getUrls().size(), 1);
            Assert.assertEquals(footerMessages.get(0).getUrls().get(0).toLink(),
                    "<a href='https://www.jfrog.com/confluence/display/RTF/Troubleshooting#Troubleshooting" +
                            "-RecoveringfromError:AnincompatibleindexhasbeenfoundfortheArtifactory%E2%80%98node_props" +
                            "%E2%80%99databasetable'>click here</a>");
        } else {
            assertNoMessages();
        }
    }

    @Test
    public void testDecorateHeaderWithUsageLimitation() {
        reset(securityService);
        try {
            when(context.beanForType(AolService.class).isAol()).thenReturn(true);
            SubscriptionsService subscriptionsService = mock(SubscriptionsService.class);
            when(subscriptionsService.isFreeSubscription()).thenReturn(true);
            when(subscriptionsService.isSubscriptionSet()).thenReturn(true);
            when(context.beanForType(SubscriptionsService.class)).thenReturn(subscriptionsService);
            when(securityService.isAdmin()).thenReturn(true);
            String globalMessages = toJsonFromList();
            List<FooterMessage> footerMessages = JsonUtils.getInstance()
                    .readValueRef(globalMessages, new TypeReference<>() {
                    });
            assertNotNull(footerMessages);
            assertEquals(footerMessages.size(), 2);
            assertTrue(footerMessages.get(0).getType().equalsIgnoreCase("warning"));
            assertTrue(footerMessages.get(0).getMessage().startsWith("Quota Alert: Storage"));
            assertTrue(footerMessages.get(1).getType().equalsIgnoreCase("error"));
            assertTrue(footerMessages.get(1).getMessage().startsWith("You have reached your monthly quota for"));
            assertTrue(footerMessages.get(1).getMessage().endsWith("To enjoy unlimited usage, Upgrade"));
        } finally {
            when(context.beanForType(AolService.class).isAol()).thenReturn(false);
        }
    }

    @Test
    public void testDecorateHeadersWithLicenseNotInstalledAnonymous() {
        reset(securityService);
        // Try not admin user
        when(addonsManager.getLicenseFooterMessage()).thenReturn(ERR);
        String globalMessages = toJsonFromList();
        List<FooterMessage> footerMessages = JsonUtils.getInstance()
                .readValueRef(globalMessages, new TypeReference<>() {
                });
        assertNotNull(footerMessages);
        assertTrue(footerMessages.isEmpty());
    }

    @Test
    public void testDecorateHeadersWithLicenseNotInstalledAdminUser() {
        reset(securityService);
        // Try with admin user
        when(securityService.isAdmin()).thenReturn(true);
        when(addonsManager.getLicenseFooterMessage()).thenReturn(ERR);
        String globalMessages = toJsonFromList();
        List<FooterMessage> footerMessages = JsonUtils.getInstance()
                .readValueRef(globalMessages, new TypeReference<>() {
                });
        assertNotNull(footerMessages);
        assertEquals(footerMessages.size(), 1);
        assertTrue(footerMessages.get(0).getType().equalsIgnoreCase("error"));
        assertTrue(footerMessages.get(0).getMessage().equalsIgnoreCase(ERR.getMessage()));
    }

    @Test
    public void testDecorateHeadersWithLicenseNotInstalledAOL() {
        try {
            reset(securityService);
            // Try as AOL
            when(context.beanForType(AolService.class).isAol()).thenReturn(true)
                    .thenReturn(false);
            String globalMessages = toJsonFromList();
            List<FooterMessage> footerMessages = JsonUtils.getInstance()
                    .readValueRef(globalMessages, new TypeReference<>() {
                    });
            assertNotNull(footerMessages);
            assertTrue(footerMessages.isEmpty());
        } finally {
            when(context.beanForType(AolService.class).isAol()).thenReturn(false);
        }
    }

    @Test
    public void testDecorateFederatedRepositoriesImportMessages() {
        reset(securityService);
        // Try with admin user
        when(securityService.isAdmin()).thenReturn(true);
        when(federatedRepoAddon.getImportConflictsErrors()).thenReturn(Set.of("repo1", "repo2"));
        String globalMessages = toJsonFromList();
        List<FooterMessage> footerMessages = JsonUtils.getInstance()
                .readValueRef(globalMessages, new TypeReference<>() {
                });
        assertNotNull(footerMessages);
        assertEquals(footerMessages.size(), 1);
        assertTrue(footerMessages.get(0).getType().equalsIgnoreCase("warning"));
        assertEquals(GlobalStateProviderServiceImpl.IMPORT_FED_REPOS_MSG.replace("%1$s", "repo1, repo2"),
                footerMessages.get(0).getMessage());
    }

    @Test
    public void testDecorateFederatedRepositoriesImportMessageNotDisplay() {
        reset(securityService);
        // Try not admin user
        when(federatedRepoAddon.getImportConflictsErrors()).thenReturn(Set.of("repo1", "repo2"));
        String globalMessages = toJsonFromList();
        List<FooterMessage> footerMessages = JsonUtils.getInstance()
                .readValueRef(globalMessages, new TypeReference<>() {
                });
        assertNotNull(footerMessages);
        assertTrue(footerMessages.isEmpty());
    }

    @Test
    public void shouldShowMessageToAdminWhenBasicAuthDisabledAndExternalAuthDisabled() {
        reset(securityService);
        when(securityService.getAndResetForceEnabledBasicAuth()).thenReturn(true);
        when(accessService.isLdapEnabled()).thenReturn(false);
        // Try with admin user
        when(securityService.isAdmin()).thenReturn(true);

        List<FooterMessage> footerMessages = getDummyGlobalModelState().getMessages();
        assertNotNull(footerMessages);
        assertEquals(footerMessages.size(), 1);
        assertEquals(
                footerMessages.iterator().next().getMessage(),
                "Before disabling the basic authentication method, a few necessary steps, such as creating an " +
                        "internal group granted with Admin permissions need to be taken to prevent a lock on " +
                        "your instance. " + URL_PLACEHOLDER + " before disabling."
        );
        assertEquals(footerMessages.get(0).getUrls().size(), 1);
        assertEquals(footerMessages.get(0).getUrls().get(0).toLink(),
                "<a href='https://www.jfrog.com/confluence/display/JFROG/General+Security+Settings" +
                        "#GeneralSecuritySettings-DisableBasicAuthenticationMethod'>See here</a>");
    }

    @Test
    public void shouldNotShowMessageToNonAdminWhenBasicAuthDisabledAndExternalAuthDisabled() {
        reset(securityService);
        when(accessService.isBasicAuthEnabled()).thenReturn(false);
        when(accessService.isLdapEnabled()).thenReturn(false);
        when(centralConfigService.getDescriptor().getSecurity().isAnyExternalAuthEnabled()).thenReturn(false);
        // Try with admin user
        when(securityService.isAdmin()).thenReturn(false);

        List<FooterMessage> footerMessages = getDummyGlobalModelState().getMessages();
        assertNotNull(footerMessages);
        assertEquals(footerMessages.size(), 0);
    }

    @Test(dataProvider = "decorateRtfsMigrationNeeded")
    void testDecorateRtfsMigrationNeeded(boolean isSaaS, boolean migrationCompleted,
            boolean rtfsExists, int expectedMessagesSize, String expectedMessage) {
        reset(securityService);
        when(securityService.isAdmin()).thenReturn(true);
        when(AolUtils.isAol()).thenReturn(isSaaS);
        when(clientService.hasMigrationStateReached(FederationMigrationStatus.MIGRATION_COMPLETED))
                .thenReturn(migrationCompleted);
        when(clientService.federationNodeExistsInTopology()).thenReturn(rtfsExists);

        List<FooterMessage> messages = getDummyGlobalModelState().getMessages();
        assertEquals(messages.size(), expectedMessagesSize);
        if (expectedMessagesSize == 1) {
            assertEquals(messages.get(0).getMessage(), expectedMessage);
        }
    }

    @DataProvider
    public static Object[][] decorateRtfsMigrationNeeded() {
        return new Object[][] {
                // isSaas, migrationCompleted, rtfsExists, expectedMessagesSize, expectedMessage
                {false, true, true, 0, null},
                {false, true, false, 0, null},
                {false, false, true, 1, GlobalStateProviderServiceImpl.RTFS_MIGRATION_NEEDED_MSG},
                {false, false, false, 0, null},
                {true, false, true, 0, null}
        };
    }
    private void setupCustomerUpdatesService() {
        CustomerUpdatesModel customerUpdatesModel = new CustomerUpdatesModel();
        UsageUpdateModel usage = new UsageUpdateModel();
        UsageUpdateItem usageItem = createUsageItem("blocked", "bytes", 3L, 2L, 100.0);
        usage.setTraffic(usageItem);
        usageItem = createUsageItem("warning", "bytes", 1934232332L, 5368709120L, 80.50);
        usage.setStorage(usageItem);
        usageItem = createUsageItem("normal", "minutes", 900, 1500, 55.3);
        usage.setBuildTime(usageItem);
        customerUpdatesModel.setUsage(usage);
        when(updatesService.getCustomerUpdates()).thenReturn(customerUpdatesModel);
        when(updatesService.isEmailValidated()).thenReturn(true);
    }

    @Nonnull
    private UsageUpdateItem createUsageItem(String status, String units, long used, long limit, double percentage) {
        UsageUpdateItem usageItem = new UsageUpdateItem();
        usageItem.setStatus(status);
        usageItem.setUsed(used);
        usageItem.setLimit(limit);
        usageItem.setUnits(units);
        usageItem.setPercentage(percentage);
        return usageItem;
    }

    @Test
    public void initialSetupRequiredIsReported() {
        reset(securityService);
        when(securityService.isAdmin()).thenReturn(true);
        when(securityService.isAnonymous()).thenReturn(false);
        when(configsService.hasConfig(V213ConversionFailFunction.PSQL_NODE_PROPS_INDEX_MISSING_MARKER))
                .thenReturn(true);
        when(accessService.isInitialSetupRequired()).thenReturn(true);
        String json = toJsonFromList();
        Assert.assertTrue(json.contains("Initial setup has not been completed"), json);
        Assert.assertFalse(json.contains("default admin"), json);
        reset(accessService);
    }

    @DataProvider
    private static Object[][] providePsqlMessageValues() {
        return new Object[][] {
                // isAdminUser, configExists, messageExcepted
                {true, true, true},
                {false, true, false},
                {true, false, false},
                {false, false, false},
        };
    }

    public void testLicenseMsg() {
        reset(securityService);
        when(securityService.isAdmin()).thenReturn(true);
        when(addonsManager.getLicenseFooterMessage()).thenReturn(WARN);

        Assert.assertEquals(toJsonFromList(), toJsonFromList(ImmutableList.of(WARN)));
    }

    public void testPypiMsg() {
        reset(securityService);
        when(securityService.isAdmin()).thenReturn(true);
        when(addonsManager.getRemoteRepUnsupportedUrls()).thenReturn(List.of(ERR));

        Assert.assertEquals(toJsonFromList(), toJsonFromList(ImmutableList.of(ERR)));
    }

    public void testQuotaErr() {
        reset(securityService);
        when(securityService.isAdmin()).thenReturn(true);
        StorageQuotaInfo quota = mock(StorageQuotaInfo.class);
        when(quota.isLimitReached()).thenReturn(true);
        when(quota.getErrorMessage()).thenReturn("Quota limit reached!");
        when(storageService.getStorageQuotaInfo(0)).thenReturn(quota);

        Assert.assertTrue(toJsonFromList().contains("Quota limit reached!"));
    }

    public void testQuotaWarn() {
        reset(securityService);
        when(securityService.isAdmin()).thenReturn(true);
        StorageQuotaInfo quota = mock(StorageQuotaInfo.class);
        when(quota.isLimitReached()).thenReturn(false);
        when(quota.isWarningLimitReached()).thenReturn(true);
        when(quota.getWarningMessage()).thenReturn("Quota warn!");
        when(storageService.getStorageQuotaInfo(0)).thenReturn(quota);

        Assert.assertTrue(toJsonFromList().contains("Quota warn!"));
    }

    public void testXrayLiveWarn() {
        reset(securityService);
        when(securityService.isAdmin()).thenReturn(true);
        when(xrayAddon.isXrayEnabled()).thenReturn(true);
        when(xrayAddon.isXrayAliveByHeartbeat()).thenReturn(false);

        Assert.assertTrue(toJsonFromList().contains("JFrog Xray is unavailable"));
    }

    public void testXrayVersionWarn() {
        reset(securityService);
        when(securityService.isAdmin()).thenReturn(true);
        when(xrayAddon.isXrayEnabled()).thenReturn(true);
        when(xrayAddon.isXrayAliveByHeartbeat()).thenReturn(true);
        when(xrayAddon.isXrayVersionValidByHeartbeat()).thenReturn(false);

        Assert.assertTrue(toJsonFromList().contains("is incompatible with this"));
    }

    @Test
    public void testInvalidateCache() {
        reset(securityService);
        when(securityService.isAdmin()).thenReturn(true);
        when(xrayAddon.isXrayEnabled()).thenReturn(true);
        when(xrayAddon.isXrayAliveByHeartbeat()).thenReturn(true);
        when(xrayAddon.isXrayVersionValidByHeartbeat()).thenReturn(false);

        String globalMessages = toJsonFromList();
        List<FooterMessage> footerMessages = JsonUtils.getInstance()
                .readValueRef(globalMessages, new TypeReference<>() {
                });
        assertNotNull(footerMessages);
        assertFalse(footerMessages.isEmpty());
        Assert.assertTrue(footerMessages.get(0).getMessage().contains("is incompatible with this"));

        // Change state of Xray and validate message is still the same (since did not invalidate cache)
        when(xrayAddon.isXrayVersionValidByHeartbeat()).thenReturn(true);
        globalMessages = toJsonFromList();
        footerMessages = JsonUtils.getInstance()
                .readValueRef(globalMessages, new TypeReference<>() {
                });
        assertNotNull(footerMessages);
        assertFalse(footerMessages.isEmpty());

        // Invalidate and assert we now don't get any errors
        msgProvider.invalidateCache();
        globalMessages = toJsonFromList();
        footerMessages = JsonUtils.getInstance()
                .readValueRef(globalMessages, new TypeReference<>() {
                });
        assertNotNull(footerMessages);
        assertTrue(footerMessages.isEmpty());
    }

    //FooterMessageVisibility.all should be shown to anon
    public void testAnonymousCanSeeAllVisibility() {
        reset(securityService);
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.isAnonymous()).thenReturn(true);
        when(context.beanForType(AolService.class).isAol()).thenReturn(false);

        FooterMessage anonVisibleMsg = createWarning("Anonymous can see me", null, FooterMessageVisibility.all);
        when(addonsManager.getLicenseFooterMessage()).thenReturn(anonVisibleMsg);

        assertEquals(toJsonFromList(), toJsonFromList(ImmutableList.of(anonVisibleMsg)));
    }

    //FooterMessageVisibility.user should be shown to non admin, non anon
    public void testUserCanSeeUserVisibility() {
        reset(securityService);
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.isAnonymous()).thenReturn(false);

        FooterMessage anonVisibleMsg = createWarning("user can see me", List.of(), FooterMessageVisibility.all);
        when(addonsManager.getLicenseFooterMessage()).thenReturn(anonVisibleMsg);

        assertEquals(toJsonFromList(), toJsonFromList(ImmutableList.of(anonVisibleMsg)));
    }

    public void testMessageWithoutRange() throws IOException {
        FooterMessage msg = FooterMessage.builder()
                .message("meow")
                .type("info")
                .visibility("user")
                .build();
        setCustomMessage(msg);

        assertEquals(toJsonFromList(), toJsonFromList(ImmutableList.of(msg)));
    }

    public void testCustomMessageCache() throws IOException {
        int numOfMessages = getDummyGlobalModelState().getMessages().size();

        FooterMessage msg = FooterMessage.builder()
                .message("meow")
                .type("info")
                .visibility("user")
                .build();
        setCustomMessage(msg);

        // message will not be updated for an hour
        assertEquals(getDummyGlobalModelState().getMessages().size(), numOfMessages);
    }

    public void testMessageInRange() throws IOException {
        FooterMessage msg = FooterMessage.builder()
                .message("how")
                .type("info")
                .visibility("user")
                .showFrom(System.currentTimeMillis() - 1L)
                .showUntil(System.currentTimeMillis() + 1000000L)
                .build();
        setCustomMessage(msg);

        assertEquals(toJsonFromList(), toJsonFromList(ImmutableList.of(msg)));
    }

    //A message with a range that has already passed (should not show)
    public void testMessageWithPastRange() throws IOException {
        FooterMessage msg = FooterMessage.builder()
                .message("This is a test of the emergency broadcast system, please stand by.")
                .type("info")
                .visibility("user")
                .showFrom(System.currentTimeMillis() - 10000000L)
                .showUntil(System.currentTimeMillis() - 1000L)
                .build();
        setCustomMessage(msg);

        assertNoMessages();
    }

    //A message with a range that was not yet reached (should not show)
    public void testMessageWithFutureRange() throws IOException {
        FooterMessage msg = FooterMessage.builder()
                .message("I feel good today, Silent Bob. We're gonna make some money...")
                .type("info")
                .visibility("user")
                .showFrom(System.currentTimeMillis() + 10000000L)
                .showUntil(System.currentTimeMillis() + 10000000000L)
                .build();
        setCustomMessage(msg);

        assertNoMessages();
    }

    public void testDismissCode() throws IOException {
        FooterMessage msg = FooterMessage.builder()
                .message("My dismiss code bring all the bots to the yard")
                .type("info")
                .visibility("user")
                .dismissible(true)
                .build();

        setCustomMessage(msg);
        assertTrue(toJsonFromList()
                .contains("\"dismissCode\":\"331d7e580da8973a49b2c4565eb7323f\""));
    }

    public void testState() {
        when(subscriptionsService.getSubscriptionType()).thenReturn(SubscriptionType.FREE);
        when(context.beanForType(SubscriptionsService.class)).thenReturn(subscriptionsService);
        GlobalStateModelImpl globalModelState = getDummyGlobalModelState();
        assertEquals(globalModelState.getState().getSubscriptionType(), SubscriptionType.FREE.getString());
        when(subscriptionsService.getSubscriptionType()).thenReturn(null);
    }

    @Test(dataProvider = "dockerHubMsgProvider")
    public void testDecorateWithNoCredentialsDockerHubFoundRepos(boolean isAol, String url, RepoType repoType,
            String username, boolean isAdmin, boolean msgExpected) {

        if (isAol) {
            //Mock getCustomerUpdates to avoid other AOL messages
            when(updatesService.getCustomerUpdates()).thenReturn(null);
        }

        reset(securityService);
        when(context.beanForType(AolService.class).isAol()).thenReturn(isAol);
        when(securityService.isAdmin()).thenReturn(isAdmin);
        when(securityService.isAnonymous()).thenReturn(!isAdmin);
        RemoteRepoConfig remoteRepoConfig = getDescriptorWithRemoteRepo(url, repoType, username);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE)).thenReturn(
                List.of(remoteRepoConfig));
        if (msgExpected) {
            assertTrue(toJsonFromList().contains("Please note, it is recommended to set a DockerHub account on your " +
                    "Docker remote repositories to work against Docker Hub. List of relevant repositories:"));
        } else {
            assertNoMessages();
        }
        reset(centralConfigService);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE)).thenReturn(List.of());
        when(context.beanForType(AolService.class).isAol()).thenReturn(false);
    }

    @Test
    public void shouldShowMessageToValidateEmail() {
        when(securityService.isAdmin()).thenReturn(true);
        when(context.beanForType(AolService.class).isAol()).thenReturn(true);
        SubscriptionsService subscriptionsService = mock(SubscriptionsService.class);
        when(subscriptionsService.isFreeSubscription()).thenReturn(true);
        when(subscriptionsService.isSubscriptionSet()).thenReturn(true);
        when(context.beanForType(SubscriptionsService.class)).thenReturn(subscriptionsService);
        when(updatesService.isEmailValidated()).thenReturn(false);
        try {
            List<FooterMessage> footerMessages = getDummyGlobalModelState().getMessages();
            assertNotNull(footerMessages);
            assertFalse(footerMessages.isEmpty());
            assertTrue(footerMessages.stream().map(FooterMessage::getMessage)
                    .anyMatch(m -> m.contains("Your email address has not been verified yet")));
        } finally {
            when(updatesService.isEmailValidated()).thenReturn(true);
        }
    }

    @DataProvider
    private static Object[][] dockerHubMsgProvider() {
        return new Object[][] {
                //url, repoType, username, isAdminUser, msgExpected
                {false, "https://registry-1.docker.io/", RepoType.Docker, "", true, true},
                {false, "http://registry-1.docker.io/", RepoType.Docker, "", true, true},
                {false, "https://registry-1.docker.io/", RepoType.Docker, "tester", true, false},
                {false, "https://registry-1.docker.io/", RepoType.Debian, "tester", true, false},
                {false, "https://registry-1.docker.io/", RepoType.Docker, "", false, false},
                {true, "http://registry-1.docker.io/", RepoType.Docker, "", true, false}
        };
    }

    private void createContext() {
        context = mock(InternalArtifactoryContext.class);
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        subscriptionsService = mock(SubscriptionsService.class);
        when(subscriptionsService.getSubscriptionType()).thenReturn(null);
        when(context.beanForType(SubscriptionsService.class)).thenReturn(subscriptionsService);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    private RemoteRepoConfig getDescriptorWithRemoteRepo(String url, RepoType repoType, String username) {
        RemoteRepoConfig dockerRemote = RemoteRepoConfig.create("dockerRemote", repoType);
        dockerRemote.getRepoTypeConfig().setUrl(url);
        dockerRemote.getRepoTypeConfig().setUsername(username);
        return dockerRemote;
    }

    private void setCustomMessage(FooterMessage msg) throws IOException {
        Files.write(toJsonFromList(ImmutableList.of(msg)).getBytes(), tempFile);
        homeStub.setProperty(ConstantValues.uiCustomFooterMessageFile, tempFile.getAbsolutePath());
    }

    private void assertNoMessages() {
        Assert.assertTrue(toJsonFromList().equalsIgnoreCase("[]"));
    }

    private String toJsonFromList() {
        GlobalStateModelImpl globalModelState = getDummyGlobalModelState();
        return toJsonFromList(globalModelState.getMessages());
    }

    @Nonnull
    private GlobalStateModelImpl getDummyGlobalModelState() {
        return (GlobalStateModelImpl) msgProvider.getGlobalStateAndMessages();
    }

    private String toJsonFromList(List<FooterMessage> messages) {
        return JsonUtils.getInstance().valueToString(messages);
    }


}