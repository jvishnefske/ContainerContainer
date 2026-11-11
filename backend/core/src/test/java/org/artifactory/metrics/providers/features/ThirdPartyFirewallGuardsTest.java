package org.artifactory.metrics.providers.features;

import org.artifactory.api.callhome.FeatureGroup;
import org.artifactory.repo.interceptor.modle.RemoteRepoOfflineTriggersConfig;
import org.artifactory.request.guard.InboundRequestGuardService;
import org.artifactory.request.guard.model.InboundRequestGuardTriggersConfig;
import org.artifactory.service.RemoteRepoOfflineGuardService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Hussein Alshqerat
 */
public class ThirdPartyFirewallGuardsTest {

    private RemoteRepoOfflineGuardService remoteRepoOfflineGuardService;
    private InboundRequestGuardService inboundRequestGuardService;
    private ThirdPartyFirewallGuards feature;

    @BeforeMethod
    public void setUp() {
        remoteRepoOfflineGuardService = mock(RemoteRepoOfflineGuardService.class);
        inboundRequestGuardService = mock(InboundRequestGuardService.class);
        feature = new ThirdPartyFirewallGuards(remoteRepoOfflineGuardService, inboundRequestGuardService);
    }

    @Test
    public void emitsModeAndVendorsAndOfflinedCountForRemoteRepoGuard() {
        stubRemoteGuard(RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW,
                Set.of("firewall.socket.dev", "endorlabs.com"),
                Set.of("x-socket-decision"),
                7);
        stubInboundGuard(InboundRequestGuardTriggersConfig.Mode.PFW3_OFF, Set.of(), Set.of());

        FeatureGroup remote = childByName(feature.getFeature(), "remote_repo_offline_guard");

        assertEquals(remote.getAttributes().get("mode"), "PFW3_NEW");
        assertEquals(remote.getAttributes().get("offlined_repos_count"), 7);
        assertVendors(remote.getAttributes().get("vendors"), "socket", "endor");
    }

    @Test
    public void emitsModeAndVendorsForInboundGuardWithoutCount() {
        stubRemoteGuard(RemoteRepoOfflineTriggersConfig.Mode.PFW3_OFF, Set.of(), Set.of(), 0);
        stubInboundGuard(InboundRequestGuardTriggersConfig.Mode.PFW3_PROBE,
                Set.of("SocketBot/1.0", "endor-agent"),
                Set.of("x-socket-request-id"));

        FeatureGroup inbound = childByName(feature.getFeature(), "inbound_request_guard");

        assertEquals(inbound.getAttributes().get("mode"), "PFW3_PROBE");
        assertNull(inbound.getAttributes().get("offlined_repos_count"),
                "inbound guard has no marked-repos concept");
        assertVendors(inbound.getAttributes().get("vendors"), "socket", "endor");
    }

    @Test
    public void bucketizesUnknownConfigEntriesAsOther() {
        stubRemoteGuard(RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL,
                Set.of("unknown-vendor.example.com"),
                Set.of("x-some-custom-header"),
                0);
        stubInboundGuard(InboundRequestGuardTriggersConfig.Mode.PFW3_OFF, Set.of(), Set.of());

        FeatureGroup remote = childByName(feature.getFeature(), "remote_repo_offline_guard");

        assertVendors(remote.getAttributes().get("vendors"), "other");
    }

    @Test
    public void emitsNullModeWhenTriggersConfigUnavailable() {
        // Defensive path: the service contract returns a non-null config today, but call home runs against live
        // services and should not crash if a future code path or partial init returns null.
        when(remoteRepoOfflineGuardService.getRemoteRepoOfflineGuardTriggersConfig()).thenReturn(null);
        when(remoteRepoOfflineGuardService.getOfflinedRepoCount()).thenReturn(0);
        when(inboundRequestGuardService.getInboundRequestGuardTriggersConfig()).thenReturn(null);

        FeatureGroup remote = childByName(feature.getFeature(), "remote_repo_offline_guard");
        FeatureGroup inbound = childByName(feature.getFeature(), "inbound_request_guard");

        assertNull(remote.getAttributes().get("mode"));
        assertEquals(remote.getAttributes().get("offlined_repos_count"), 0);
        assertEquals(remote.getAttributes().get("vendors"), List.of());
        assertNull(inbound.getAttributes().get("mode"));
        assertEquals(inbound.getAttributes().get("vendors"), List.of());
    }

    @Test
    public void rootFeatureGroupIsNamedThirdPartyFirewallGuardsAndHasBothChildren() {
        stubRemoteGuard(RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW, Set.of(), Set.of(), 0);
        stubInboundGuard(InboundRequestGuardTriggersConfig.Mode.PFW3_OFF, Set.of(), Set.of());

        FeatureGroup root = feature.getFeature();

        assertEquals(readName(root), "third_party_firewall_guards");
        assertNotNull(childByName(root, "remote_repo_offline_guard"));
        assertNotNull(childByName(root, "inbound_request_guard"));
        assertEquals(root.getFeatures().size(), 2);
    }

    private void stubRemoteGuard(RemoteRepoOfflineTriggersConfig.Mode mode, Set<String> urlSubs,
            Set<String> headers, int offlinedCount) {
        RemoteRepoOfflineTriggersConfig cfg = new RemoteRepoOfflineTriggersConfig();
        cfg.setMode(mode);
        cfg.setUrlSubstrings(new HashSet<>(urlSubs));
        cfg.setHeaderNames(new HashSet<>(headers));
        when(remoteRepoOfflineGuardService.getRemoteRepoOfflineGuardTriggersConfig()).thenReturn(cfg);
        when(remoteRepoOfflineGuardService.getOfflinedRepoCount()).thenReturn(offlinedCount);
    }

    private void stubInboundGuard(InboundRequestGuardTriggersConfig.Mode mode, Set<String> userAgentSubs,
            Set<String> headers) {
        InboundRequestGuardTriggersConfig cfg = new InboundRequestGuardTriggersConfig();
        cfg.setMode(mode);
        cfg.setUserAgentSubstrings(new HashSet<>(userAgentSubs));
        cfg.setHeaderNames(new HashSet<>(headers));
        when(inboundRequestGuardService.getInboundRequestGuardTriggersConfig()).thenReturn(cfg);
    }

    @SuppressWarnings("unchecked")
    private static void assertVendors(Object attribute, String... expected) {
        assertNotNull(attribute, "vendors attribute must be set");
        List<String> actual = (List<String>) attribute;
        assertEquals(actual.size(), expected.length, "vendor list size mismatch: " + actual);
        for (String v : expected) {
            assertTrue(actual.contains(v), "expected vendor '" + v + "' in " + actual);
        }
    }

    // FeatureGroup#name is a protected field with no public getter; reflect once to keep tests focused on
    // structure rather than serialization round-tripping.
    private static FeatureGroup childByName(FeatureGroup parent, String name) {
        if (parent.getFeatures() == null) {
            return null;
        }
        return parent.getFeatures().stream()
                .filter(f -> name.equals(readName(f)))
                .findFirst()
                .orElse(null);
    }

    private static String readName(FeatureGroup g) {
        try {
            java.lang.reflect.Field nameField = FeatureGroup.class.getDeclaredField("name");
            nameField.setAccessible(true);
            return (String) nameField.get(g);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
