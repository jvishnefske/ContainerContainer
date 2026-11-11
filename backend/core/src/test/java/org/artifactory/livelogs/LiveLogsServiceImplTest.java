package org.artifactory.livelogs;

import com.google.common.collect.Lists;
import com.jfrog.sysconf.SysConfig;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.missioncontrol.MissionControlService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.model.ArtifactoryServerRole;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.artifactory.livelogs.LiveLogsServiceImpl.ENABLED_PROP_NAME;
import static org.artifactory.livelogs.LiveLogsServiceImpl.LOGS_WHITELIST;
import static org.artifactory.livelogs.LiveLogsServiceImpl.READ_BLOCK_SIZE_KB_PROP_NAME;
import static org.artifactory.livelogs.LiveLogsServiceImpl.SERVICE_NAMES;
import static org.jfrog.common.util.SystemLogsUtil.DEFAULT_ENABLED;
import static org.jfrog.common.util.SystemLogsUtil.DEFAULT_READ_BLOCK_SIZE_KB;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class LiveLogsServiceImplTest extends ArtifactoryHomeBoundTest {

    public static final String ARTIFACTORY_SERVICE_LOG = "artifactory-service.log";
    public static final String ARTIFACTORY_REQUEST_LOG = "artifactory-request.log";
    public static final String ARTIFACTORY_TRAFFIC_LOG = "artifactory-traffic-v2.log";
    public static final String EVIDENCE_SERVICE_LOG = "evidence-service.log";
    public static final String EVIDENCE_REQUEST_LOG = "evidence-request.log";
    public static final String JFCONFIG_SERVICE_LOG = "jfconfig-service.log";
    public static final String JFCONFIG_REQUEST_LOG = "jfconfig-request.log";
    public static final String JFMELT_SERVICE_LOG = "jfmelt-service.log";
    public static final String JFMELT_REQUEST_LOG = "jfmelt-request.log";
    public static final String JFBUS_SERVICE_LOG = "jfbus-service.log";
    public static final String JFBUS_REQUEST_LOG = "jfbus-request.log";
    public static final String ONEMODEL_SERVICE_LOG = "onemodel-service.log";
    public static final String ONEMODEL_REQUEST_LOG = "onemodel-request.log";
    public static final String MC_SERVICE_LOG = "mc-service.log";
    public static final String MC_REQUEST_LOG = "mc-request.log";
    public static final String NODE_ID_1 = "nodeId1";
    public static final String NODE_ID_2 = "nodeId2";

    @Mock
    private ArtifactoryServersCommonService artifactoryServersCommonService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AddonsManager addonsManager;

    private LiveLogsServiceImpl liveLogsService;

    @Mock
    private ArtifactoryContext context;
    @Mock
    private ArtifactoryHome home;
    @Mock
    private SysConfig sysConfig;
    private AutoCloseable mocks;

    @BeforeMethod
    public void beforeMethod() {
        mocks = MockitoAnnotations.openMocks(this);
        File logDir = Mockito.mock(File.class);
        ArtifactoryHome.bind(home);
        ArtifactoryContextThreadBinder.bind(context);
        MissionControlService missionControlService = Mockito.mock(MissionControlService.class);
        FederatedRepoService federatedRepoService = Mockito.mock(FederatedRepoService.class);
        when(missionControlService.isMissionControlEnabled()).thenReturn(false);
        when(federatedRepoService.shouldProcessEventsToRTFS()).thenReturn(false);
        when(context.beanForType(MissionControlService.class)).thenReturn(missionControlService);
        when(context.beanForType(FederatedRepoService.class)).thenReturn(federatedRepoService);
        when(home.getLogDir()).thenReturn(logDir);
        when(ArtifactoryHome.get().getSysConfig()).thenReturn(sysConfig);
        when(sysConfig.getBoolean(ENABLED_PROP_NAME, DEFAULT_ENABLED)).thenReturn(true);
        liveLogsService =
                spy(new LiveLogsServiceImpl(artifactoryServersCommonService, securityService, addonsManager));
        File artifactoryServiceLog = Mockito.mock(File.class);
        when(artifactoryServiceLog.getName()).thenReturn(ARTIFACTORY_SERVICE_LOG);
        when(artifactoryServiceLog.length()).thenReturn(10L);
        when(artifactoryServiceLog.isFile()).thenReturn(true);

        File artifactoryRequestLog = Mockito.mock(File.class);
        when(artifactoryRequestLog.getName()).thenReturn(ARTIFACTORY_REQUEST_LOG);
        when(artifactoryRequestLog.length()).thenReturn(10L);
        when(artifactoryRequestLog.isFile()).thenReturn(true);

        File artifactoryTrafficLog = Mockito.mock(File.class);
        when(artifactoryTrafficLog.getName()).thenReturn(ARTIFACTORY_TRAFFIC_LOG);
        when(artifactoryTrafficLog.length()).thenReturn(10L);
        when(artifactoryTrafficLog.isFile()).thenReturn(true);

        File mcServiceLog = Mockito.mock(File.class);
        when(mcServiceLog.getName()).thenReturn(MC_SERVICE_LOG);
        when(mcServiceLog.length()).thenReturn(10L);
        when(mcServiceLog.isFile()).thenReturn(true);

        File mcRequestLog = Mockito.mock(File.class);
        when(mcRequestLog.getName()).thenReturn(MC_REQUEST_LOG);
        when(mcRequestLog.length()).thenReturn(10L);
        when(mcRequestLog.isFile()).thenReturn(true);

        File evdServiceLog = Mockito.mock(File.class);
        when(evdServiceLog.getName()).thenReturn(EVIDENCE_SERVICE_LOG);
        when(evdServiceLog.length()).thenReturn(10L);
        when(evdServiceLog.isFile()).thenReturn(true);

        File evdRequestLog = Mockito.mock(File.class);
        when(evdRequestLog.getName()).thenReturn(EVIDENCE_REQUEST_LOG);
        when(evdRequestLog.length()).thenReturn(10L);
        when(evdRequestLog.isFile()).thenReturn(true);

        File jfconfigServiceLog = Mockito.mock(File.class);
        when(jfconfigServiceLog.getName()).thenReturn(JFCONFIG_SERVICE_LOG);
        when(jfconfigServiceLog.length()).thenReturn(10L);
        when(jfconfigServiceLog.isFile()).thenReturn(true);

        File jfconfigRequestLog = Mockito.mock(File.class);
        when(jfconfigRequestLog.getName()).thenReturn(JFCONFIG_REQUEST_LOG);
        when(jfconfigRequestLog.length()).thenReturn(10L);
        when(jfconfigRequestLog.isFile()).thenReturn(true);

        File jfmeltServiceLog = Mockito.mock(File.class);
        when(jfmeltServiceLog.getName()).thenReturn(JFMELT_SERVICE_LOG);
        when(jfmeltServiceLog.length()).thenReturn(10L);
        when(jfmeltServiceLog.isFile()).thenReturn(true);

        File jfmeltRequestLog = Mockito.mock(File.class);
        when(jfmeltRequestLog.getName()).thenReturn(JFMELT_REQUEST_LOG);
        when(jfmeltRequestLog.length()).thenReturn(10L);
        when(jfmeltRequestLog.isFile()).thenReturn(true);

        when(logDir.listFiles())
                .thenReturn(
                        new File[] {artifactoryServiceLog, artifactoryRequestLog, artifactoryTrafficLog, mcServiceLog
                                , mcRequestLog, evdServiceLog, evdRequestLog, jfconfigServiceLog, jfconfigRequestLog
                                , jfmeltServiceLog, jfmeltRequestLog});

        ArtifactoryServer rt1 =
                new ArtifactoryServer(NODE_ID_1, 1612800485851L, "URL1", 1, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.MEMBER, 1612800485851L, "7", 324, 234, ArtifactoryRunningMode.HA, "1");
        ArtifactoryServer rt2 =
                new ArtifactoryServer(NODE_ID_2, 1612800485851L, "URL2", 2, ArtifactoryServerState.RUNNING,
                        ArtifactoryServerRole.MEMBER, 1612800485851L, "7", 324, 234, ArtifactoryRunningMode.HA, "2");
        List<ArtifactoryServer> activeMembers = Lists.newArrayList(rt1, rt2);
        when(artifactoryServersCommonService.getActiveMembers()).thenReturn(activeMembers);
    }

    @AfterMethod
    public void afterMethod() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testAssertIdIsNotEmpty_Empty() {
        assertThrows(BadRequestException.class, () -> liveLogsService.assertIdIsNotEmpty(""));
    }

    @Test
    public void testAssertIdIsNotEmpty_Null() {
        assertThrows(BadRequestException.class, () -> liveLogsService.assertIdIsNotEmpty(null));
    }

    @Test
    public void testAssertLiveLogsFeatureIsEnabled_EnterpriseLicense() {
        when(addonsManager.isEntitled(EntitlementType.LIVE_LOGS)).thenReturn(true);
        liveLogsService.assertLiveLogsFeatureIsEnabled();
    }

    @Test
    public void testAssertLiveLogsFeatureIsEnabled_NoEnterpriseLicense() {
        when(addonsManager.isEntitled(EntitlementType.LIVE_LOGS)).thenReturn(false);
        assertThrows(BadRequestException.class, () -> liveLogsService.assertLiveLogsFeatureIsEnabled());
    }

    @Test
    public void testGetLiveLogsConfig() {
        LiveLogsConfig liveLogsConfig = liveLogsService.getLiveLogsConfig();
        List<String> logs = liveLogsConfig.getLogs();
        List<String> nodes = liveLogsConfig.getNodes();
        long refreshRateMillis = liveLogsConfig.getRefreshRateMillis();
        assertEquals(logs.size(), LOGS_WHITELIST.size());
        for (int i = 0; i < LOGS_WHITELIST.size(); i++) {
            assertEquals(logs.get(i), LOGS_WHITELIST.get(i));
        }
        assertEquals(nodes.size(), 2);
        assertEquals(nodes.get(0), NODE_ID_1);
        assertEquals(nodes.get(1), NODE_ID_2);
        assertEquals(refreshRateMillis, 10000L);
        assertTrue(liveLogsConfig.getLogs().contains(EVIDENCE_SERVICE_LOG));
        assertTrue(liveLogsConfig.getLogs().contains(EVIDENCE_REQUEST_LOG));
    }

    @Test
    public void testGetLiveLogsData_forEvidenceLogs() {
        Mockito.doReturn(Collections.emptyList()).when(liveLogsService).getAdditionalSensitiveRegex();

        LiveLogsData data = liveLogsService.getLiveLogsData(EVIDENCE_SERVICE_LOG, 0);
        assertNotNull(data.getLastUpdateModified());
        assertNotNull(data.getLastUpdateLabel());
        assertEquals(data.getLogContent(), "");
        assertEquals(data.getFileSize(), 0);

        data = liveLogsService.getLiveLogsData(EVIDENCE_REQUEST_LOG, 0);
        assertNotNull(data.getLastUpdateModified());
        assertNotNull(data.getLastUpdateLabel());
        assertEquals(data.getLogContent(), "");
        assertEquals(data.getFileSize(), 0);
    }

    @Test
    public void testEvidenceLogWhitelistAndServices() {
        assertTrue(LOGS_WHITELIST.contains(EVIDENCE_SERVICE_LOG));
        assertTrue(LOGS_WHITELIST.contains(EVIDENCE_REQUEST_LOG));
        assertTrue(SERVICE_NAMES.contains("evidence"));
    }

    @Test
    public void testGetLiveLogsData() {
        Mockito.doReturn(Collections.emptyList()).when(liveLogsService).getAdditionalSensitiveRegex();

        LiveLogsData liveLogsData = liveLogsService.getLiveLogsData(ARTIFACTORY_SERVICE_LOG, 0);
        assertNotNull(liveLogsData.getLastUpdateModified());
        assertNotNull(liveLogsData.getLastUpdateLabel());
        assertEquals(liveLogsData.getLogContent(), "");
        assertEquals(liveLogsData.getFileSize(), 0);

        liveLogsData = liveLogsService.getLiveLogsData(ARTIFACTORY_REQUEST_LOG, 0);
        assertNotNull(liveLogsData.getLastUpdateModified());
        assertNotNull(liveLogsData.getLastUpdateLabel());
        assertEquals(liveLogsData.getLogContent(), "");
        assertEquals(liveLogsData.getFileSize(), 0);

        liveLogsData = liveLogsService.getLiveLogsData(ARTIFACTORY_TRAFFIC_LOG, 0);
        assertNotNull(liveLogsData.getLastUpdateModified());
        assertNotNull(liveLogsData.getLastUpdateLabel());
        assertEquals(liveLogsData.getLogContent(), "");
        assertEquals(liveLogsData.getFileSize(), 0);

        liveLogsData = liveLogsService.getLiveLogsData(MC_SERVICE_LOG, 0);
        assertNotNull(liveLogsData.getLastUpdateModified());
        assertNotNull(liveLogsData.getLastUpdateLabel());
        assertEquals(liveLogsData.getLogContent(), "");
        assertEquals(liveLogsData.getFileSize(), 0);

        liveLogsData = liveLogsService.getLiveLogsData(MC_REQUEST_LOG, 0);
        assertNotNull(liveLogsData.getLastUpdateModified());
        assertNotNull(liveLogsData.getLastUpdateLabel());
        assertEquals(liveLogsData.getLogContent(), "");
        assertEquals(liveLogsData.getFileSize(), 0);
    }

    @Test
    public void testNoMissionControlRunning() {
        MissionControlService missionControlService = Mockito.mock(MissionControlService.class);
        when(missionControlService.isMissionControlEnabled()).thenReturn(false);
        when(context.beanForType(MissionControlService.class)).thenReturn(missionControlService);

        assertEquals(LOGS_WHITELIST.stream().filter(logName -> logName.startsWith("mc")).count(), 0);
        assertEquals(SERVICE_NAMES.stream().filter(logName -> logName.startsWith("mc")).count(), 0);
    }

    @Test(dependsOnMethods = "testNoMissionControlRunning")
    public void testMissionControlRunning() {
        MissionControlService missionControlService = Mockito.mock(MissionControlService.class);
        when(missionControlService.isMissionControlEnabled()).thenReturn(true);
        when(context.beanForType(MissionControlService.class)).thenReturn(missionControlService);
        liveLogsService.addMCIfNeeded();
        assertEquals(LOGS_WHITELIST.stream().filter(logName -> logName.startsWith("mc")).count(), 4);
        assertEquals(SERVICE_NAMES.stream().filter(logName -> logName.startsWith("mc")).count(), 1);
    }

    @Test
    public void testNoRTFSRunning() {
        liveLogsService.addArtifactoryFederationIfNeeded();
        assertEquals(LOGS_WHITELIST.stream().filter(logName -> logName.startsWith("artifactory-federation"))
                .count(), 0);
        assertEquals(SERVICE_NAMES.stream().filter(logName -> logName.startsWith("rtfs")).count(), 0);
    }

    @Test(dependsOnMethods = "testNoRTFSRunning")
    public void testRTFSRunning() {
        FederatedRepoService federatedRepoService = context.beanForType(FederatedRepoService.class);
        when(federatedRepoService.shouldProcessEventsToRTFS()).thenReturn(true);
        liveLogsService.addArtifactoryFederationIfNeeded();
        assertEquals(LOGS_WHITELIST.stream().filter(logName -> logName.startsWith("artifactory-federation"))
                .count(), 1);
        assertEquals(SERVICE_NAMES.stream().filter(logName -> logName.startsWith("rtfs")).count(), 1);
    }

    @Test
    public void testJFconfigLogWhitelistAndServices() {
        assertTrue(LOGS_WHITELIST.contains(JFCONFIG_SERVICE_LOG));
        assertTrue(LOGS_WHITELIST.contains(JFCONFIG_REQUEST_LOG));
        assertTrue(SERVICE_NAMES.contains("jfconfig"));
    }

    @Test
    public void testJFmeltLogWhitelistAndServices() {
        assertTrue(LOGS_WHITELIST.contains(JFMELT_SERVICE_LOG));
        assertTrue(LOGS_WHITELIST.contains(JFMELT_REQUEST_LOG));
        assertTrue(SERVICE_NAMES.contains("jfmelt"));
    }

    @Test
    public void testJFbusLogsExcludedFromLiveLogs() {
        // jfbus logs are intentionally not exposed in Live Logs (RTDEV-92618, product decision)
        assertFalse(LOGS_WHITELIST.contains(JFBUS_SERVICE_LOG));
        assertFalse(LOGS_WHITELIST.contains(JFBUS_REQUEST_LOG));
        assertFalse(SERVICE_NAMES.contains("jfbus"));
    }

    @Test
    public void testOneModelLogWhitelistAndServices() {
        assertTrue(LOGS_WHITELIST.contains(ONEMODEL_SERVICE_LOG));
        assertTrue(LOGS_WHITELIST.contains(ONEMODEL_REQUEST_LOG));
        assertTrue(SERVICE_NAMES.contains("onemodel"));
        assertFalse(LOGS_WHITELIST.contains("onemodelregistry-service.log"));
        assertFalse(SERVICE_NAMES.contains("onemodelregistry"));
    }

    @Test(dataProvider = "blockSizeProvider")
    public void testAssertLiveLogsBlockSize(int readBlockSize, int expectedSize) {
        when(sysConfig.getInt(READ_BLOCK_SIZE_KB_PROP_NAME, DEFAULT_READ_BLOCK_SIZE_KB)).thenReturn(readBlockSize);
        assertEquals(liveLogsService.getReadBlockSizeFromSystemYaml(), expectedSize);
    }

    @DataProvider
    private static Object[][] blockSizeProvider() {
        return new Object[][] {
                {-1, DEFAULT_READ_BLOCK_SIZE_KB},
                {0, DEFAULT_READ_BLOCK_SIZE_KB},
                {100, 100},
                {400, 400}
        };
    }
}
