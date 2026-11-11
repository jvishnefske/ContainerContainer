package org.artifactory.request.guard;

import com.google.common.annotations.VisibleForTesting;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.aol.AolService;
import org.artifactory.common.ConstantValues;
import org.artifactory.request.guard.model.InboundRequestGuardTriggersConfig;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.util.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.artifactory.storage.fs.service.ConfigsServiceKey.INBOUND_REQUEST_GUARD_TRIGGERS;

/**
 * @author Hussein Alshqerat
 */
@Slf4j
@Service
public class InboundRequestGuardServiceImpl implements InboundRequestGuardService {

    private static final String USER_AGENT_HEADER = "User-Agent";

    private ConfigsService configsService;
    private AddonsManager addonsManager;
    private AolService aolService;

    private final AtomicReference<InboundRequestGuardTriggersConfig> triggers =
            new AtomicReference<>(new InboundRequestGuardTriggersConfig());
    // Parsed IpAddressMatchers, kept in sync with triggers.get().getIpAddresses(). Updated under the same code
    // paths that mutate the cached triggers so per-request shouldBlock() never has to re-parse CIDR strings.
    private volatile List<IpRule> ipRules = List.of();

    @Override
    public void onContextCreated() {
        reloadTriggersFromDb();
        logStartupMode();
    }

    private void logStartupMode() {
        log.info("Inbound request guard initialized: mode={}", triggers.get().getMode());
    }

    @Override
    public InboundRequestGuardTriggersConfig getInboundRequestGuardTriggersConfig() {
        return triggers.get();
    }

    @Override
    public void updateTriggers(InboundRequestGuardTriggersConfig newTriggers) {
        InboundRequestGuardTriggersConfig normalized =
                normalize((newTriggers != null) ? newTriggers : new InboundRequestGuardTriggersConfig());
        configsService.addOrUpdateConfig(INBOUND_REQUEST_GUARD_TRIGGERS, normalized, System.currentTimeMillis());
        applyNormalized(normalized);
        log.debug("Updated inbound request guard triggers: mode={} headerNames={} userAgentSubstrings={} "
                        + "ipAddresses={}", normalized.getMode(), normalized.getHeaderNames(),
                normalized.getUserAgentSubstrings(), normalized.getIpAddresses());
        propagateTriggersChangeIfNeeded();
    }

    @Override
    public void reloadTriggersFromDb() {
        InboundRequestGuardTriggersConfig fromDb =
                configsService.getConfig(INBOUND_REQUEST_GUARD_TRIGGERS, InboundRequestGuardTriggersConfig.class);
        InboundRequestGuardTriggersConfig normalized;
        if (fromDb != null) {
            normalized = normalize(fromDb);
        } else {
            // No persisted triggers — apply the system-property default so the cached mode reflects the constant
            // (the field initializer's PFW3_PROBE would otherwise short-circuit effectiveMode()).
            normalized = normalize(new InboundRequestGuardTriggersConfig());
            normalized.setMode(defaultModeFromConstant());
        }
        applyNormalized(normalized);
        log.debug("Loaded inbound request guard triggers from DB: mode={} headerNames={} userAgentSubstrings={} "
                        + "ipAddresses={}", normalized.getMode(), normalized.getHeaderNames(),
                normalized.getUserAgentSubstrings(), normalized.getIpAddresses());
    }

    @Override
    public boolean shouldBlock(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        InboundRequestGuardTriggersConfig current = triggers.get();
        InboundRequestGuardTriggersConfig.Mode mode = effectiveMode(current);
        if (mode == InboundRequestGuardTriggersConfig.Mode.PFW3_OFF) {
            return false;
        }
        // Sets are pre-lowercased / pre-parsed at the write boundary; only request-side values need normalising.
        Set<String> lowerHeaders = current.getHeaderNames();
        Set<String> lowerUaSubs = current.getUserAgentSubstrings();
        List<IpRule> currentIpRules = ipRules;
        if (lowerHeaders.isEmpty() && lowerUaSubs.isEmpty() && currentIpRules.isEmpty()) {
            return false;
        }
        String matchedHeader = findMatchingHeader(request, lowerHeaders);
        String matchedUaSubstring = findMatchingUserAgentSubstring(request, lowerUaSubs);
        String matchedIpPattern = findMatchingIp(request, currentIpRules);
        if (matchedHeader == null && matchedUaSubstring == null && matchedIpPattern == null) {
            return false;
        }
        if (mode == InboundRequestGuardTriggersConfig.Mode.PFW3_PROBE) {
            log.warn("[probe] Inbound request (h='{}' ua='{}' ipMatch='{}' uri='{}').",
                    matchedHeader, matchedUaSubstring, matchedIpPattern,
                    request.getRequestURI());
            return false;
        }
        log.debug("Inbound request blocked (headerMatch='{}' userAgentSubstringMatch='{}' ipMatch='{}' uri='{}' "
                        + "userAgent='{}').", matchedHeader, matchedUaSubstring, matchedIpPattern,
                request.getRequestURI(), request.getHeader(USER_AGENT_HEADER));
        return true;
    }

    private String findMatchingHeader(HttpServletRequest request, Set<String> lowerHeaderNames) {
        if (lowerHeaderNames.isEmpty()) {
            return null;
        }
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return null;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (StringUtils.isNotBlank(name)
                    && lowerHeaderNames.contains(name.toLowerCase(Locale.ROOT))) {
                return name;
            }
        }
        return null;
    }

    private String findMatchingUserAgentSubstring(HttpServletRequest request, Set<String> lowerSubstrings) {
        if (lowerSubstrings.isEmpty()) {
            return null;
        }
        String userAgent = request.getHeader(USER_AGENT_HEADER);
        if (StringUtils.isBlank(userAgent)) {
            return null;
        }
        String lower = userAgent.toLowerCase(Locale.ROOT);
        for (String sub : lowerSubstrings) {
            if (lower.contains(sub)) {
                return sub;
            }
        }
        return null;
    }

    private String findMatchingIp(HttpServletRequest request, List<IpRule> rules) {
        if (rules.isEmpty()) {
            return null;
        }
        String clientIp;
        try {
            clientIp = HttpUtils.getRemoteClientAddress(request);
        } catch (URISyntaxException e) {
            log.debug("Failed to resolve client IP for inbound guard check", e);
            return null;
        }
        if (StringUtils.isBlank(clientIp)) {
            return null;
        }
        for (IpRule rule : rules) {
            if (rule.matcher().matches(clientIp)) {
                return rule.pattern();
            }
        }
        return null;
    }

    // Resolution order: persisted/in-memory cfg mode → SaaS/SH downstream system property (picked by AolService) →
    // PFW3_PROBE. The constant lets ops set a fleet-wide default without writing a triggers row; once an admin
    // persists a mode via REST, the DB value always wins.
    private InboundRequestGuardTriggersConfig.Mode effectiveMode(InboundRequestGuardTriggersConfig cfg) {
        if (cfg.getMode() != null) {
            return cfg.getMode();
        }
        return defaultModeFromConstant();
    }

    private InboundRequestGuardTriggersConfig.Mode defaultModeFromConstant() {
        String cv = selectModeConstant();
        if (StringUtils.isBlank(cv)) {
            return InboundRequestGuardTriggersConfig.Mode.PFW3_PROBE;
        }
        try {
            return InboundRequestGuardTriggersConfig.Mode.valueOf(cv.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid value '{}' — falling back to PFW3_PROBE.", cv);
            return InboundRequestGuardTriggersConfig.Mode.PFW3_PROBE;
        }
    }

    // SaaS deployments and Self-Hosted deployments use distinct system properties so ops can hold different
    // fleet-wide defaults per channel. If AolService isn't wired (test/early-startup), treat as Self-Hosted —
    // smaller blast radius than mis-routing to the SaaS constant.
    private String selectModeConstant() {
        boolean saas = aolService != null && aolService.isAol();
        return saas
                ? ConstantValues.unsupported3pfwSaasDownstreamBlockedMode.getString()
                : ConstantValues.unsupported3pfwShDownstreamBlockedMode.getString();
    }

    private void applyNormalized(InboundRequestGuardTriggersConfig normalized) {
        triggers.set(normalized);
        ipRules = parseIpRules(normalized.getIpAddresses());
    }

    private static List<IpRule> parseIpRules(Set<String> ipAddresses) {
        if (ipAddresses == null || ipAddresses.isEmpty()) {
            return List.of();
        }
        List<IpRule> rules = new ArrayList<>(ipAddresses.size());
        for (String entry : ipAddresses) {
            rules.add(new IpRule(entry, new IpAddressMatcher(entry)));
        }
        return List.copyOf(rules);
    }

    private static InboundRequestGuardTriggersConfig normalize(InboundRequestGuardTriggersConfig source) {
        InboundRequestGuardTriggersConfig out = new InboundRequestGuardTriggersConfig();
        out.setMode(source.getMode());
        out.setHeaderNames(lowercased(source.getHeaderNames()));
        out.setUserAgentSubstrings(lowercased(source.getUserAgentSubstrings()));
        out.setIpAddresses(validatedIpAddresses(source.getIpAddresses()));
        return out;
    }

    private static Set<String> lowercased(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> out = new HashSet<>(source.size());
        for (String s : source) {
            if (StringUtils.isNotBlank(s)) {
                out.add(s.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static Set<String> validatedIpAddresses(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> out = new HashSet<>(source.size());
        for (String s : source) {
            if (StringUtils.isBlank(s)) {
                continue;
            }
            String trimmed = s.trim();
            try {
                // Validate by attempting to construct the matcher — throws IAE on invalid IP/CIDR.
                new IpAddressMatcher(trimmed);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid IP address or CIDR range in inbound guard config: '" + trimmed + "'", e);
            }
            out.add(trimmed);
        }
        return out;
    }

    private void propagateTriggersChangeIfNeeded() {
        if (addonsManager == null) {
            return;
        }
        HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
        if (haAddon.isHaEnabled()) {
            haAddon.propagateInboundRequestGuardTriggersChange();
        }
    }

    @VisibleForTesting
    void setTriggersForTest(InboundRequestGuardTriggersConfig newTriggers) {
        applyNormalized(normalize(newTriggers != null ? newTriggers : new InboundRequestGuardTriggersConfig()));
    }

    @Autowired
    public void setConfigsService(ConfigsService configsService) {
        this.configsService = configsService;
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    public void setAolService(AolService aolService) {
        this.aolService = aolService;
    }

    private record IpRule(String pattern, IpAddressMatcher matcher) {
    }
}
