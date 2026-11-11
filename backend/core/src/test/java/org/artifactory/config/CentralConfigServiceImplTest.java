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

package org.artifactory.config;

import jakarta.annotation.Nonnull;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddonsImpl;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.RepoConfigBackwardsCompatibleImportProcessor;
import org.artifactory.api.config.RepoExistenceFunction;
import org.artifactory.api.config.VersionInfo;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.mail.MailServerDescriptor;
import org.artifactory.descriptor.property.PredefinedValue;
import org.artifactory.descriptor.property.Property;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.property.SystemPropertyDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.group.LdapGroupSetting;
import org.artifactory.descriptor.security.oauth.CentralOAuthProviders;
import org.artifactory.exception.DocumentBuildException;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ArtifactoryTracerConfiguration;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.projects.ProjectAdminValidation;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManager;
import org.artifactory.repo.service.RepoConfigBootstrapStorageHelperFactory;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.sapi.common.ImportKind;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.schedule.CachedThreadPoolTaskExecutor;
import org.artifactory.security.access.AccessService;
import org.artifactory.service.ConfigValidationService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.db.fs.service.ConfigsServiceImpl;
import org.artifactory.storage.db.security.service.VersioningCacheImpl;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.version.VersionProvider;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.jfrog.access.client.auth.ldap.AuthSettingsClient;
import org.jfrog.common.ResourceUtils;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.common.config.diff.DiffMerger;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.config.ArtifactoryConfigDiffUtil.findDiff;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.testng.AssertJUnit.assertNull;

/**
 * Unit tests for the CentralConfigServiceImpl.
 *
 * @author Yossi Shaul
 */
public class CentralConfigServiceImplTest extends ArtifactoryHomeBoundTest {

    private static final CompoundVersionDetails earlyVersion = new CompoundVersionDetails(
            ArtifactoryServiceVersionProvider.v500.get(),
            "EARLY_TEST", 0
    );
    private static final CompoundVersionDetails recentVersion = new CompoundVersionDetails(
            ArtifactoryServiceVersionProvider.v522m001.get(),
            "RECENT_TEST", 0
    );
    private CentralConfigServiceImpl centralConfigService;
    private Map<String, String> configs;
    private ConfigsService configsService;
    private AccessService accessService;
    private PlatformConfigService platformConfigService;
    private RepoExistenceFunction repoExistenceFunction;
    private ArtifactoryApplicationContext context;
    private static MockedStatic<AccessConfigHolder> staticAccessConfigHolder;
    private static final AccessConfigHolder spyAccessConfigHolder = Mockito.spy(AccessConfigHolder.class);
    private static final ConfigValidationService configValidationService = spy(new ConfigValidationService());
    private MockedStatic<RepoConfigBootstrapStorageHelperFactory> repoConfigBootstrapStorageHelperFactory;

    @DataProvider
    public static Object[][] booleanProvider() {
        return new Object[][] {
                {true},
                {false}
        };
    }

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

    @BeforeMethod
    public void setup() throws IOException {
        repoConfigBootstrapStorageHelperFactory = Mockito.mockStatic(RepoConfigBootstrapStorageHelperFactory.class);
        configs = new HashMap<>();
        InputStream is = getClass().getResourceAsStream("/META-INF/default/artifactory.config.xml");
        String defaultConfig = IOUtils.toString(is, Charset.defaultCharset());
        configs.put(ArtifactoryHome.ARTIFACTORY_CONFIG_FILE, defaultConfig);

        centralConfigService = new CentralConfigServiceImpl();
        SecurityService securityService = EasyMock.createMock(SecurityService.class);
        AddonsManager addonsManager = EasyMock.createMock(AddonsManager.class);
        HaCommonAddon haCommonAddon = EasyMock.createMock(HaCommonAddon.class);
        ConfigChangeEventHandlerManager configChangeEventHandlerManager = mock(ConfigChangeEventHandlerManager.class);
        VersionProvider versionProvider = mock(VersionProvider.class);
        ProjectAdminValidation projectAdminValidation = new ProjectAdminValidation(securityService);
        HaAddon haAddon = EasyMock.createMock(HaAddon.class);
        ConfigurationChangesInterceptors interceptors = createMock(ConfigurationChangesInterceptors.class);

        configsService = EasyMock.createMock(ConfigsService.class);
        configsService.backupConfig(anyObject(), anyLong());
        expectLastCall().anyTimes();

        context = mock(ArtifactoryApplicationContext.class);

        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(context.isReady()).thenReturn(true);
        when(context.beanForType(ArtifactoryTracer.class))
                .thenReturn(new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer()));
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beanForType(ConfigChangeEventHandlerManager.class)).thenReturn(configChangeEventHandlerManager);
        when(context.getVersionProvider()).thenReturn(versionProvider);
        expect(securityService.isAdmin()).andReturn(true).anyTimes();
        expect(securityService.isAnyProjectAdmin()).andReturn(true).anyTimes();
        expect(haCommonAddon.isHaEnabled()).andReturn(false).anyTimes();
        expect(addonsManager.addonByType(HaCommonAddon.class)).andReturn(haCommonAddon).anyTimes();
        expect(addonsManager.addonByType(HaAddon.class)).andReturn(haAddon).anyTimes();
        interceptors.onBeforeSave(anyObject());
        expectLastCall().anyTimes();
        interceptors.onAfterSave(anyObject(), anyObject());
        expectLastCall().anyTimes();
        IAnswer<Boolean> answer = initConfigsService();
        expect(configsService.updateConfigIfLastModificationMatch(anyObject(), anyObject(), anyLong(), anyLong()))
                .andAnswer(answer).anyTimes();
        accessService = mock(AccessService.class);
        AuthSettingsClient authSettingsClient = mock(AuthSettingsClient.class);
        platformConfigService = mock(PlatformConfigService.class);
        when(accessService.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);
        doNothing().when(accessService).updateConfigurationModel(any());
        ReflectionTestUtils.setField(centralConfigService, "securityService", securityService);
        ReflectionTestUtils.setField(centralConfigService, "addonsManager", addonsManager);
        ReflectionTestUtils.setField(centralConfigService, "interceptors", interceptors);
        ReflectionTestUtils.setField(centralConfigService, "configsService", configsService);
        ReflectionTestUtils.setField(centralConfigService, "accessService", accessService);
        ReflectionTestUtils
                .setField(centralConfigService, "centralConfigDiffValidation", projectAdminValidation);
        ReflectionTestUtils.setField(centralConfigService, "configValidationService", configValidationService);
        ReflectionTestUtils.setField(centralConfigService, "subConverters", Set.of());
        ReflectionTestUtils.setField(centralConfigService, "platformConfigService", platformConfigService);

        when(context.getArtifactoryHome()).thenReturn(homeStub);

        RepoConfigBackwardsCompatibleImportProcessor repoConfigBackwardsCompatibleImportProcessor =
                EasyMock.createMock(RepoConfigBackwardsCompatibleImportProcessor.class);
        repoExistenceFunction = EasyMock.createMock(RepoExistenceFunction.class);

        when(context.beanForType(RepoConfigBackwardsCompatibleImportProcessor.class)).thenReturn(
                repoConfigBackwardsCompatibleImportProcessor);

        centralConfigService.setRepoConfigBCProcessor(repoConfigBackwardsCompatibleImportProcessor);

        repoConfigBackwardsCompatibleImportProcessor.importRepoConfigs(anyObject(), EasyMock.anyBoolean(),
                EasyMock.anyBoolean());
        expectLastCall().anyTimes();
        repoConfigBackwardsCompatibleImportProcessor.reloadRepoConfigs();
        expectLastCall().anyTimes();

        expect(repoConfigBackwardsCompatibleImportProcessor.countAll()).andReturn(2).anyTimes();

        expect(repoExistenceFunction.existsByRepoKey("local")).andReturn(true).anyTimes();
        expect(repoExistenceFunction.existsByRepoKey("remote-repo")).andReturn(true).anyTimes();
        expect(repoExistenceFunction.existsByRepoKey("noRemote")).andReturn(false).anyTimes();
        expect(repoExistenceFunction.existsByRepoKey("missingRepo")).andReturn(false).anyTimes();

        replay(securityService, addonsManager, haCommonAddon, interceptors,
                repoConfigBackwardsCompatibleImportProcessor, repoExistenceFunction,
                configsService);
        ArtifactoryContextThreadBinder.bind(context);

        CachedThreadPoolTaskExecutor cachedThreadPoolTaskExecutor = new CachedThreadPoolTaskExecutor();
        ReflectionTestUtils
                .setField(centralConfigService, "cachedThreadPoolTaskExecutor", cachedThreadPoolTaskExecutor);

        centralConfigService.init();

        verifyNoInteractions(configChangeEventHandlerManager);
    }

    @AfterMethod
    public void cleanup() {
        ArtifactoryContextThreadBinder.unbind();
        repoConfigBootstrapStorageHelperFactory.close();
    }

    private IAnswer<Boolean> initConfigsService() {
        expect(configsService.getConfig(anyString())).andAnswer(() -> configs.get(getCurrentArguments()[0].toString()))
                .anyTimes();
        expect(configsService.hasConfig(anyObject()))
                .andAnswer(() -> configs.containsKey(getCurrentArguments()[0].toString())).anyTimes();
        IAnswer<Boolean> answer = () -> {
            Object[] args = getCurrentArguments();
            configs.put((String) args[0], (String) args[1]);
            return true;
        };
        expect(configsService.addOrUpdateConfig(anyString(), anyObject(), anyLong())).andAnswer(answer).anyTimes();
        configsService.addConfig(anyObject(), anyObject(), anyLong());
        expectLastCall().andAnswer(answer).anyTimes();
        configsService.updateConfig(anyObject(), anyObject(), anyLong());
        expectLastCall().andAnswer(answer).anyTimes();
        return answer;
    }

    @Test
    public void keyStorePasswordMovesToDescriptor() throws Exception {
        String PASSWORD_CONFIG_KEY = "keystore:password";
        String PASSWORD = "123456";

        // Create mocks
        ConfigsServiceImpl configsService = mock(ConfigsServiceImpl.class);
        ReflectionTestUtils.setField(centralConfigService, "configsService", configsService);
        when(configsService.getConfig(PASSWORD_CONFIG_KEY)).thenReturn(PASSWORD);
        when(configsService.hasConfig(eq(ArtifactoryHome.ARTIFACTORY_CONFIG_FILE))).thenReturn(false);

        AddonsManager addonsManager = mock(AddonsManager.class);
        ReflectionTestUtils.setField(centralConfigService, "addonsManager", addonsManager);
        when(addonsManager.addonByType(any())).thenReturn(new CoreAddonsImpl());

        ConfigurationChangesInterceptors interceptors = mock(ConfigurationChangesInterceptors.class);
        ReflectionTestUtils.setField(centralConfigService, "interceptors", interceptors);

        // Create descriptor
        MutableCentralConfigDescriptor configDescriptor = getConfigDescriptor();
        configDescriptor.setSecurity(new SecurityDescriptor());
        initCache(centralConfigService, configDescriptor);

        centralConfigService.moveKeyStorePasswordToConfig(earlyVersion, recentVersion, configDescriptor);

        assertTrue(PASSWORD.equals(configDescriptor.getSecurity().getSigningKeysSettings().getKeyStorePassword()));
    }

    @Test
    public void testMergeOfOneChange() {
        initConfig();

        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        String newBaseUrl = "https://newUrlbase:8080";
        newDescriptor.setUrlBase(newBaseUrl);
        MutableCentralConfigDescriptor clone = SerializationUtils.clone(newDescriptor);
        centralConfigService.saveEditedDescriptorAndReload(newDescriptor);
        assertNotEquals(centralConfigService.getDescriptor().getRevision(), clone.getRevision());
        assertEquals(centralConfigService.getDescriptor().getUrlBase(), newBaseUrl);
    }

    @Test
    public void testValidationCalled() {
        ConfigValidationService configValidationService = mock(ConfigValidationService.class);
        ReflectionTestUtils
                .setField(centralConfigService, "configValidationService", configValidationService);
        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName("new");
        centralConfigService.saveEditedDescriptorAndReload(newDescriptor, repoLayout);
        verify(configValidationService, times(1)).validate(repoLayout);
    }

    @Test
    public void testValidationCalledForMcScope() {
        ConfigValidationService configValidationService = mock(ConfigValidationService.class);
        ReflectionTestUtils
                .setField(centralConfigService, "configValidationService", configValidationService);
        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName("new");
        SecurityService securityService = EasyMock.createMock(SecurityService.class);
        ReflectionTestUtils.setField(centralConfigService, "securityService", securityService);
        expect(securityService.isAnyProjectAdmin()).andReturn(false).anyTimes();
        expect(securityService.hasMcScope()).andReturn(true).anyTimes();
        replay(securityService);
        centralConfigService.saveEditedDescriptorAndReload(newDescriptor, repoLayout);
        verify(configValidationService, times(1)).validate(repoLayout);
    }

    @Test(expectedExceptions = ForbiddenException.class)
    public void testForNonMcScopeNonProjectAdmin() {
        ConfigValidationService configValidationService = mock(ConfigValidationService.class);
        ReflectionTestUtils
                .setField(centralConfigService, "configValidationService", configValidationService);
        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName("new");
        SecurityService securityService = EasyMock.createMock(SecurityService.class);
        ReflectionTestUtils.setField(centralConfigService, "securityService", securityService);
        expect(securityService.isAnyProjectAdmin()).andReturn(false).anyTimes();
        expect(securityService.hasMcScope()).andReturn(false).anyTimes();
        replay(securityService);
        centralConfigService.saveEditedDescriptorAndReload(newDescriptor, repoLayout);
    }

    @Test
    public void testSaveConfigAtFirstTime() throws InterruptedException {
        EasyMock.reset(configsService);
        initConfigsService();
        EasyMock.replay(configsService);

        File latestFile = homeStub.getArtifactoryConfigLatestFile();
        latestFile.delete();
        assertFalse(latestFile.exists());

        MutableCentralConfigDescriptor config = centralConfigService.getMutableDescriptor();
        centralConfigService.saveEditedDescriptorAndReload(config);
        assertNotEquals(centralConfigService.getDescriptor().getRevision(), 0);

        Thread.sleep(200); // File is written in background
        assertTrue(latestFile.exists());
        EasyMock.verify(configsService);
    }

    @Test
    public void testSaveConfigFileIfNeededOnStartup() throws InterruptedException {
        EasyMock.reset(configsService);
        // save config in db
        initConfigsService();
        EasyMock.replay(configsService);

        // delete config file
        File latestFile = homeStub.getArtifactoryConfigLatestFile();
        latestFile.delete();
        assertFalse(latestFile.exists());

        centralConfigService.init();
        Thread.sleep(200);
        // config file should be written on startup
        assertTrue(latestFile.exists());
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "Failed to read object from " +
            "stream")
    public void testSaveInvalidXmlWithImport() throws IOException {
        initConfig();

        String invalidConfig = IOUtils
                .toString(getClass().getResourceAsStream("artifactory.config.invalid.xml"));
        FileUtils.write(ArtifactoryHome.get().getArtifactoryConfigImportFile(), invalidConfig);

        centralConfigService.init();
    }

    @Test
    public void testSaveValidXmlWithImport() throws IOException {
        initConfig();

        String validConfig = IOUtils
                .toString(getClass().getResourceAsStream("artifactory.config.valid.xml"));
        FileUtils.write(ArtifactoryHome.get().getArtifactoryConfigImportFile(), validConfig);

        centralConfigService.init();
    }

    @Test(dataProvider = "booleanProvider")
    public void testMergeOfTwoChangesWithoutConflicts(boolean inOrder) {
        initConfig();

        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        String newBaseUrl = "https://newUrlbase:8080";
        newDescriptor.setUrlBase(newBaseUrl);

        MutableCentralConfigDescriptor newDescriptor2 = centralConfigService.getMutableDescriptor();
        String newServerName = "https://newUrlbase:8080";
        newDescriptor2.setServerName(newServerName);

        saveWithOrder(inOrder, newDescriptor, newDescriptor2);

        assertTrue(centralConfigService.getDescriptor().getRevision() > newDescriptor.getRevision());
        assertTrue(centralConfigService.getDescriptor().getRevision() > newDescriptor2.getRevision());
        assertEquals(centralConfigService.getDescriptor().getUrlBase(), newBaseUrl);
        assertEquals(centralConfigService.getDescriptor().getServerName(), newServerName);
    }

    @Test(dataProvider = "booleanProvider")
    public void testMergeOfTwoChangesWithConflict(boolean inOrder) {
        initConfig();

        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        newDescriptor.setUrlBase("https://newUrlbase:8080");

        MutableCentralConfigDescriptor newDescriptor2 = centralConfigService.getMutableDescriptor();
        newDescriptor2.setUrlBase("https://newUrlbase222:8080");

        String toValidate;
        if (inOrder) {
            centralConfigService.saveEditedDescriptorAndReload(newDescriptor);
            centralConfigService.saveEditedDescriptorAndReload(newDescriptor2);
            toValidate = newDescriptor2.getUrlBase();
        } else {
            centralConfigService.saveEditedDescriptorAndReload(newDescriptor2);
            centralConfigService.saveEditedDescriptorAndReload(newDescriptor);
            toValidate = newDescriptor.getUrlBase();
        }

        assertTrue(centralConfigService.getDescriptor().getRevision() > newDescriptor.getRevision());
        assertTrue(centralConfigService.getDescriptor().getRevision() > newDescriptor2.getRevision());
        assertEquals(centralConfigService.getDescriptor().getUrlBase(), toValidate);
    }

    @Test(dataProvider = "booleanProvider")
    public void testMergeOfMapObjects(boolean inOrder) {
        initConfig();

        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        LocalReplicationDescriptor newReplication = new LocalReplicationDescriptor();
        newReplication.setReplicationKey("hello-world");
        newReplication.setRepoKey("repo-key");
        newDescriptor.addLocalReplication(newReplication);

        MutableCentralConfigDescriptor newDescriptor2 = centralConfigService.getMutableDescriptor();
        LocalReplicationDescriptor newReplication2 = new LocalReplicationDescriptor();
        newReplication2.setReplicationKey("hello-world2");
        newReplication2.setRepoKey("repo-key2");
        newDescriptor2.addLocalReplication(newReplication2);

        saveWithOrder(inOrder, newDescriptor, newDescriptor2);

        assertTrue(centralConfigService.getDescriptor().getRevision() > newDescriptor.getRevision());
        assertTrue(centralConfigService.getDescriptor().getRevision() > newDescriptor2.getRevision());
        LocalReplicationDescriptor localReplication =
                centralConfigService.getDescriptor().getLocalReplication("repo-key" + (inOrder ? "2" : ""));
        assertNotNull(localReplication);
        assertEquals(localReplication.getReplicationKey(), "hello-world" + (inOrder ? "2" : ""));
    }

    @Test(dataProvider = "booleanProvider")
    public void testMergeListOfObjects(boolean inOrder) {
        initConfig();

        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        PropertySet propertySet = new PropertySet();
        propertySet.setName("name");
        newDescriptor.addPropertySet(propertySet);

        MutableCentralConfigDescriptor newDescriptor2 = centralConfigService.getMutableDescriptor();
        PropertySet propertySet2 = new PropertySet();
        propertySet2.setName("name2");
        newDescriptor2.addPropertySet(propertySet2);

        saveWithOrder(inOrder, newDescriptor, newDescriptor2);

        assertTrue(centralConfigService.getDescriptor().getRevision() > newDescriptor.getRevision());
        assertTrue(centralConfigService.getDescriptor().getRevision() > newDescriptor2.getRevision());
        assertEquals(centralConfigService.getDescriptor().getPropertySets().get(0).getName(),
                "name" + (inOrder ? "" : "2"));
        assertEquals(centralConfigService.getDescriptor().getPropertySets().get(1).getName(),
                "name" + (inOrder ? "2" : ""));
    }

    @Test(dataProvider = "booleanProvider")
    public void testMergeListWithConflictOnKey(boolean inOrder) {
        initConfig();

        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        PropertySet propertySet = new PropertySet();
        propertySet.setName("name");
        propertySet.setVisible(false);
        newDescriptor.addPropertySet(propertySet);

        MutableCentralConfigDescriptor newDescriptor2 = centralConfigService.getMutableDescriptor();
        PropertySet propertySet2 = new PropertySet();
        propertySet2.setName("name");
        propertySet2.setVisible(true);
        newDescriptor2.addPropertySet(propertySet2);

        saveWithOrder(inOrder, newDescriptor, newDescriptor2);

        assertTrue(centralConfigService.getDescriptor().getRevision() > newDescriptor.getRevision());
        assertTrue(centralConfigService.getDescriptor().getRevision() > newDescriptor2.getRevision());
        assertEquals(centralConfigService.getDescriptor().getPropertySets().size(), 1);
        assertEquals(centralConfigService.getDescriptor().getPropertySets().get(0).isVisible(), inOrder);
    }

    @Test(dataProvider = "booleanProvider")
    public void testMergeMapWithConflictOnKey(boolean inOrder) {
        initConfig();

        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        LocalReplicationDescriptor newReplication = new LocalReplicationDescriptor();
        newReplication.setReplicationKey("hello-world");
        newReplication.setRepoKey("repo-key");
        newDescriptor.addLocalReplication(newReplication);

        MutableCentralConfigDescriptor newDescriptor2 = centralConfigService.getMutableDescriptor();
        LocalReplicationDescriptor newReplication2 = new LocalReplicationDescriptor();
        newReplication2.setReplicationKey("hello-world");
        newReplication2.setRepoKey("repo-key2");
        newDescriptor2.addLocalReplication(newReplication2);

        saveWithOrder(inOrder, newDescriptor, newDescriptor2);

        assertTrue(centralConfigService.getDescriptor().getRevision() > newDescriptor.getRevision());
        assertTrue(centralConfigService.getDescriptor().getRevision() > newDescriptor2.getRevision());
        LocalReplicationDescriptor localReplication =
                centralConfigService.getDescriptor().getLocalReplication("repo-key" + (inOrder ? "2" : ""));
        assertNotNull(localReplication);
        assertEquals(localReplication.getReplicationKey(), "hello-world");
    }

    @Test
    public void testGetJfrogBaseUrlNoPath() {
        initConfig();

        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        String newBaseUrl = "https://newUrlbase:8080";
        newDescriptor.setUrlBase(newBaseUrl);
        centralConfigService.saveEditedDescriptorAndReload(newDescriptor);
        assertEquals(centralConfigService.getDescriptor().getJFrogUrlBase(8081), newBaseUrl);
    }

    @Test
    public void testGetJfrogBaseUrlWIthPortSwitch() {
        initConfig();

        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        String newBaseUrl = "https://newUrlbase:8080";
        newDescriptor.setUrlBase(newBaseUrl);
        centralConfigService.saveEditedDescriptorAndReload(newDescriptor);
        assertEquals(centralConfigService.getDescriptor().getJFrogUrlBase(8082), newBaseUrl);
    }

    @Test
    public void testGetJfrogBaseUrlWithPath() {
        initConfig();

        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        String jfrogBaseUrl = "https://newUrlbase:8080";
        String newBaseUrl = jfrogBaseUrl + "/artifactory";
        newDescriptor.setUrlBase(newBaseUrl);
        centralConfigService.saveEditedDescriptorAndReload(newDescriptor);
        assertEquals(centralConfigService.getDescriptor().getJFrogUrlBase(8081), jfrogBaseUrl);
        assertEquals(centralConfigService.getDescriptor().getUrlBase(), newBaseUrl);
    }

    @Test
    public void centralConfigProcessingWithDtdSuccessful() {
        final String configWithDtd = ResourceUtils.getResourceAsString("/config/config-with-doctype_v2.1.0.xml");

        centralConfigService.setConfigXml(configWithDtd);

        // no exception for non UI/API request.
        centralConfigService.setConfigXml(configWithDtd, false);

        // exception for UI/API request.
        final Throwable throwable = expectThrows(
                Throwable.class,
                () -> centralConfigService.setConfigXml(configWithDtd, true)
        );
        Assert.assertNotNull(throwable);
        Assert.assertTrue(throwable instanceof DocumentBuildException);
        Assert.assertEquals(throwable.getMessage(),
                "Error on line 2: DOCTYPE is disallowed when the feature " +
                        "\"http://apache.org/xml/features/disallow-doctype-decl\" set to true."
        );
    }

    @Test
    public void getStringBasedOnEnumTest() {
        assertNull(
                centralConfigService
                        .getReloadablePropertyByEnum(SubscriptionType.class, ConstantValues.aolSubscriptionType));

        homeStub.getArtifactoryProperties().setProperty(ConstantValues.aolSubscriptionType.getPropertyName(),
                SubscriptionType.FREE_XRAY.getString());
        SubscriptionType stringBasedEnum =
                centralConfigService
                        .getReloadablePropertyByEnum(SubscriptionType.class, ConstantValues.aolSubscriptionType);
        assertEquals(stringBasedEnum, SubscriptionType.FREE_XRAY);

        centralConfigService.getDescriptor().getSystemProperties()
                .add(new SystemPropertyDescriptor(ConstantValues.aolSubscriptionType.getPropertyName(),
                        SubscriptionType.FREE.getString()));
        stringBasedEnum =
                centralConfigService
                        .getReloadablePropertyByEnum(SubscriptionType.class, ConstantValues.aolSubscriptionType);
        assertEquals(stringBasedEnum, SubscriptionType.FREE);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "No enum value.*")
    public void getStringBasedOnEnumWithExceptionFromConstantValuesTest() {
        centralConfigService.getDescriptor().getSystemProperties()
                .add(new SystemPropertyDescriptor(ConstantValues.aolSubscriptionType.getPropertyName(),
                        "wrong subscription"));
        centralConfigService.getReloadablePropertyByEnum(SubscriptionType.class, ConstantValues.aolSubscriptionType);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "No enum value.*")
    public void getStringBasedOnEnumWithExceptionFromConfigTest() {
        homeStub.getArtifactoryProperties().setProperty(ConstantValues.aolSubscriptionType.getPropertyName(),
                "no subscription");
        centralConfigService.getReloadablePropertyByEnum(SubscriptionType.class, ConstantValues.aolSubscriptionType);
    }

    @Test
    public void testRemoveDuplicatedReplicationsNoNeedToRun() throws IOException {
        initConfig();
        CompoundVersionDetails source = createVersion();
        CompoundVersionDetails target = createVersion();
        MutableCentralConfigDescriptor mutableDescriptor = spy(centralConfigService.getMutableDescriptor());
        centralConfigService.removeDuplicatedReplications(source, target, mutableDescriptor);
        verify(mutableDescriptor, times(0)).getLocalReplicationsMap();
    }

    @Test
    public void testRemoveDuplicatedReplicationsRunsWithMarker() throws IOException {
        initConfig();
        CompoundVersionDetails source = createVersion();
        CompoundVersionDetails target = createVersion();
        MutableCentralConfigDescriptor mutableDescriptor = spy(centralConfigService.getMutableDescriptor());
        File forceDuplicateReplicationConvertFile = homeStub.getForceDuplicateReplicationConvertFile();
        if (forceDuplicateReplicationConvertFile.exists()) {
            forceDuplicateReplicationConvertFile.delete();
        }
        assertTrue(forceDuplicateReplicationConvertFile.createNewFile());
        centralConfigService.removeDuplicatedReplications(source, target, mutableDescriptor);
        verify(mutableDescriptor, times(1)).getLocalReplicationsMap();
        assertFalse(forceDuplicateReplicationConvertFile.exists());
    }

    @Test
    public void testRemoveDuplicatedReplications() {
        initConfig();
        centralConfigService.setRepoExistence(repoExistenceFunction);
        CompoundVersionDetails source =
                new CompoundVersionDetails(ArtifactoryServiceVersionProvider.v7140m001.get(), "build", 9);
        CompoundVersionDetails target = createVersion();

        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        LocalRepoDescriptor localRepoDescriptor = createLocalRepo(mutableDescriptor);

        LocalReplicationDescriptor localReplicationDescriptor =
                createLocalReplication(mutableDescriptor, localRepoDescriptor.getKey(), "https://url.com",
                        " 0 23 5 * * ?");

        LocalReplicationDescriptor localReplicationDescriptor2 =
                createLocalReplication(mutableDescriptor, localRepoDescriptor.getKey(), "https://url2.com",
                        " 0 23 5 * * ?");

        LocalReplicationDescriptor localReplicationDescriptor3 =
                createLocalReplication(mutableDescriptor, localRepoDescriptor.getKey(), "https://url.com",
                        " 0 13 5 * * ?");
        LocalReplicationDescriptor localReplicationDescriptor4 =
                createLocalReplication(mutableDescriptor, "missingRepo", "https://url.com",
                        " 0 13 5 * * ?");

        HttpRepoDescriptor httpRepoDescriptor = createRemoteRepo(mutableDescriptor);

        createRemoteReplication(mutableDescriptor, httpRepoDescriptor.getKey(), " 0 23 5 * * ?", "replica");
        RemoteReplicationDescriptor remoteReplicationDescriptor2 =
                createRemoteReplication(mutableDescriptor, httpRepoDescriptor.getKey(), " 0 1 5 * * ?", "replica1");
        createRemoteReplication(mutableDescriptor, "noRemote", " 0 1 5 * * ?", "replica2");
        centralConfigService.removeDuplicatedReplications(source, target, mutableDescriptor);
        assertLocalReplications(mutableDescriptor, localRepoDescriptor, localReplicationDescriptor,
                localReplicationDescriptor2, localReplicationDescriptor3, localReplicationDescriptor4);

        assertRemoteReplications(mutableDescriptor, remoteReplicationDescriptor2);
    }

    @Test
    public void testIsInterested() throws IOException {
        CompoundVersionDetails target = createVersion();
        CompoundVersionDetails source = createVersion();

        /* this check is added to avoid running the test on release version. Only snapshot and milestone versions
         need this test to be run.
         */
        if (source.isCurrent()) {return;}

        assertTrue(centralConfigService.isInterested(source, target));
        source = new CompoundVersionDetails(ArtifactoryServiceVersion.getCurrent(), "build", 9);

        assertFalse(centralConfigService.isInterested(source, target));
        File forceDuplicateReplicationConvertFile = homeStub.getForceDuplicateReplicationConvertFile();
        if (!forceDuplicateReplicationConvertFile.exists()) {
            assertTrue(forceDuplicateReplicationConvertFile.createNewFile());
        }
        forceDuplicateReplicationConvertFile.deleteOnExit();
        assertTrue(centralConfigService.isInterested(source, target));
    }

    @Test
    public void updateLicenseIfNeededNonAolMode() {
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ContextHelper.get().beanForType(AolService.class).isAol();
        assertFalse(centralConfigService.updateLicenseIfNeeded(null));
    }

    @Test
    public void updateLicenseIfNeededWhenDescriptorIsNull() {
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ContextHelper.get().beanForType(AolService.class).isAol();
        assertFalse(centralConfigService.updateLicenseIfNeeded(null));
    }

    @Test
    public void updateLicenseIfNeededWithoutSystemProperties() {
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ContextHelper.get().beanForType(AolService.class).isAol();

        MutableCentralConfigDescriptor centralConfigDescriptor = new CentralConfigDescriptorImpl();
        assertFalse(centralConfigService.updateLicenseIfNeeded(centralConfigDescriptor));
    }

    @Test
    public void updateLicenseIfNeededWithoutLicenseSystemProperty() {
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ContextHelper.get().beanForType(AolService.class).isAol();

        MutableCentralConfigDescriptor centralConfigDescriptor = new CentralConfigDescriptorImpl();
        centralConfigDescriptor.setSystemProperties(List.of(new SystemPropertyDescriptor("key", "value")));
        assertFalse(centralConfigService.updateLicenseIfNeeded(centralConfigDescriptor));
    }

    @Test
    public void updateLicenseIfNeededWithLicenseSystemProperty() {
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ContextHelper.get().beanForType(AolService.class).isAol();

        MutableCentralConfigDescriptor descriptor = new CentralConfigDescriptorImpl();
        SystemPropertyDescriptor licenseProperty =
                new SystemPropertyDescriptor(ConstantValues.aolSubscriptionType.getPropertyName(),
                        SubscriptionType.FREE_XRAY.getString());
        List<SystemPropertyDescriptor> list = new ArrayList<>();
        list.add(licenseProperty);
        descriptor.setSystemProperties(list);

        homeStub.getArtifactoryProperties().setProperty(ConstantValues.aolSubscriptionType.getPropertyName(),
                SubscriptionType.ENTERPRISE_XRAY_TEAM.getString());
        assertTrue(centralConfigService.updateLicenseIfNeeded(descriptor));

        Optional<SystemPropertyDescriptor> property = StreamSupportUtils.stream(descriptor.getSystemProperties())
                .filter(prop -> prop.getKey().equalsIgnoreCase(ConstantValues.aolSubscriptionType.getPropertyName()))
                .findFirst();

        assertTrue(property.isPresent());
        assertTrue(property.get().getValue().equalsIgnoreCase(SubscriptionType.ENTERPRISE_XRAY_TEAM.getString()));
    }

    @Test
    public void prepareToSendDataToAccessOnAolSkipsSetAllProxies() {
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        clearInvocations(spyAccessConfigHolder);

        MutableCentralConfigDescriptor descriptor = centralConfigService.getMutableDescriptor();
        descriptor.setProxies(List.of(getProxyDescriptor()));
        centralConfigService.saveEditedDescriptorAndReload(descriptor);

        // proxies are on-prem-only: AOL must NOT stage proxies for mirroring to Access
        verify(spyAccessConfigHolder, never()).setAllProxies(any());
        // but the default-proxy push to crowd.proxy.setting is still staged unconditionally
        verify(spyAccessConfigHolder, atLeast(1)).setProxySettings(any());
    }

    @Test
    public void prepareToSendDataToAccessOnPremStagesAllProxies() {
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        clearInvocations(spyAccessConfigHolder);

        MutableCentralConfigDescriptor descriptor = centralConfigService.getMutableDescriptor();
        List<ProxyDescriptor> proxies = List.of(getProxyDescriptor());
        descriptor.setProxies(proxies);
        centralConfigService.saveEditedDescriptorAndReload(descriptor);

        verify(spyAccessConfigHolder, atLeast(1)).setAllProxies(proxies);
    }

    @Test
    public void prepareToSendDataToAccessOnPremStagesEmptyProxyList() {
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        clearInvocations(spyAccessConfigHolder);

        MutableCentralConfigDescriptor descriptor = centralConfigService.getMutableDescriptor();
        descriptor.setProxies(List.of());
        centralConfigService.saveEditedDescriptorAndReload(descriptor);

        // empty descriptor proxy list is still staged so Access mirror can be cleared
        verify(spyAccessConfigHolder, atLeast(1)).setAllProxies(List.of());
    }

    @Test
    void getServiceVersionInfoReturnsCorrectVersionInfo() {
        String SERVICE_VERSION = "2.2.2";
        String SERVICE_REVISION = "222";
        String SERVICE_PACKAGE_HANDLER_VERSION = "2.500.0";
        homeStub.getArtifactoryProperties()
                .setProperty(ConstantValues.artifactoryServiceVersion.getPropertyName(),
                        SERVICE_VERSION);
        homeStub.getArtifactoryProperties()
                .setProperty(ConstantValues.artifactoryServiceRevision.getPropertyName(),
                        SERVICE_REVISION);
        homeStub.getArtifactoryProperties()
                .setProperty(ConstantValues.artifactoryServicePackageHandlerVersion.getPropertyName(),
                        SERVICE_PACKAGE_HANDLER_VERSION);

        VersionInfo expectedVersionInfo =
                new VersionInfo(SERVICE_VERSION, SERVICE_REVISION, SERVICE_PACKAGE_HANDLER_VERSION);
        VersionInfo actualVersionInfo = centralConfigService.getServiceVersionInfo();
        Assert.assertEquals(expectedVersionInfo, actualVersionInfo);
        Assert.assertEquals(expectedVersionInfo.toString(), actualVersionInfo.toString());
    }

    private void assertRemoteReplications(MutableCentralConfigDescriptor mutableDescriptor,
            RemoteReplicationDescriptor remoteReplicationDescriptor2) {
        List<RemoteReplicationDescriptor> remoteReplications = mutableDescriptor.getRemoteReplications();
        assertEquals(remoteReplications.size(), 1);
        assertEquals(remoteReplications.get(0), remoteReplicationDescriptor2);
    }

    private void assertLocalReplications(MutableCentralConfigDescriptor mutableDescriptor,
            LocalRepoDescriptor localRepoDescriptor,
            LocalReplicationDescriptor localReplicationDescriptor,
            LocalReplicationDescriptor localReplicationDescriptor2,
            LocalReplicationDescriptor localReplicationDescriptor3,
            LocalReplicationDescriptor localReplicationDescriptor4) {
        Map<String, Map<String, LocalReplicationDescriptor>> localReplicationsMap =
                mutableDescriptor.getLocalReplicationsMap();
        assertEquals(localReplicationsMap.size(), 1);
        assertNull(localReplicationsMap.get(localReplicationDescriptor4.getRepoKey()));
        Map<String, LocalReplicationDescriptor> replicationPerUrl =
                localReplicationsMap.get(localRepoDescriptor.getKey());
        assertEquals(replicationPerUrl.size(), 2);
        assertEquals(replicationPerUrl.get(localReplicationDescriptor2.getUrl()), localReplicationDescriptor2);
        assertEquals(replicationPerUrl.get(localReplicationDescriptor3.getUrl()), localReplicationDescriptor3);
        assertNotEquals(replicationPerUrl.get(localReplicationDescriptor.getUrl()), localReplicationDescriptor);
    }

    @Nonnull
    private HttpRepoDescriptor createRemoteRepo(MutableCentralConfigDescriptor mutableDescriptor) {
        HttpRepoDescriptor httpRepoDescriptor = new HttpRepoDescriptor();
        httpRepoDescriptor.setUrl("https://remote.com");
        httpRepoDescriptor.setType(RepoType.Docker);
        httpRepoDescriptor.setKey("remote-repo");
        mutableDescriptor.addRemoteRepository(httpRepoDescriptor);
        return httpRepoDescriptor;
    }

    @Nonnull
    private LocalRepoDescriptor createLocalRepo(MutableCentralConfigDescriptor mutableDescriptor) {
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setKey("local");
        localRepoDescriptor.setType(RepoType.Docker);
        mutableDescriptor.addLocalRepository(localRepoDescriptor);
        return localRepoDescriptor;
    }

    @Nonnull
    private RemoteReplicationDescriptor createRemoteReplication(MutableCentralConfigDescriptor mutableDescriptor,
            String key, String s, String replica1) {
        RemoteReplicationDescriptor remoteReplicationDescriptor2 = new RemoteReplicationDescriptor();
        remoteReplicationDescriptor2.setRepoKey(key);
        remoteReplicationDescriptor2.setCronExp(s);
        remoteReplicationDescriptor2.setReplicationKey(replica1);
        mutableDescriptor.addRemoteReplication(remoteReplicationDescriptor2);
        return remoteReplicationDescriptor2;
    }

    @Nonnull
    private LocalReplicationDescriptor createLocalReplication(MutableCentralConfigDescriptor mutableDescriptor,
            String key, String url, String cron) {
        LocalReplicationDescriptor localReplicationDescriptor = new LocalReplicationDescriptor();
        localReplicationDescriptor.setUrl(url);
        localReplicationDescriptor.setCronExp(cron);
        localReplicationDescriptor.setRepoKey(key);
        localReplicationDescriptor.setReplicationKey(key + url + cron);
        mutableDescriptor.addLocalReplication(localReplicationDescriptor);
        return localReplicationDescriptor;
    }

    @Nonnull
    private CompoundVersionDetails createVersion() {
        return new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.values()[ArtifactoryServiceVersionProvider.values().length - 1].get(),
                "build", 9);
    }

    @Test
    public void importFromHybrid() throws IOException {
        InputStream is = getClass().getResourceAsStream("artifactory.config.valid.with.ldap.xml");
        String defaultConfig = IOUtils.toString(is, Charset.defaultCharset());
        FileUtils.write(ArtifactoryHome.get().getArtifactoryConfigFile(), defaultConfig);
        CentralConfigServiceImpl spy = spy(centralConfigService);
        ImportSettings importSettings = Mockito.mock(ImportSettings.class);
        when(importSettings.getImportKind()).thenReturn(ImportKind.HYBRID_MIGRATION);
        when(importSettings.getStatusHolder()).thenReturn(Mockito.mock(MutableStatusHolder.class));
        when(importSettings.getBaseDir()).thenReturn(ArtifactoryHome.get().getEtcDir());
        spy.importFrom(importSettings);
        List<LdapGroupSetting> ldapGroupSettings = spyAccessConfigHolder.drainLdapGroupSetting();
        assertEquals(1, ldapGroupSettings.size());
        List<LdapSetting> ldapSettings = spyAccessConfigHolder.drainLdapSetting();
        assertEquals(2, ldapSettings.size());
        verify(spy).sanitizeAndSetConfigXml(any(), any());
        verify(accessService, atLeast(1)).updateConfigurationToAccess();
        verify(platformConfigService, times(2)).updatePlatformConfigIfNeeded();
    }

    @Test
    public void importFromStandard() throws IOException {
        InputStream is = getClass().getResourceAsStream("artifactory.config.valid.with.ldap.xml");
        String defaultConfig = IOUtils.toString(is, Charset.defaultCharset());
        FileUtils.write(ArtifactoryHome.get().getArtifactoryConfigFile(), defaultConfig);
        CentralConfigServiceImpl spy = spy(centralConfigService);
        ImportSettings importSettings = Mockito.mock(ImportSettings.class);
        when(importSettings.getImportKind()).thenReturn(ImportKind.STANDARD);
        when(importSettings.getStatusHolder()).thenReturn(Mockito.mock(MutableStatusHolder.class));
        when(importSettings.getBaseDir()).thenReturn(ArtifactoryHome.get().getEtcDir());
        spy.importFrom(importSettings);
        List<LdapGroupSetting> ldapGroupSettings = spyAccessConfigHolder.drainLdapGroupSetting();
        assertEquals(1, ldapGroupSettings.size());
        List<LdapSetting> ldapSettings = spyAccessConfigHolder.drainLdapSetting();
        assertEquals(2, ldapSettings.size());
        verify(spy, never()).sanitizeAndSetConfigXml(any(), any());
        verify(accessService, atLeast(1)).updateConfigurationToAccess();
        verify(platformConfigService, times(2)).updatePlatformConfigIfNeeded();
    }

    @Test(dataProvider = "booleanProvider")
    public void testValidMergeAndSaveNewData(boolean shouldValidate) {
        clearInvocations(configValidationService);
        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        MailServerDescriptor mailServerDescriptor = getMailServerDescriptor();
        newDescriptor.setMailServer(mailServerDescriptor);
        centralConfigService.saveEditedDescriptorAndReload(newDescriptor);
        mailServerDescriptor.setUsername("newUsername");
        List<DataDiff<?>> diffs = DiffMerger.diffToDataDiff(
                findDiff(centralConfigService.getMutableDescriptor(), newDescriptor).getDiffs());
        centralConfigService.mergeAndSaveNewData(diffs, shouldValidate);
        assertEquals(centralConfigService.getMutableDescriptor().getMailServer().getUsername(), "newUsername");
        if (!shouldValidate) {
            verify(configValidationService, never()).validate(any());
        }
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "Found config " +
            "violations: Unicode is not supported")
    public void testInvalidMergeAndSaveNewData() {
        MutableCentralConfigDescriptor newDescriptor = centralConfigService.getMutableDescriptor();
        MailServerDescriptor mailServerDescriptor = getMailServerDescriptor();
        newDescriptor.setMailServer(mailServerDescriptor);
        centralConfigService.saveEditedDescriptorAndReload(newDescriptor);
        mailServerDescriptor.setUsername("invalid\u0007username");
        List<DataDiff<?>> diffs = DiffMerger.diffToDataDiff(
                findDiff(centralConfigService.getMutableDescriptor(), newDescriptor).getDiffs());
        centralConfigService.mergeAndSaveNewData(diffs, true);
    }

    @Test
    public void testGetServiceProxyByKeyReturnProxy() {
        ProxyDescriptor proxyDescriptor = getProxyDescriptor();
        CentralConfigDescriptor descriptor = centralConfigService.getDescriptor();
        descriptor.getProxies().add(proxyDescriptor);
        ProxyDescriptor serviceProxyByKey = centralConfigService.getServiceProxyByKey(proxyDescriptor.getServices());
        assertNotNull(serviceProxyByKey);
        assertEquals(proxyDescriptor.getKey(), serviceProxyByKey.getKey());
    }

    @Test
    public void testGetServiceProxyByKeyReturnProxyDefault() {
        ProxyDescriptor proxyDescriptor = getProxyDescriptor();
        proxyDescriptor.setPlatformDefault(true);
        CentralConfigDescriptor descriptor = centralConfigService.getDescriptor();
        descriptor.getProxies().add(proxyDescriptor);
        ProxyDescriptor serviceProxyByKey = centralConfigService.getServiceProxyByKey(proxyDescriptor.getServices());
        assertNotNull(serviceProxyByKey);
        assertEquals(proxyDescriptor.getKey(), serviceProxyByKey.getKey());
    }

    @Test
    public void testGetServiceProxyByKeyReturnNull() {
        ProxyDescriptor serviceProxyByKey = centralConfigService.getServiceProxyByKey("key");
        assertNull(serviceProxyByKey);
    }

    @Test
    public void testValidateConfig() {
        MutableCentralConfigDescriptor configDescriptor = centralConfigService.getMutableDescriptor();
        SecurityDescriptor securityDescriptor = new SecurityDescriptor();
        CentralOAuthProviders centralOAuthProviders = new CentralOAuthProviders();
        String invalidXmlCharacter = "\u000B";
        centralOAuthProviders.setEnabledProviderNames(List.of(invalidXmlCharacter, "Google"));
        securityDescriptor.setCentralOAuthProviders(centralOAuthProviders);
        configDescriptor.setSecurity(securityDescriptor);
        final Throwable throwable = expectThrows(
                Throwable.class,
                () -> centralConfigService.validateDescriptor(configDescriptor)
        );
        assertEquals(throwable.getMessage(), "Found config violations: " +
                "An invalid XML character (Unicode: 0xb) was found in the element content of the document.");
    }

    @Nonnull
    private static MailServerDescriptor getMailServerDescriptor() {
        MailServerDescriptor mailServerDescriptor = new MailServerDescriptor();
        mailServerDescriptor.setUsername("username");
        mailServerDescriptor.setPassword("password");
        mailServerDescriptor.setFrom("from");
        mailServerDescriptor.setHost("host");
        mailServerDescriptor.setPort(50);
        return mailServerDescriptor;
    }

    private static ProxyDescriptor getProxyDescriptor() {
        ProxyDescriptor proxy = new ProxyDescriptor();
        proxy.setHost("localhost");
        proxy.setKey("proxy");
        proxy.setPort(8987);
        proxy.setServices("artifactory");
        return proxy;
    }

    private void initConfig() {
        MutableCentralConfigDescriptor config = centralConfigService.getMutableDescriptor();
        centralConfigService.saveEditedDescriptorAndReload(config);
        assertNotEquals(centralConfigService.getDescriptor().getRevision(), 0);

        config = centralConfigService.getMutableDescriptor();
        centralConfigService.saveEditedDescriptorAndReload(config);
        assertNotEquals(centralConfigService.getDescriptor().getRevision() > config.getRevision(),
                config.getRevision() + " " + centralConfigService.getDescriptor().getRevision());
        assertNotEquals(centralConfigService.getDescriptor().getRevision(), 1);
    }

    private void saveWithOrder(boolean inOrder, MutableCentralConfigDescriptor newDescriptor,
            MutableCentralConfigDescriptor newDescriptor2) {
        if (inOrder) {
            centralConfigService.saveEditedDescriptorAndReload(newDescriptor);
            centralConfigService.saveEditedDescriptorAndReload(newDescriptor2);
        } else {
            centralConfigService.saveEditedDescriptorAndReload(newDescriptor2);
            centralConfigService.saveEditedDescriptorAndReload(newDescriptor);
        }
    }

    private void initCache(CentralConfigServiceImpl centralConfigService,
            MutableCentralConfigDescriptor configDescriptor) {
        VersioningCacheImpl<CentralConfigServiceImpl.CentralConfigDescriptorCache> cache =
                new VersioningCacheImpl<>(3000,
                        () -> new CentralConfigServiceImpl.CentralConfigDescriptorCache(configDescriptor,
                                configDescriptor.getServerName()), "centralConfigDescriptorCache");
        ReflectionTestUtils.setField(centralConfigService, "descriptorCache", cache);
    }

    private MutableCentralConfigDescriptor getConfigDescriptor() {
        MutableCentralConfigDescriptor cc = new CentralConfigDescriptorImpl();
        cc.setServerName("mymy");

        ProxyDescriptor proxy = getProxyDescriptor();
        cc.setProxies(Arrays.asList(proxy));

        // property sets
        PropertySet propSet = new PropertySet();
        propSet.setName("propSet1");
        Property prop = new Property();
        prop.setName("prop1");
        PredefinedValue value1 = new PredefinedValue();
        value1.setValue("value1");
        prop.addPredefinedValue(value1);
        PredefinedValue value2 = new PredefinedValue();
        value2.setValue("value2");
        prop.addPredefinedValue(value2);
        propSet.addProperty(prop);
        cc.addPropertySet(propSet);

        return cc;
    }
}
