package org.artifactory.metrics.providers.features;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.callhome.FeatureGroup;
import org.artifactory.repo.interceptor.modle.RemoteRepoOfflineTriggersConfig;
import org.artifactory.request.guard.InboundRequestGuardService;
import org.artifactory.request.guard.model.InboundRequestGuardTriggersConfig;
import org.artifactory.service.RemoteRepoOfflineGuardService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Emits the current state of the third-party-firewall (PFW3) guards — the remote-repo offline guard and the
 * inbound request guard — to call home. For each guard we ship the active mode and the set of canonical vendor
 * names derived from the configured URL substrings / header names ("socket", "endor", "other"). The remote-repo
 * guard additionally ships a count of repos currently marked offline by the guard.
 *
 * @author Hussein Alshqerat
 */
@Component
public class ThirdPartyFirewallGuards implements CallHomeFeature {

    private static final String VENDOR_SOCKET = "socket";
    private static final String VENDOR_ENDOR = "endor";
    private static final String VENDOR_OTHER = "other";

    private final RemoteRepoOfflineGuardService remoteRepoOfflineGuardService;
    private final InboundRequestGuardService inboundRequestGuardService;

    public ThirdPartyFirewallGuards(RemoteRepoOfflineGuardService remoteRepoOfflineGuardService,
            InboundRequestGuardService inboundRequestGuardService) {
        this.remoteRepoOfflineGuardService = remoteRepoOfflineGuardService;
        this.inboundRequestGuardService = inboundRequestGuardService;
    }

    @Override
    public FeatureGroup getFeature() {
        FeatureGroup guards = new FeatureGroup("third_party_firewall_guards");
        guards.addFeature(buildRemoteRepoOfflineGuardGroup());
        guards.addFeature(buildInboundRequestGuardGroup());
        return guards;
    }

    private FeatureGroup buildRemoteRepoOfflineGuardGroup() {
        RemoteRepoOfflineTriggersConfig triggers =
                remoteRepoOfflineGuardService.getRemoteRepoOfflineGuardTriggersConfig();
        FeatureGroup group = new FeatureGroup("remote_repo_offline_guard");
        group.addFeatureAttribute("mode", triggers != null ? triggers.getModeName() : null);
        group.addFeatureAttribute("vendors", deriveVendors(
                triggers != null ? triggers.getUrlSubstrings() : null,
                triggers != null ? triggers.getHeaderNames() : null));
        group.addFeatureAttribute("offlined_repos_count", remoteRepoOfflineGuardService.getOfflinedRepoCount());
        return group;
    }

    private FeatureGroup buildInboundRequestGuardGroup() {
        InboundRequestGuardTriggersConfig triggers =
                inboundRequestGuardService.getInboundRequestGuardTriggersConfig();
        FeatureGroup group = new FeatureGroup("inbound_request_guard");
        group.addFeatureAttribute("mode", triggers != null ? triggers.getModeName() : null);
        group.addFeatureAttribute("vendors", deriveVendors(
                triggers != null ? triggers.getUserAgentSubstrings() : null,
                triggers != null ? triggers.getHeaderNames() : null));
        return group;
    }

    // Canonicalize the configured URL substrings + header names into a small, stable set of vendor labels. Keeps
    // call-home payloads bounded regardless of how many substrings an admin adds, and avoids leaking the verbatim
    // admin-config strings into telemetry. Anything we don't recognize bucketizes as "other".
    private static List<String> deriveVendors(Collection<String> urlOrUaSubstrings, Collection<String> headerNames) {
        Set<String> out = new LinkedHashSet<>();
        addVendors(out, urlOrUaSubstrings);
        addVendors(out, headerNames);
        return out.isEmpty() ? Collections.emptyList() : List.copyOf(out);
    }

    private static void addVendors(Set<String> out, Collection<String> values) {
        if (values == null) {
            return;
        }
        for (String v : values) {
            String label = vendorLabel(v);
            if (label != null) {
                out.add(label);
            }
        }
    }

    private static String vendorLabel(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String[] tokens = value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String t : tokens) {
            if (t.startsWith(VENDOR_SOCKET)) {
                return VENDOR_SOCKET;
            }
            if (t.startsWith(VENDOR_ENDOR)) {
                return VENDOR_ENDOR;
            }
        }
        return VENDOR_OTHER;
    }
}
