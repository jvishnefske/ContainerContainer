package org.artifactory.service;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.aol.AolService;
import org.artifactory.common.ConstantValues;
import org.artifactory.cron.CronUtils;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.interceptor.modle.RemoteRepoOfflineMarkedRepos;
import org.artifactory.repo.interceptor.modle.RemoteRepoOfflineTriggersConfig;
import org.artifactory.repo.remote.interceptor.RemoteRepoOutboundHeaderAuditService;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.schedule.monitor.OnlineMonitorService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.jobs.RemoteRepoOfflineGuardJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.artifactory.storage.fs.service.ConfigsServiceKey.REMOTE_REPO_OFFLINE_MARKED_REPOS;
import static org.artifactory.storage.fs.service.ConfigsServiceKey.REMOTE_REPO_OFFLINE_TRIGGERS;

/**
 * @author Hussein Alshqerat
 */
@Slf4j
@Service
public class RemoteRepoOfflineGuardServiceImpl implements RemoteRepoOfflineGuardService {

    private TaskService taskService;
    private InternalRepositoryService repositoryService;
    private ConfigsService configsService;
    private RemoteRepoOutboundHeaderAuditService auditService;
    private OnlineMonitorService onlineMonitorService;
    private AddonsManager addonsManager;
    private AolService aolService;
    private MeterRegistry meterRegistry;

    private final AtomicReference<RemoteRepoOfflineTriggersConfig> triggers =
            new AtomicReference<>(new RemoteRepoOfflineTriggersConfig());
    private final Object markedReposLock = new Object();
    private RemoteRepoOfflineMarkedRepos markedRepos = new RemoteRepoOfflineMarkedRepos();
    private final AtomicInteger probeWouldOfflineCount = new AtomicInteger(0);
    private Gauge offlinedReposGauge;
    private Gauge probeWouldOfflineGauge;

    @Override
    public void onContextCreated() {
        loadFromDb();
        updateModeSpecificMetrics(effectiveMode(triggers.get()));
        stampInitEpochIfNewModeDefault();
        scheduleJob();
        logStartupMode();
    }

    private void updateModeSpecificMetrics(RemoteRepoOfflineTriggersConfig.Mode mode) {
        if (offlinedReposGauge != null) {
            meterRegistry.remove(offlinedReposGauge);
            offlinedReposGauge = null;
        }
        if (probeWouldOfflineGauge != null) {
            meterRegistry.remove(probeWouldOfflineGauge);
            probeWouldOfflineGauge = null;
        }
        if (mode == RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE) {
            probeWouldOfflineGauge = Gauge.builder("remote.repo.offline.guard.probe.would.offline", this,
                            srv -> srv.probeWouldOfflineCount.get())
                    .description("Repos that would be offlined in last evaluate() run; "
                            + "meaningful only in PFW3_PROBE mode")
                    .tag("mode", mode.name())
                    .register(meterRegistry);
        } else if (mode == RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW
                || mode == RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL) {
            offlinedReposGauge = Gauge.builder("remote.repo.offline.guard.offlined.repos", this,
                            RemoteRepoOfflineGuardServiceImpl::getOfflinedRepoCount)
                    .description("Number of remote repos currently marked offline by the guard")
                    .tag("mode", mode.name())
                    .register(meterRegistry);
        }
    }

    private void logStartupMode() {
        log.info("Remote repo offline guard initialized: mode={}", triggers.get().getMode());
    }

    // First-startup boundary stamp for the PFW3_NEW default. Without this, a fresh install carries the default
    // mode but a null initEpochMillis, so the cron's "post-init" gate (isPostInitRepo) is false for every repo
    // and header-driven offlining silently no-ops until an admin saves any triggers config. Cluster-wide
    // convergence is lazy: peers picking up the persisted stamp on their next reload tolerate millisecond drift.
    private void stampInitEpochIfNewModeDefault() {
        RemoteRepoOfflineTriggersConfig persisted =
                configsService.getConfig(REMOTE_REPO_OFFLINE_TRIGGERS, RemoteRepoOfflineTriggersConfig.class);
        if (persisted != null) {
            return;
        }
        RemoteRepoOfflineTriggersConfig current = triggers.get();
        // Consult the resolved mode (cfg → constant → PFW3_PROBE) so a fresh install whose constant default is
        // PFW3_NEW gets stamped on first boot, even though the in-memory triggers field still defaults to
        // PFW3_PROBE before any persisted config is loaded.
        if (effectiveMode(current) != RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW
                || current.getInitEpochMillis() != null) {
            return;
        }
        long now = System.currentTimeMillis();
        RemoteRepoOfflineTriggersConfig stamped = normalize(current);
        stamped.setMode(RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW);
        stamped.setInitEpochMillis(now);
        configsService.addOrUpdateConfig(REMOTE_REPO_OFFLINE_TRIGGERS, stamped, now);
        triggers.set(stamped);
        log.debug("Stamped remote repo offline guard init epoch on first startup: {}", now);
    }

    @Override
    public RemoteRepoOfflineTriggersConfig getRemoteRepoOfflineGuardTriggersConfig() {
        return triggers.get();
    }

    @Override
    public void updateTriggers(RemoteRepoOfflineTriggersConfig newTriggers) {
        RemoteRepoOfflineTriggersConfig normalized =
                normalize((newTriggers != null) ? newTriggers : new RemoteRepoOfflineTriggersConfig());
        // Carry over the previously-stamped init date when the admin payload didn't include one. Lets ops update
        // url/header sets without resetting the new/existing-repo split.
        if (normalized.getInitEpochMillis() == null) {
            normalized.setInitEpochMillis(readPersistedInitEpochMillis());
        }
        // Auto-stamp on first transition into PFW3_NEW so admins don't have to supply the date manually.
        if (normalized.getMode() == RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW
                && normalized.getInitEpochMillis() == null) {
            normalized.setInitEpochMillis(System.currentTimeMillis());
        }
        configsService.addOrUpdateConfig(REMOTE_REPO_OFFLINE_TRIGGERS, normalized, System.currentTimeMillis());
        triggers.set(normalized);
        log.debug("Updated remote repo offline triggers: mode={} u={} h={} init={}",
                normalized.getMode(), normalized.getUrlSubstrings(), normalized.getHeaderNames(),
                normalized.getInitEpochMillis());
        propagateTriggersChangeIfNeeded();
        updateModeSpecificMetrics(effectiveMode(normalized));
    }

    @Override
    public void reloadTriggersFromDb() {
        RemoteRepoOfflineTriggersConfig resolved = resolveTriggersFromDbOrSeed();
        triggers.set(resolved);
        log.debug("Reloaded remote repo offline triggers from DB (HA propagation): mode={} u={} "
                + "h={}", resolved.getMode(), resolved.getUrlSubstrings(), resolved.getHeaderNames());
        updateModeSpecificMetrics(effectiveMode(resolved));
    }

    @Override
    public void evaluate() {
        RemoteRepoOfflineTriggersConfig current = readTriggersFromDbOrCached();
        RemoteRepoOfflineTriggersConfig.Mode mode = effectiveMode(current);
        if (mode == RemoteRepoOfflineTriggersConfig.Mode.PFW3_OFF) {
            log.debug("Remote repo offline guard skipped (mode=PFW3_OFF).");
            return;
        }
        // Sets are pre-lowercased at the write boundary (normalize); shouldn't be re-lowered here.
        Set<String> lowerSubs = current.getUrlSubstrings();
        Set<String> lowerHeaders = current.getHeaderNames();
        Set<String> whitelistedKeys = current.getWhitelistedRepoKeys();
        Long initEpochMillis = current.getInitEpochMillis();
        synchronized (markedReposLock) {
            markedRepos = readMarkedReposFromDb();
            probeWouldOfflineCount.set(0);
            boolean dirty = false;
            for (RemoteRepoConfig repo : repositoryService.getRemoteRepoConfigs()) {
                boolean changed =
                        applyTriggerForRepo(repo, lowerSubs, lowerHeaders, whitelistedKeys, mode, initEpochMillis);
                dirty = dirty || changed;
            }
            if (dirty) {
                persistMarkedRepos();
            }
        }
    }

    @Override
    public void onRemoteRepoUrlChanged(String repoKey, String newUrl) {
        if (StringUtils.isBlank(repoKey)) {
            return;
        }
        // Drop audit-header samples tied to the old URL so the cron header-detection logic starts fresh on the
        // new upstream. Re-onlining the repo itself is always an explicit admin action — the guard never flips a
        // repo back to online on its own.
        auditService.remove(repoKey);
        log.debug("Remote repo '{}' URL changed to '{}'; audit headers cleared.", repoKey, newUrl);
    }

    @Override
    public boolean isOfflinedByGuard(String repoKey) {
        if (StringUtils.isBlank(repoKey)) {
            return false;
        }
        if (triggers.get().getWhitelistedRepoKeys().contains(repoKey)) {
            return false;
        }
        synchronized (markedReposLock) {
            // Callers (download path in RemoteRepoBase.internalGetInfo) only reach this method on the offline
            // branch — i.e. repo.isOffline()==true is already known. The DB hit is fine on that cold path and
            // closes the HA staleness window so peers return the EULA 403 instead of the regular 404.
            markedRepos = readMarkedReposFromDb();
            return markedRepos.getRepoKeys().contains(repoKey);
        }
    }

    @Override
    public boolean isCreateBlocked(String repoKey, String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        RemoteRepoOfflineTriggersConfig current = readTriggersFromDbOrCached();
        RemoteRepoOfflineTriggersConfig.Mode mode = effectiveMode(current);
        if (mode != RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW
                && mode != RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL) {
            return false;
        }
        if (StringUtils.isNotBlank(repoKey) && current.getWhitelistedRepoKeys().contains(repoKey)) {
            return false;
        }
        return matchesUrl(url, current.getUrlSubstrings());
    }

    @Override
    public boolean isUpdateUrlBlocked(String repoKey, String oldUrl, String newUrl) {
        if (StringUtils.isBlank(repoKey) || Objects.equals(oldUrl, newUrl)) {
            return false;
        }
        return isCreateBlocked(repoKey, newUrl);
    }

    @Override
    public int getOfflinedRepoCount() {
        return readMarkedReposFromDb().getRepoKeys().size();
    }

    @Override
    public boolean isReonlineBlocked(String repoKey, String newUrl) {
        if (StringUtils.isBlank(repoKey)) {
            return false;
        }
        RemoteRepoOfflineTriggersConfig current = readTriggersFromDbOrCached();
        RemoteRepoOfflineTriggersConfig.Mode mode = effectiveMode(current);
        if (mode != RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW
                && mode != RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL) {
            return false;
        }
        if (current.getWhitelistedRepoKeys().contains(repoKey)) {
            return false;
        }
        synchronized (markedReposLock) {
            // Refresh from DB to close the HA staleness window: a peer node's evaluate() may have just marked
            // this repo offline, and the local cache wouldn't see it until our own cron tick.
            markedRepos = readMarkedReposFromDb();
            if (!markedRepos.getRepoKeys().contains(repoKey)) {
                return false;
            }
        }
        if (mode == RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW) {
            // In PFW3_NEW, only post-init ("new") repos can be re-online-blocked. Pre-init repos may carry stale
            // markers from a prior enforcing-mode session — admins can re-online those freely.
            RemoteRepoConfig repo = findRemoteRepo(repoKey);
            if (!isPostInitRepo(repo, current.getInitEpochMillis())) {
                return false;
            }
        }
        return matchesUrl(newUrl, current.getUrlSubstrings())
                || matchesHeaders(repoKey, current.getHeaderNames());
    }

    // Returns true if the marked-repos set was mutated. The guard only ever drives offline state in one direction
    // (online → offline); restoring a guard-offlined repo back to online is always an explicit admin action.
    private boolean applyTriggerForRepo(RemoteRepoConfig repo, Set<String> lowerSubs, Set<String> lowerHeaders,
            Set<String> whitelistedKeys, RemoteRepoOfflineTriggersConfig.Mode mode, Long initEpochMillis) {
        String repoKey = repo.getKey();
        String url = repo.getRepoTypeConfig().getUrl();
        boolean whitelisted = whitelistedKeys.contains(repoKey);
        boolean urlBad = matchesUrl(url, lowerSubs);
        boolean headerBad = matchesHeaders(repoKey, lowerHeaders);
        boolean alreadyOffline = repo.getRepoTypeConfig().isOffline();
        if (mode == RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE) {
            if (!whitelisted && (urlBad || headerBad) && !alreadyOffline) {
                log.warn("[probe] '{}' (u={} h={}, url='{}', headers={}).",
                        repoKey, urlBad, headerBad, url, auditService.getHeaderNames(repoKey));
                probeWouldOfflineCount.incrementAndGet();
            }
            return false;
        }
        // Enforcing modes: PFW3_ALL offlines on URL or header match for every repo. PFW3_NEW only offlines on
        // header match, and only for repos created at or after initEpochMillis — URL is enforced exclusively at
        // the create/update interceptor in NEW (no retroactive URL offlining).
        boolean inScope = (mode == RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL)
                || (mode == RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW
                && isPostInitRepo(repo, initEpochMillis));
        if (!inScope) {
            return false;
        }
        boolean considerUrlForCron = (mode == RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL);
        boolean shouldOffline = !whitelisted && ((considerUrlForCron && urlBad) || headerBad);
        if (shouldOffline && !alreadyOffline) {
            repo.getRepoTypeConfig().setOffline(true);
            repositoryService.updateRepoConfig(repo);
            markedRepos.getRepoKeys().add(repoKey);
            stopOnlineMonitor(repoKey);
            log.warn("Remote repo '{}' set offline (mode={}, urlMatch={} headerMatch={}, url='{}').",
                    repoKey, mode, urlBad, headerBad, url);
            return true;
        }
        return false;
    }

    private boolean isPostInitRepo(RemoteRepoConfig repo, Long initEpochMillis) {
        if (repo == null || initEpochMillis == null) {
            return false;
        }
        return repo.getCreation() >= initEpochMillis;
    }

    // Resolution order: persisted/in-memory cfg mode → SaaS/SH upstream system property (picked by AolService) →
    // PFW3_PROBE. The constant lets ops set a fleet-wide default without writing a triggers row; once an admin
    // persists a mode via REST, the DB value always wins.
    private RemoteRepoOfflineTriggersConfig.Mode effectiveMode(RemoteRepoOfflineTriggersConfig cfg) {
        if (cfg.getMode() != null) {
            return cfg.getMode();
        }
        return defaultModeFromConstant();
    }

    private RemoteRepoOfflineTriggersConfig.Mode defaultModeFromConstant() {
        String cv = selectModeConstant();
        if (StringUtils.isBlank(cv)) {
            return RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE;
        }
        try {
            return RemoteRepoOfflineTriggersConfig.Mode.valueOf(cv.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid value '{}' — falling back to PFW3_PROBE.", cv);
            return RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE;
        }
    }

    // SaaS deployments and Self-Hosted deployments use distinct system properties so ops can hold different
    // fleet-wide defaults per channel. If AolService isn't wired (test/early-startup), treat as Self-Hosted —
    // smaller blast radius than mis-routing to the SaaS constant.
    private String selectModeConstant() {
        boolean saas = aolService != null && aolService.isAol();
        return saas
                ? ConstantValues.unsupported3pfwSaasUpstreamBlockedMode.getString()
                : ConstantValues.unsupported3pfwShUpstreamBlockedMode.getString();
    }

    private boolean matchesUrl(String url, Set<String> lowerSubs) {
        if (StringUtils.isBlank(url) || lowerSubs.isEmpty()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        for (String sub : lowerSubs) {
            if (lower.contains(sub)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesHeaders(String repoKey, Set<String> lowerHeaders) {
        if (lowerHeaders.isEmpty()) {
            return false;
        }
        Set<String> seen = auditService.getHeaderNames(repoKey);
        for (String name : seen) {
            if (name != null && lowerHeaders.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void stopOnlineMonitor(String repoKey) {
        if (onlineMonitorService == null) {
            return;
        }
        try {
            onlineMonitorService.stopMonitorForRepo(repoKey);
        } catch (Exception e) {
            log.warn("Failed to stop online monitor for offlined repo '{}'", repoKey, e);
        }
    }

    private RemoteRepoConfig findRemoteRepo(String repoKey) {
        return repositoryService.getRemoteRepoConfigByKey(repoKey);
    }

    private static Set<String> lowercased(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>(source.size());
        for (String s : source) {
            if (StringUtils.isNotBlank(s)) {
                out.add(s.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private void loadFromDb() {
        triggers.set(resolveTriggersFromDbOrSeed());
        synchronized (markedReposLock) {
            markedRepos = readMarkedReposFromDb();
        }
    }

    // Returns the persisted triggers (normalized) when present; otherwise a fresh config whose mode comes from
    // the system-property default. Centralizes the load-or-seed step shared by the startup loader and the HA
    // reload path. The field initializer's PFW3_PROBE would otherwise short-circuit effectiveMode() before the
    // constant is read on a fresh install.
    private RemoteRepoOfflineTriggersConfig resolveTriggersFromDbOrSeed() {
        RemoteRepoOfflineTriggersConfig fromDb =
                configsService.getConfig(REMOTE_REPO_OFFLINE_TRIGGERS, RemoteRepoOfflineTriggersConfig.class);
        if (fromDb != null) {
            return normalize(fromDb);
        }
        RemoteRepoOfflineTriggersConfig seeded = normalize(new RemoteRepoOfflineTriggersConfig());
        seeded.setMode(defaultModeFromConstant());
        return seeded;
    }

    private RemoteRepoOfflineTriggersConfig readTriggersFromDbOrCached() {
        RemoteRepoOfflineTriggersConfig fromDb =
                configsService.getConfig(REMOTE_REPO_OFFLINE_TRIGGERS, RemoteRepoOfflineTriggersConfig.class);
        if (fromDb != null) {
            RemoteRepoOfflineTriggersConfig normalized = normalize(fromDb);
            triggers.set(normalized);
            return normalized;
        }
        return triggers.get();
    }

    private static RemoteRepoOfflineTriggersConfig normalize(RemoteRepoOfflineTriggersConfig source) {
        RemoteRepoOfflineTriggersConfig out = new RemoteRepoOfflineTriggersConfig();
        out.setMode(source.getMode());
        out.setUrlSubstrings(lowercased(source.getUrlSubstrings()));
        out.setHeaderNames(lowercased(source.getHeaderNames()));
        // Repo keys are case-sensitive — copy through without lowercasing, dropping blanks.
        out.setWhitelistedRepoKeys(copyTrimmed(source.getWhitelistedRepoKeys()));
        out.setInitEpochMillis(source.getInitEpochMillis());
        return out;
    }

    private Long readPersistedInitEpochMillis() {
        RemoteRepoOfflineTriggersConfig persisted =
                configsService.getConfig(REMOTE_REPO_OFFLINE_TRIGGERS, RemoteRepoOfflineTriggersConfig.class);
        return (persisted != null) ? persisted.getInitEpochMillis() : null;
    }

    private static Set<String> copyTrimmed(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return new HashSet<>();
        }
        Set<String> out = new HashSet<>(source.size());
        for (String s : source) {
            if (StringUtils.isNotBlank(s)) {
                out.add(s);
            }
        }
        return out;
    }

    private RemoteRepoOfflineMarkedRepos readMarkedReposFromDb() {
        RemoteRepoOfflineMarkedRepos fromDb =
                configsService.getConfig(REMOTE_REPO_OFFLINE_MARKED_REPOS, RemoteRepoOfflineMarkedRepos.class);
        return (fromDb != null) ? fromDb : new RemoteRepoOfflineMarkedRepos();
    }

    private void persistMarkedRepos() {
        configsService.addOrUpdateConfig(REMOTE_REPO_OFFLINE_MARKED_REPOS, markedRepos, System.currentTimeMillis());
    }

    private void propagateTriggersChangeIfNeeded() {
        if (addonsManager == null) {
            return;
        }
        HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
        if (haAddon.isHaEnabled()) {
            haAddon.propagateRemoteRepoOfflineTriggersChange();
        }
    }

    private void scheduleJob() {
        String cron = ConstantValues.remoteRepoOfflineGuardCron.getString();
        if (StringUtils.isBlank(cron) || !CronUtils.isValid(cron)) {
            log.warn("Remote repo offline guard job disabled, cron '{}' is blank or invalid.", cron);
            return;
        }
        log.debug("Scheduling remote repo offline guard with cron '{}'.", cron);
        taskService.startTask(
                TaskUtils.createCronTask(RemoteRepoOfflineGuardJob.class, cron, "Remote Repo Offline Guard"), false);
    }

    @VisibleForTesting
    void setMarkedReposForTest(RemoteRepoOfflineMarkedRepos markedRepos) {
        synchronized (markedReposLock) {
            this.markedRepos = markedRepos;
        }
    }

    @VisibleForTesting
    void setTriggersForTest(RemoteRepoOfflineTriggersConfig newTriggers) {
        this.triggers.set(normalize(newTriggers != null ? newTriggers : new RemoteRepoOfflineTriggersConfig()));
    }

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Autowired
    public void setRepositoryService(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setConfigsService(ConfigsService configsService) {
        this.configsService = configsService;
    }

    @Autowired
    public void setAuditService(RemoteRepoOutboundHeaderAuditService auditService) {
        this.auditService = auditService;
    }

    @Autowired
    public void setOnlineMonitorService(OnlineMonitorService onlineMonitorService) {
        this.onlineMonitorService = onlineMonitorService;
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    public void setAolService(AolService aolService) {
        this.aolService = aolService;
    }

    @Autowired
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
}
