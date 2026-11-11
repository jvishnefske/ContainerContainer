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

package org.artifactory.metrics.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.HttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.callhome.CallHomeRequest;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.VersionInfo;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.metrics.callhome.CallHomeMetrics;
import org.artifactory.metrics.providers.features.CallHomeFeature;
import org.artifactory.metrics.providers.features.ThirdPartyFirewallGuards;
import org.artifactory.repo.interceptor.modle.RemoteRepoOfflineTriggersConfig;
import org.artifactory.request.guard.InboundRequestGuardService;
import org.artifactory.request.guard.model.InboundRequestGuardTriggersConfig;
import org.artifactory.security.access.AccessService;
import org.artifactory.service.RemoteRepoOfflineGuardService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.db.DbMetaData;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.jfrog.access.common.ServiceId;
import org.jfrog.storage.DbType;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Mayost
 */
public class CallHomeServiceTest {

    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private ArtifactoryApplicationContext artifactoryContext;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    private CentralConfigDescriptor centralConfigDescriptor;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private CentralConfigService configService;
    @Mock
    private ArtifactoryServersCommonService serversService;
    @Mock
    private AccessService accessService;
    @Mock
    private DbService dbService;
    @Mock
    private ArtifactoryDbProperties dbProperties;
    @Mock
    private AolService aolService;

    private CallHomeService callHomeService;
    private AutoCloseable closable;

    @BeforeMethod
    public void beforeMethod() {
        this.closable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        ArtifactoryHome.bind(artifactoryHome);
        callHomeService =
                spy(new CallHomeService(addonsManager, configService, serversService, accessService, dbService,
                        dbProperties, aolService));
    }

    @AfterMethod
    public void afterMethod() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        this.closable.close();
    }

    @Test(dataProvider = "accountUrlExpectedAccountName")
    void checkCallHomeAccountName(String accountUrl, String expectedAccountName) {
        assertEquals(callHomeService.getAccountName(accountUrl), expectedAccountName);
    }

    @DataProvider
    private static Object[][] accountUrlExpectedAccountName() {
        return new Object[][] {
                {"https://bezirk.jfrog.io/bezirk/webapp/", "bezirk"},
                {"https://localhost:8080/artifactory/webapp/", "N/A"},
                {null, "N/A"}
        };
    }

    @Test(dataProvider = "testShouldExecuteCallHomeDataProvider")
    void testShouldExecuteCallHome(boolean isVersionQueryEnabled, boolean isOfflineMode, boolean isAol,
            boolean expectedShouldExecuteCallHome) {
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.versionQueryEnabled))
                .thenReturn(isVersionQueryEnabled);
        when(configService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.isOfflineMode()).thenReturn(isOfflineMode);
        when(aolService.isAol()).thenReturn(isAol);
        assertEquals(callHomeService.shouldExecuteCallHome(), expectedShouldExecuteCallHome);
    }

    @DataProvider
    private static Object[][] testShouldExecuteCallHomeDataProvider() {
        return new Object[][] {
                {true, true, true, true},
                {true, true, false, false},
                {true, false, true, true},
                {true, false, false, true},
                {false, true, true, false},
                {false, true, false, false},
                {false, false, true, false},
                {false, false, false, false}
        };
    }

    @Test
    public void testGetFeatureException() throws Exception {

        //Given
        Appender mockedAppender = Mockito.mock(Appender.class);
        ArgumentCaptor<LoggingEvent> loggingEventCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
        Logger root = (Logger) LoggerFactory.getLogger(CallHomeService.class);
        root.addAppender(mockedAppender);
        root.setLevel(Level.ERROR);

        CentralConfigService centralConfigService = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor descriptor = Mockito.mock(CentralConfigDescriptor.class);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        when(accessService.getArtifactoryServiceId())
                .thenReturn(new ServiceId(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        AolService aolService = Mockito.mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(artifactoryContext.beanForType(eq(AolService.class))).thenReturn(aolService);
        when(addonsManager.addonByType(eq(HaCommonAddon.class))).thenReturn(Mockito.mock(HaCommonAddon.class));
        when(dbService.getDatabaseType()).thenReturn(DbType.POSTGRESQL);
        DbMetaData dbMetadata = Mockito.mock(DbMetaData.class);
        when(dbMetadata.getProductVersion()).thenReturn("9.6");
        when(dbService.getDbMetaData()).thenReturn(dbMetadata);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(configService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(configService.getMutableDescriptor()).thenReturn(Mockito.mock(MutableCentralConfigDescriptor.class));
        VersionInfo versionInfo = new VersionInfo("7.x-SNAPSHOT", "2147483647");
        when(configService.getProductVersionInfo()).thenReturn(versionInfo);
        when(centralConfigDescriptor.isOfflineMode()).thenReturn(false);
        when(aolService.isAol()).thenReturn(true);

        CallHomeService service =
                new CallHomeService(addonsManager, configService, serversService, accessService, dbService,
                        dbProperties, aolService) {
                    @Override
                    CloseableHttpClient createHttpClient() {
                        return Mockito.mock(CloseableHttpClient.class);
                    }

                    @Override
                    boolean shouldExecuteCallHome() {
                        return true;
                    }
                };

        ((List<CallHomeFeature>) FieldUtils.readField(service, "callHomeFeatures", true))
                .add(() -> {
                    throw new RuntimeException("Get feature error !");
                });

        CallHomeMetrics callHomeMetrics = Mockito.mock(CallHomeMetrics.class);
        when(callHomeMetrics.getCallHomeData()).thenReturn(new ConcurrentHashMap<>());
        FieldUtils.writeField(service, "callHomeMetrics", callHomeMetrics, true);

        //When
        service.callHome();

        //Then
        verify(mockedAppender, times(1)).doAppend(loggingEventCaptor.capture());
        Assert.assertEquals(loggingEventCaptor.getValue().getLevel(), Level.ERROR);
        Assert.assertTrue(loggingEventCaptor.getValue().getMessage().startsWith("Failed to get feature from: "));
    }

    @Test
    void testAddLinuxDistributionAttributesIfNeeded() {
        CallHomeRequest request = new CallHomeRequest();
        String osReleaseContent =
                "NAME=\"Ubuntu\"\nPRETTY_NAME=\"Ubuntu 20.04.2 LTS\"\nVERSION=\"20.04.2 LTS (Focal Fossa)\"";
        try (BufferedReader bufferedReader = new BufferedReader(new StringReader(osReleaseContent))) {
            CallHomeService callHomeService = getCallHomeService(bufferedReader);
            callHomeService.addLinuxDistributionAttributesIfNeeded(request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals("Ubuntu", request.environment.attributes.osDistribution);
        assertEquals("Ubuntu 20.04.2 LTS", request.environment.attributes.osPrettyName);
        assertEquals("20.04.2 LTS (Focal Fossa)", request.environment.attributes.osVersion);
    }

    private CallHomeService getCallHomeService(BufferedReader bufferedReader) {
        return new CallHomeService(addonsManager, configService, serversService, accessService, dbService,
                dbProperties, aolService) {
            @Override
            BufferedReader createBufferedReader() {
                return bufferedReader;
            }

            @Override
            boolean isLinux() {
                return true;
            }
        };
    }

    /**
     * End-to-end shape of the guards section in the serialized call-home payload. Invokes the private
     * callHomeEntity() to drive the same path the HTTP post would, then parses the JSON back and asserts the
     * guards FeatureGroup is wired in with the expected children, modes, vendors, and offlined count. Prints the
     * full payload to stdout so the response shape is visible when running the test locally.
     */
    @Test
    public void callHomePayloadIncludesThirdPartyFirewallGuardsFeatureGroup() throws Exception {
        stubCallHomeEnvForIntegrationTest();
        CallHomeService service = newIntegrationCallHomeService();
        wireThirdPartyFirewallGuardsWithRepresentativeData(service);
        stubEmptyMetrics(service);

        String json = invokeCallHomeEntityAndReadJson(service);

        // Helpful when running locally — surfaces the full payload so reviewers can eyeball the wire format.
        System.out.println("=== Call home JSON ===\n" + prettyPrint(json) + "\n=== end ===");

        JsonNode guards = findThirdPartyFirewallGuardsGroup(json);
        assertNotNull(guards, "third_party_firewall_guards feature_group must be present in the call-home payload");

        JsonNode remote = findChildByName(guards, "remote_repo_offline_guard");
        assertNotNull(remote, "remote_repo_offline_guard child must exist");
        JsonNode remoteAttrs = remote.path("attributes");
        assertEquals("PFW3_NEW", remoteAttrs.path("mode").asText());
        assertEquals(3, remoteAttrs.path("offlined_repos_count").asInt());
        JsonNode remoteVendors = remoteAttrs.path("vendors");
        assertEquals(2, remoteVendors.size(), "expected socket + endor vendors, got " + remoteVendors);

        JsonNode inbound = findChildByName(guards, "inbound_request_guard");
        assertNotNull(inbound, "inbound_request_guard child must exist");
        JsonNode inboundAttrs = inbound.path("attributes");
        assertEquals("PFW3_PROBE", inboundAttrs.path("mode").asText());
        assertEquals(1, inboundAttrs.path("vendors").size(), "expected single socket vendor for inbound");
        // The inbound guard has no marker concept — call home must not advertise an offlined count for it.
        Assert.assertTrue(inboundAttrs.path("offlined_repos_count").isMissingNode(),
                "inbound guard must not expose offlined_repos_count");
    }

    private void stubCallHomeEnvForIntegrationTest() throws Exception {
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        when(accessService.getArtifactoryServiceId())
                .thenReturn(new ServiceId(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        when(aolService.isAol()).thenReturn(false);
        when(artifactoryContext.beanForType(eq(AolService.class))).thenReturn(aolService);
        when(addonsManager.addonByType(eq(HaCommonAddon.class))).thenReturn(mock(HaCommonAddon.class));
        when(dbService.getDatabaseType()).thenReturn(DbType.POSTGRESQL);
        DbMetaData dbMetadata = mock(DbMetaData.class);
        when(dbMetadata.getProductVersion()).thenReturn("16.0");
        when(dbService.getDbMetaData()).thenReturn(dbMetadata);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(configService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(configService.getMutableDescriptor()).thenReturn(mock(MutableCentralConfigDescriptor.class));
        when(configService.getProductVersionInfo()).thenReturn(new VersionInfo("7.x-SNAPSHOT", "2147483647"));
        when(centralConfigDescriptor.isOfflineMode()).thenReturn(false);
        when(dbProperties.allowNonPgFlag()).thenReturn(Optional.empty());
    }

    private CallHomeService newIntegrationCallHomeService() {
        return new CallHomeService(addonsManager, configService, serversService, accessService, dbService,
                dbProperties, aolService) {
            @Override
            CloseableHttpClient createHttpClient() {
                return mock(CloseableHttpClient.class);
            }

            @Override
            boolean shouldExecuteCallHome() {
                return true;
            }
        };
    }

    private void wireThirdPartyFirewallGuardsWithRepresentativeData(CallHomeService service) throws Exception {
        RemoteRepoOfflineGuardService remoteGuard = mock(RemoteRepoOfflineGuardService.class);
        InboundRequestGuardService inboundGuard = mock(InboundRequestGuardService.class);

        RemoteRepoOfflineTriggersConfig remoteTriggers = new RemoteRepoOfflineTriggersConfig();
        remoteTriggers.setMode(RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW);
        remoteTriggers.setUrlSubstrings(Set.of("firewall.socket.dev", "endorlabs.com"));
        remoteTriggers.setHeaderNames(Set.of("x-socket-decision"));
        when(remoteGuard.getRemoteRepoOfflineGuardTriggersConfig()).thenReturn(remoteTriggers);
        when(remoteGuard.getOfflinedRepoCount()).thenReturn(3);

        InboundRequestGuardTriggersConfig inboundTriggers = new InboundRequestGuardTriggersConfig();
        inboundTriggers.setMode(InboundRequestGuardTriggersConfig.Mode.PFW3_PROBE);
        inboundTriggers.setHeaderNames(Set.of("x-socket-request-id"));
        when(inboundGuard.getInboundRequestGuardTriggersConfig()).thenReturn(inboundTriggers);

        @SuppressWarnings("unchecked")
        List<CallHomeFeature> features =
                (List<CallHomeFeature>) FieldUtils.readField(service, "callHomeFeatures", true);
        features.add(new ThirdPartyFirewallGuards(remoteGuard, inboundGuard));
    }

    private void stubEmptyMetrics(CallHomeService service) throws Exception {
        CallHomeMetrics callHomeMetrics = mock(CallHomeMetrics.class);
        when(callHomeMetrics.getCallHomeData()).thenReturn(new ConcurrentHashMap<>());
        FieldUtils.writeField(service, "callHomeMetrics", callHomeMetrics, true);
    }

    private static String invokeCallHomeEntityAndReadJson(CallHomeService service) throws Exception {
        Method m = CallHomeService.class.getDeclaredMethod("callHomeEntity");
        m.setAccessible(true);
        HttpEntity entity = (HttpEntity) m.invoke(service);
        return EntityUtils.toString(entity);
    }

    private static JsonNode findThirdPartyFirewallGuardsGroup(String json) throws Exception {
        JsonNode root = new ObjectMapper().readTree(json);
        JsonNode featureGroups = root.path("feature_groups");
        for (JsonNode group : featureGroups) {
            if ("third_party_firewall_guards".equals(group.path("name").asText())) {
                return group;
            }
        }
        return null;
    }

    private static JsonNode findChildByName(JsonNode parent, String name) {
        for (JsonNode child : parent.path("features")) {
            if (name.equals(child.path("name").asText())) {
                return child;
            }
        }
        return null;
    }

    private static String prettyPrint(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json));
    }
}
