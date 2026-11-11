package org.artifactory.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.aol.AolService;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.interceptor.modle.RemoteRepoOfflineMarkedRepos;
import org.artifactory.repo.interceptor.modle.RemoteRepoOfflineTriggersConfig;
import org.artifactory.repo.remote.interceptor.RemoteRepoOutboundHeaderAuditService;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.monitor.OnlineMonitorService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.artifactory.storage.fs.service.ConfigsServiceKey.REMOTE_REPO_OFFLINE_MARKED_REPOS;
import static org.artifactory.storage.fs.service.ConfigsServiceKey.REMOTE_REPO_OFFLINE_TRIGGERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Hussein Alshqerat
 */
class RemoteRepoOfflineGuardServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    TaskService taskService;
    @Mock
    InternalRepositoryService repositoryService;
    @Mock
    ConfigsService configsService;
    @Mock
    RemoteRepoOutboundHeaderAuditService auditService;
    @Mock
    OnlineMonitorService onlineMonitorService;
    @Mock
    AddonsManager addonsManager;
    @Mock
    HaAddon haAddon;
    @Mock
    AolService aolService;

    RemoteRepoOfflineGuardServiceImpl service;
    SimpleMeterRegistry meterRegistry;
    AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        service = new RemoteRepoOfflineGuardServiceImpl();
        service.setTaskService(taskService);
        service.setRepositoryService(repositoryService);
        service.setConfigsService(configsService);
        service.setAuditService(auditService);
        service.setOnlineMonitorService(onlineMonitorService);
        service.setAddonsManager(addonsManager);
        service.setAolService(aolService);
        service.setMeterRegistry(meterRegistry);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        // Default to self-hosted; SaaS-specific tests opt in via when(aolService.isAol()).thenReturn(true).
        org.mockito.Mockito.lenient().when(aolService.isAol()).thenReturn(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    private static RemoteRepoConfig remoteRepo(String repoKey, String url, boolean offline) {
        return remoteRepo(repoKey, url, offline, 0L);
    }

    private static RemoteRepoConfig remoteRepo(String repoKey, String url, boolean offline, long creation) {
        RemoteRepoConfig repo = org.mockito.Mockito.mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig typeConfig = org.mockito.Mockito.mock(RemoteRepositoryRepoTypeConfig.class);
        when(repo.getKey()).thenReturn(repoKey);
        when(repo.getRepoTypeConfig()).thenReturn(typeConfig);
        when(repo.getCreation()).thenReturn(creation);
        when(typeConfig.getUrl()).thenReturn(url);
        when(typeConfig.isOffline()).thenReturn(offline);
        return repo;
    }

    private RemoteRepoOfflineTriggersConfig triggers(Set<String> urlSubs, Set<String> headers) {
        return triggers(urlSubs, headers, RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL);
    }

    private RemoteRepoOfflineTriggersConfig triggers(Set<String> urlSubs, Set<String> headers,
            RemoteRepoOfflineTriggersConfig.Mode mode) {
        RemoteRepoOfflineTriggersConfig t = new RemoteRepoOfflineTriggersConfig();
        t.setMode(mode);
        t.setUrlSubstrings(urlSubs);
        t.setHeaderNames(headers);
        return t;
    }

    private RemoteRepoOfflineTriggersConfig triggers(Set<String> urlSubs, Set<String> headers,
            Set<String> whitelist) {
        RemoteRepoOfflineTriggersConfig t = triggers(urlSubs, headers);
        t.setWhitelistedRepoKeys(whitelist);
        return t;
    }

    private void stubTriggers(RemoteRepoOfflineTriggersConfig t) {
        when(configsService.getConfig(eq(REMOTE_REPO_OFFLINE_TRIGGERS), eq(RemoteRepoOfflineTriggersConfig.class)))
                .thenReturn(t);
    }

    private void stubMarked(Set<String> keys) {
        RemoteRepoOfflineMarkedRepos marked = new RemoteRepoOfflineMarkedRepos();
        marked.setRepoKeys(new HashSet<>(keys));
        when(configsService.getConfig(eq(REMOTE_REPO_OFFLINE_MARKED_REPOS), eq(RemoteRepoOfflineMarkedRepos.class)))
                .thenReturn(marked);
        // Read paths consult the in-memory cache (writers refresh it from DB). Seed it for reader-only tests.
        RemoteRepoOfflineMarkedRepos cached = new RemoteRepoOfflineMarkedRepos();
        cached.setRepoKeys(new HashSet<>(keys));
        service.setMarkedReposForTest(cached);
    }

    @Test
    void headerMatchOfflinesRepoAndPersistsMarker() {
        stubTriggers(triggers(Set.of(), Set.of("x-bad")));
        stubMarked(Set.of());
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://registry.example.com", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));
        when(auditService.getHeaderNames("npm-remote")).thenReturn(Set.of("X-Bad"));

        service.evaluate();

        verify(repo.getRepoTypeConfig()).setOffline(true);
        verify(repositoryService).updateRepoConfig(repo);
        verify(configsService).addOrUpdateConfig(
                eq(REMOTE_REPO_OFFLINE_MARKED_REPOS), any(Object.class), anyLong());
        verify(onlineMonitorService).stopMonitorForRepo("npm-remote");
    }

    @Test
    void urlSubstringMatchOfflinesRepoInPfw3All() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of()));
        stubMarked(Set.of());
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://bad-host.example.com/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.evaluate();

        verify(repo.getRepoTypeConfig()).setOffline(true);
        verify(repositoryService).updateRepoConfig(repo);
        verify(onlineMonitorService).stopMonitorForRepo("npm-remote");
    }

    @Test
    void urlSubstringMatchDoesNotOfflineAnyRepoInPfw3New() {
        // In PFW3_NEW, URL is enforced only at the create/update interceptor. Cron must not retroactively offline
        // a repo whose URL matches a substring added after the repo was created.
        long init = 1_000_000_000L;
        RemoteRepoOfflineTriggersConfig t =
                triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW);
        t.setInitEpochMillis(init);
        stubTriggers(t);
        stubMarked(Set.of());
        RemoteRepoConfig postInit = remoteRepo("new-repo", "https://bad-host.example.com/repo", false, init + 1);
        RemoteRepoConfig preInit = remoteRepo("legacy-repo", "https://bad-host.example.com/repo", false, init - 1);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(postInit, preInit));

        service.evaluate();

        verify(postInit.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(preInit.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
    }

    @Test
    void noStopMonitorCallWhenRepoStaysOnline() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of()));
        stubMarked(Set.of());
        RemoteRepoConfig repo = remoteRepo("safe-remote", "https://registry.example.com", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));
        when(auditService.getHeaderNames("safe-remote")).thenReturn(Set.of());

        service.evaluate();

        verifyNoInteractions(onlineMonitorService);
    }

    @Test
    void stopMonitorFailureDoesNotPropagate() {
        stubTriggers(triggers(Set.of(), Set.of("x-bad")));
        stubMarked(Set.of());
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://registry.example.com", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));
        when(auditService.getHeaderNames("npm-remote")).thenReturn(Set.of("X-Bad"));
        org.mockito.Mockito.doThrow(new RuntimeException("monitor down"))
                .when(onlineMonitorService).stopMonitorForRepo("npm-remote");

        service.evaluate();

        verify(repo.getRepoTypeConfig()).setOffline(true);
        verify(repositoryService).updateRepoConfig(repo);
    }

    @Test
    void headerNameMatchOfflinesRepo() {
        stubTriggers(triggers(Set.of(), Set.of("X-Forbidden-Header")));
        stubMarked(Set.of());
        RemoteRepoConfig repo = remoteRepo("docker-remote", "https://registry.example.com", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));
        when(auditService.getHeaderNames("docker-remote")).thenReturn(Set.of("Content-Type", "x-forbidden-header"));

        service.evaluate();

        verify(repo.getRepoTypeConfig()).setOffline(true);
        verify(repositoryService).updateRepoConfig(repo);
    }

    @Test
    void noMatchLeavesRepoAlone() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of("X-Bad")));
        stubMarked(Set.of());
        RemoteRepoConfig repo = remoteRepo("safe-remote", "https://registry.example.com", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));
        when(auditService.getHeaderNames("safe-remote")).thenReturn(Set.of("Content-Type"));

        service.evaluate();

        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
        verify(configsService, never())
                .addOrUpdateConfig(any(ConfigsServiceKey.class), any(Object.class), anyLong());
    }

    @Test
    void cronDoesNotReonlineGuardOfflinedRepoEvenWhenTriggersClear() {
        // Re-online is always an explicit admin action; cron never restores a guard-offlined repo.
        stubTriggers(triggers(Set.of("bad-host"), Set.of()));
        stubMarked(Set.of("offlined-by-us"));
        RemoteRepoConfig adminOfflined = remoteRepo("admin-offlined", "https://registry.example.com", true);
        RemoteRepoConfig guardOfflined = remoteRepo("offlined-by-us", "https://registry.example.com", true);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(adminOfflined, guardOfflined));
        when(auditService.getHeaderNames(any())).thenReturn(Set.of());

        service.evaluate();

        verify(adminOfflined.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(guardOfflined.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
    }

    @Test
    void whitelistedRepoIsNotOfflinedEvenWhenUrlMatches() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), Set.of("npm-remote")));
        stubMarked(Set.of());
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://bad-host.example.com/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.evaluate();

        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
        verifyNoInteractions(onlineMonitorService);
    }

    @Test
    void whitelistedRepoIsNotOfflinedEvenWhenHeaderMatches() {
        stubTriggers(triggers(Set.of(), Set.of("x-forbidden-header"), Set.of("docker-remote")));
        stubMarked(Set.of());
        RemoteRepoConfig repo = remoteRepo("docker-remote", "https://registry.example.com", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));
        when(auditService.getHeaderNames("docker-remote")).thenReturn(Set.of("X-Forbidden-Header"));

        service.evaluate();

        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
    }

    @Test
    void whitelistingAfterOfflineDoesNotAutoReonline() {
        // Adding a repo to the whitelist does not auto-restore an existing guard-offline state — admin must
        // explicitly re-online. The whitelist still exempts future enforcement actions.
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), Set.of("npm-remote")));
        stubMarked(Set.of("npm-remote"));
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://bad-host.example.com/repo", true);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.evaluate();

        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
    }

    @Test
    void whitelistedRepoStaysOnlineInProbeMode() {
        // PFW3_PROBE would normally log "would be offlined"; whitelist should suppress that for exempt repos.
        RemoteRepoOfflineTriggersConfig probeWithWhitelist =
                triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE);
        probeWithWhitelist.setWhitelistedRepoKeys(Set.of("npm-remote"));
        stubTriggers(probeWithWhitelist);
        stubMarked(Set.of());
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://bad-host.example.com/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.evaluate();

        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
    }

    @Test
    void alreadyOfflineRepoIsNotWrittenAgain() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of()));
        stubMarked(Set.of());
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://bad-host.example.com/repo", true);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.evaluate();

        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
    }

    @Test
    void urlChangeClearsHeadersButNeverReonlines() {
        // Audit headers tied to the old URL are dropped, but the repo's offline state is not changed by the hook.
        stubMarked(Set.of("npm-remote"));
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://good-host.example.com", true);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.onRemoteRepoUrlChanged("npm-remote", "https://good-host.example.com");

        verify(auditService).remove("npm-remote");
        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
        verify(configsService, never())
                .addOrUpdateConfig(any(ConfigsServiceKey.class), any(Object.class), anyLong());
    }

    @Test
    void urlChangeOnRepoNotMarkedJustClearsHeaders() {
        service.onRemoteRepoUrlChanged("admin-offlined", "https://good-host.example.com");

        verify(auditService).remove("admin-offlined");
        verify(repositoryService, never()).updateRepoConfig(any());
        verify(configsService, never())
                .addOrUpdateConfig(any(ConfigsServiceKey.class), any(Object.class), anyLong());
    }

    @Test
    void updateTriggersPersistsAndUpdatesInMemory() {
        RemoteRepoOfflineTriggersConfig t = triggers(Set.of("bad"), Set.of("x-bad"));

        service.updateTriggers(t);

        verify(configsService).addOrUpdateConfig(eq(REMOTE_REPO_OFFLINE_TRIGGERS), eq(t), anyLong());
        assertEquals(t, offlineTriggers());
    }

    @Test
    void updateTriggersNormalizesCaseBeforePersistingAndCaching() {
        RemoteRepoOfflineTriggersConfig input = triggers(Set.of("Bad-Host"), Set.of("X-Bad-Header"));
        org.mockito.ArgumentCaptor<RemoteRepoOfflineTriggersConfig> persisted =
                org.mockito.ArgumentCaptor.forClass(RemoteRepoOfflineTriggersConfig.class);

        service.updateTriggers(input);

        verify(configsService).addOrUpdateConfig(eq(REMOTE_REPO_OFFLINE_TRIGGERS), persisted.capture(), anyLong());
        assertEquals(Set.of("bad-host"), persisted.getValue().getUrlSubstrings());
        assertEquals(Set.of("x-bad-header"), persisted.getValue().getHeaderNames());
        assertEquals(Set.of("bad-host"), offlineTriggers().getUrlSubstrings());
        assertEquals(Set.of("x-bad-header"), offlineTriggers().getHeaderNames());
    }

    @Test
    void updateTriggersWithNullReplacesWithSeededDefaults() {
        service.updateTriggers(null);

        verify(configsService).addOrUpdateConfig(
                eq(REMOTE_REPO_OFFLINE_TRIGGERS), any(Object.class), anyLong());
        assertEquals(RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE, offlineTriggers().getMode());
        assertEquals(RemoteRepoOfflineTriggersConfig.DEFAULT_URL_SUBSTRINGS,
                offlineTriggers().getUrlSubstrings());
        assertEquals(RemoteRepoOfflineTriggersConfig.DEFAULT_HEADER_NAMES,
                offlineTriggers().getHeaderNames());
    }

    @Test
    void evaluateIsNoopWhenNoTriggersConfigured() {
        stubTriggers(new RemoteRepoOfflineTriggersConfig());
        stubMarked(Set.of());
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://anything", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.evaluate();

        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
        assertFalse(offlineTriggers().getUrlSubstrings().contains("anything"));
    }

    @Test
    void probeModeLogsButDoesNotOffline() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE));
        stubMarked(Set.of());
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://bad-host.example.com/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.evaluate();

        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
        verifyNoInteractions(onlineMonitorService);
        verify(configsService, never())
                .addOrUpdateConfig(any(ConfigsServiceKey.class), any(Object.class), anyLong());
    }

    @Test
    void offModeSkipsEvaluationEntirely() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_OFF));
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://bad-host.example.com/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.evaluate();

        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
        verifyNoInteractions(onlineMonitorService);
    }

    @Test
    void urlChangeInOffModeClearsHeadersAndDoesNothingElse() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_OFF));

        service.onRemoteRepoUrlChanged("npm-remote", "https://anywhere");

        verify(auditService).remove("npm-remote");
        verify(repositoryService, never()).updateRepoConfig(any());
        verify(configsService, never())
                .addOrUpdateConfig(any(ConfigsServiceKey.class), any(Object.class), anyLong());
    }

    @Test
    void updateTriggersPropagatesToOtherHaNodesWhenHaEnabled() {
        when(haAddon.isHaEnabled()).thenReturn(true);
        RemoteRepoOfflineTriggersConfig t = triggers(Set.of("bad"), Set.of("X-Bad"));

        service.updateTriggers(t);

        verify(haAddon).propagateRemoteRepoOfflineTriggersChange();
    }

    @Test
    void updateTriggersDoesNotPropagateWhenHaDisabled() {
        when(haAddon.isHaEnabled()).thenReturn(false);
        RemoteRepoOfflineTriggersConfig t = triggers(Set.of("bad"), Set.of("X-Bad"));

        service.updateTriggers(t);

        verify(haAddon, never()).propagateRemoteRepoOfflineTriggersChange();
    }

    @Test
    void onContextCreatedLoadsTriggersAndSchedulesJob() {
        RemoteRepoOfflineTriggersConfig fromDb = triggers(Set.of("bad"), Set.of("x-bad"));
        stubTriggers(fromDb);
        stubMarked(Set.of("npm-remote"));

        service.onContextCreated();

        assertEquals(fromDb, offlineTriggers());
        verify(taskService).startTask(org.mockito.ArgumentMatchers.any(), eq(false));
    }

    @Test
    void reloadTriggersFromDbReplacesInMemoryCache() {
        RemoteRepoOfflineTriggersConfig fromDb =
                triggers(Set.of("new-bad"), Set.of("x-new"), RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL);
        stubTriggers(fromDb);

        service.reloadTriggersFromDb();

        assertEquals(fromDb, offlineTriggers());
    }

    @Test
    void reloadTriggersFromDbNormalizesLegacyMixedCaseData() {
        RemoteRepoOfflineTriggersConfig legacy =
                triggers(Set.of("Legacy-Host"), Set.of("X-Legacy"), RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL);
        stubTriggers(legacy);

        service.reloadTriggersFromDb();

        assertEquals(Set.of("legacy-host"), offlineTriggers().getUrlSubstrings());
        assertEquals(Set.of("x-legacy"), offlineTriggers().getHeaderNames());
    }

    @Test
    void reloadTriggersFromDbDefaultsToSeededValuesWhenNothingInDb() {
        when(configsService.getConfig(eq(REMOTE_REPO_OFFLINE_TRIGGERS), eq(RemoteRepoOfflineTriggersConfig.class)))
                .thenReturn(null);

        service.reloadTriggersFromDb();

        assertEquals(RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE, offlineTriggers().getMode());
        assertEquals(RemoteRepoOfflineTriggersConfig.DEFAULT_URL_SUBSTRINGS,
                offlineTriggers().getUrlSubstrings());
        assertEquals(RemoteRepoOfflineTriggersConfig.DEFAULT_HEADER_NAMES,
                offlineTriggers().getHeaderNames());
    }

    @Test
    void getRemoteRepoOfflineGuardTriggersConfigReturnsProbeDefaultBeforeFirstLoad() {
        RemoteRepoOfflineTriggersConfig defaults = offlineTriggers();
        assertEquals(RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE, defaults.getMode());
        assertEquals(RemoteRepoOfflineTriggersConfig.DEFAULT_HEADER_NAMES, defaults.getHeaderNames());
        assertEquals(RemoteRepoOfflineTriggersConfig.DEFAULT_URL_SUBSTRINGS, defaults.getUrlSubstrings());
        assertNull(defaults.getInitEpochMillis());
    }

    @Test
    void freshConfigCarriesDefaultHeaderNamesAndUrlSubstrings() {
        RemoteRepoOfflineTriggersConfig fresh = new RemoteRepoOfflineTriggersConfig();

        assertEquals(RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE, fresh.getMode());
        assertEquals(RemoteRepoOfflineTriggersConfig.DEFAULT_HEADER_NAMES, fresh.getHeaderNames());
        assertEquals(RemoteRepoOfflineTriggersConfig.DEFAULT_URL_SUBSTRINGS, fresh.getUrlSubstrings());
        // Sanity: the well-known Socket / Endor entries are present.
        assertTrue(fresh.getHeaderNames().contains("x-socket-decision"));
        assertTrue(fresh.getUrlSubstrings().contains("firewall.socket.dev"));
        assertTrue(fresh.getUrlSubstrings().contains("endorlabs.com"));
    }

    @Test
    void isReonlineBlockedReturnsTrueWhenMarkedAndUrlStillMatches() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of()));
        stubMarked(Set.of("npm-remote"));

        assertTrue(service.isReonlineBlocked("npm-remote", "https://bad-host.example.com/repo"));
    }

    @Test
    void isReonlineBlockedReturnsTrueWhenMarkedAndHeaderStillMatches() {
        stubTriggers(triggers(Set.of(), Set.of("X-Forbidden-Header")));
        stubMarked(Set.of("docker-remote"));
        when(auditService.getHeaderNames("docker-remote")).thenReturn(Set.of("x-forbidden-header"));

        assertTrue(service.isReonlineBlocked("docker-remote", "https://registry.example.com"));
    }

    @Test
    void isReonlineBlockedReturnsFalseWhenRepoNotMarked() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of()));
        stubMarked(Set.of());

        assertFalse(service.isReonlineBlocked("admin-offlined", "https://bad-host.example.com"));
    }

    @Test
    void isReonlineBlockedReturnsFalseWhenTriggersNoLongerMatch() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of("X-Bad")));
        stubMarked(Set.of("npm-remote"));
        when(auditService.getHeaderNames("npm-remote")).thenReturn(Set.of("Content-Type"));

        assertFalse(service.isReonlineBlocked("npm-remote", "https://good-host.example.com"));
    }

    @Test
    void isReonlineBlockedReturnsFalseInProbeMode() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE));
        stubMarked(Set.of("npm-remote"));

        assertFalse(service.isReonlineBlocked("npm-remote", "https://bad-host.example.com"));
    }

    @Test
    void isReonlineBlockedReturnsFalseInOffMode() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_OFF));
        stubMarked(Set.of("npm-remote"));

        assertFalse(service.isReonlineBlocked("npm-remote", "https://bad-host.example.com"));
    }

    @Test
    void isReonlineBlockedReturnsFalseForBlankRepoKey() {
        assertFalse(service.isReonlineBlocked("", "https://bad-host.example.com"));
        assertFalse(service.isReonlineBlocked(null, "https://bad-host.example.com"));
    }

    @Test
    void isOfflinedByGuardReturnsTrueWhenRepoIsMarked() {
        stubMarked(Set.of("npm-remote"));

        assertTrue(service.isOfflinedByGuard("npm-remote"));
    }

    @Test
    void isOfflinedByGuardReturnsFalseWhenRepoNotMarked() {
        stubMarked(Set.of("docker-remote"));

        assertFalse(service.isOfflinedByGuard("npm-remote"));
    }

    @Test
    void isOfflinedByGuardReturnsFalseForBlankRepoKey() {
        assertFalse(service.isOfflinedByGuard(""));
        assertFalse(service.isOfflinedByGuard(null));
    }

    @Test
    void isOfflinedByGuardReturnsFalseForWhitelistedRepoEvenIfStillMarked() {
        // Stale marked entry from before whitelist was added — guard should not claim ownership.
        service.setTriggersForTest(triggers(Set.of("bad-host"), Set.of(), Set.of("npm-remote")));
        stubMarked(Set.of("npm-remote"));

        assertFalse(service.isOfflinedByGuard("npm-remote"));
    }

    @Test
    void isReonlineBlockedReturnsFalseForWhitelistedRepoEvenIfTriggersStillMatch() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), Set.of("npm-remote")));
        stubMarked(Set.of("npm-remote"));

        assertFalse(service.isReonlineBlocked("npm-remote", "https://bad-host.example.com/repo"));
    }

    @Test
    void normalizePreservesWhitelistCaseAndStripsBlanks() {
        RemoteRepoOfflineTriggersConfig input = triggers(Set.of("bad"), Set.of("x-bad"),
                new HashSet<>(java.util.Arrays.asList("CaseSensitive-Repo", "Another-Repo", " ", "")));

        service.updateTriggers(input);

        Set<String> persistedWhitelist = offlineTriggers().getWhitelistedRepoKeys();
        assertEquals(Set.of("CaseSensitive-Repo", "Another-Repo"), persistedWhitelist);
    }

    @Test
    void isReonlineBlockedReturnsFalseWhenModeIsNullDefaultingToProbe() {
        RemoteRepoOfflineTriggersConfig legacy = new RemoteRepoOfflineTriggersConfig();
        legacy.setMode(null);
        legacy.setUrlSubstrings(Set.of("bad-host"));
        stubTriggers(legacy);

        assertFalse(service.isReonlineBlocked("npm-remote", "https://bad-host.example.com"));
    }

    @Test
    void isReonlineBlockedReturnsFalseWhenTriggersConfigMissingFromDb() {
        when(configsService.getConfig(eq(REMOTE_REPO_OFFLINE_TRIGGERS), eq(RemoteRepoOfflineTriggersConfig.class)))
                .thenReturn(null);

        assertFalse(service.isReonlineBlocked("npm-remote", "https://bad-host.example.com"));
    }

    @Test
    void urlChangeInProbeModeClearsHeadersAndDoesNotReonline() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE));
        stubMarked(Set.of("npm-remote"));

        service.onRemoteRepoUrlChanged("npm-remote", "https://good-host.example.com");

        verify(auditService).remove("npm-remote");
        verify(repositoryService, never()).updateRepoConfig(any());
        verify(configsService, never())
                .addOrUpdateConfig(any(ConfigsServiceKey.class), any(Object.class), anyLong());
    }

    private RemoteRepoOfflineTriggersConfig newModeTriggers(long initEpochMillis) {
        RemoteRepoOfflineTriggersConfig t =
                triggers(Set.of("bad-host"), Set.of("x-bad"), RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW);
        t.setInitEpochMillis(initEpochMillis);
        return t;
    }

    @Test
    void pfw3NewOfflinesPostInitRepoOnHeaderMatch() {
        long init = 1_000_000_000L;
        stubTriggers(newModeTriggers(init));
        stubMarked(Set.of());
        RemoteRepoConfig postInit = remoteRepo("new-repo", "https://registry.example.com", false, init + 1);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(postInit));
        when(auditService.getHeaderNames("new-repo")).thenReturn(Set.of("x-bad"));

        service.evaluate();

        verify(postInit.getRepoTypeConfig()).setOffline(true);
        verify(repositoryService).updateRepoConfig(postInit);
        verify(onlineMonitorService).stopMonitorForRepo("new-repo");
    }

    @Test
    void pfw3NewLeavesPreInitRepoUntouchedEvenOnHeaderMatch() {
        long init = 1_000_000_000L;
        stubTriggers(newModeTriggers(init));
        stubMarked(Set.of());
        RemoteRepoConfig preInit = remoteRepo("legacy-repo", "https://registry.example.com", false, init - 1);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(preInit));
        when(auditService.getHeaderNames("legacy-repo")).thenReturn(Set.of("x-bad"));

        service.evaluate();

        verify(preInit.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
        verifyNoInteractions(onlineMonitorService);
    }

    @Test
    void pfw3NewDoesNotReonlinePreInitRepoCarryingStaleMarker() {
        // A pre-init repo previously offlined under PFW3_ALL must stay offline after admin switches to PFW3_NEW;
        // pre-init repos are out of scope for PFW3_NEW so the cron leaves them untouched.
        long init = 1_000_000_000L;
        stubTriggers(newModeTriggers(init));
        stubMarked(Set.of("legacy-repo"));
        RemoteRepoConfig preInit = remoteRepo("legacy-repo", "https://registry.example.com", true, init - 1);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(preInit));
        when(auditService.getHeaderNames("legacy-repo")).thenReturn(Set.of());

        service.evaluate();

        verify(preInit.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
    }

    @Test
    void pfw3AllOfflinesPreInitRepoOnHeaderMatch() {
        // PFW3_ALL ignores the new/existing split — any repo with header match is offlined.
        long init = 1_000_000_000L;
        RemoteRepoOfflineTriggersConfig t =
                triggers(Set.of(), Set.of("x-bad"), RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL);
        t.setInitEpochMillis(init);
        stubTriggers(t);
        stubMarked(Set.of());
        RemoteRepoConfig preInit = remoteRepo("legacy-repo", "https://registry.example.com", false, init - 1);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(preInit));
        when(auditService.getHeaderNames("legacy-repo")).thenReturn(Set.of("x-bad"));

        service.evaluate();

        verify(preInit.getRepoTypeConfig()).setOffline(true);
        verify(repositoryService).updateRepoConfig(preInit);
    }

    @Test
    void isReonlineBlockedReturnsFalseForPreInitRepoInPfw3New() {
        long init = 1_000_000_000L;
        stubTriggers(newModeTriggers(init));
        stubMarked(Set.of("legacy-repo"));
        RemoteRepoConfig preInit = remoteRepo("legacy-repo", "https://bad-host.example.com", true, init - 1);
        when(repositoryService.getRemoteRepoConfigByKey("legacy-repo")).thenReturn(preInit);

        assertFalse(service.isReonlineBlocked("legacy-repo", "https://bad-host.example.com"));
    }

    @Test
    void isReonlineBlockedReturnsTrueForPostInitRepoInPfw3New() {
        long init = 1_000_000_000L;
        stubTriggers(newModeTriggers(init));
        stubMarked(Set.of("new-repo"));
        RemoteRepoConfig postInit = remoteRepo("new-repo", "https://bad-host.example.com", true, init + 1);
        when(repositoryService.getRemoteRepoConfigByKey("new-repo")).thenReturn(postInit);

        assertTrue(service.isReonlineBlocked("new-repo", "https://bad-host.example.com"));
    }

    @Test
    void isCreateBlockedReturnsTrueInPfw3NewWhenUrlMatches() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW));

        assertTrue(service.isCreateBlocked("npm-remote", "https://bad-host.example.com/repo"));
    }

    @Test
    void isCreateBlockedReturnsTrueInPfw3AllWhenUrlMatches() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL));

        assertTrue(service.isCreateBlocked("npm-remote", "https://bad-host.example.com/repo"));
    }

    @Test
    void isCreateBlockedReturnsFalseInPfw3Probe() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE));

        assertFalse(service.isCreateBlocked("npm-remote", "https://bad-host.example.com/repo"));
    }

    @Test
    void isCreateBlockedReturnsFalseInPfw3Off() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_OFF));

        assertFalse(service.isCreateBlocked("npm-remote", "https://bad-host.example.com/repo"));
    }

    @Test
    void isCreateBlockedReturnsFalseWhenUrlDoesNotMatch() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL));

        assertFalse(service.isCreateBlocked("npm-remote", "https://safe.example.com/repo"));
    }

    @Test
    void isCreateBlockedExemptsWhitelistedKeys() {
        // Whitelist is a full exemption: URL create/update writes are also allowed for whitelisted repo keys.
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), Set.of("npm-remote")));

        assertFalse(service.isCreateBlocked("npm-remote", "https://bad-host.example.com/repo"));
    }

    @Test
    void isUpdateUrlBlockedReturnsFalseWhenUrlUnchanged() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL));

        assertFalse(service.isUpdateUrlBlocked("npm-remote",
                "https://bad-host.example.com/a", "https://bad-host.example.com/a"));
    }

    @Test
    void isUpdateUrlBlockedReturnsTrueWhenNewUrlMatchesInPfw3New() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW));

        assertTrue(service.isUpdateUrlBlocked("npm-remote",
                "https://good-host.example.com", "https://bad-host.example.com/repo"));
    }

    @Test
    void isUpdateUrlBlockedExemptsWhitelistedKeys() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), Set.of("npm-remote")));

        assertFalse(service.isUpdateUrlBlocked("npm-remote",
                "https://good-host.example.com", "https://bad-host.example.com/repo"));
    }

    @Test
    void isUpdateUrlBlockedReturnsFalseInPfw3Probe() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE));

        assertFalse(service.isUpdateUrlBlocked("npm-remote",
                "https://good-host.example.com", "https://bad-host.example.com/repo"));
    }

    @Test
    void updateTriggersAutoStampsInitEpochMillisOnFirstPfw3NewTransition() {
        // No persisted config yet (configsService returns null by default).
        RemoteRepoOfflineTriggersConfig input =
                triggers(Set.of("bad"), Set.of("x-bad"), RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW);
        long before = System.currentTimeMillis();

        service.updateTriggers(input);

        long after = System.currentTimeMillis();
        Long stamped = offlineTriggers().getInitEpochMillis();
        assertTrue(stamped != null && stamped >= before && stamped <= after,
                "initEpochMillis must be stamped within the call window");
    }

    @Test
    void updateTriggersRespectsAdminSuppliedInitEpochMillis() {
        long adminProvided = 1_700_000_000_000L;
        RemoteRepoOfflineTriggersConfig input =
                triggers(Set.of("bad"), Set.of("x-bad"), RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW);
        input.setInitEpochMillis(adminProvided);

        service.updateTriggers(input);

        assertEquals(adminProvided, offlineTriggers().getInitEpochMillis());
    }

    @Test
    void updateTriggersCarriesOverPreviousInitEpochMillisWhenPayloadOmitsIt() {
        long previouslyStamped = 1_600_000_000_000L;
        RemoteRepoOfflineTriggersConfig previouslyPersisted =
                triggers(Set.of("old"), Set.of("x-old"), RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW);
        previouslyPersisted.setInitEpochMillis(previouslyStamped);
        stubTriggers(previouslyPersisted);
        // Admin now sends an ALL update without an init date — must inherit the prior stamp.
        RemoteRepoOfflineTriggersConfig update =
                triggers(Set.of("new"), Set.of("x-new"), RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL);

        service.updateTriggers(update);

        assertEquals(previouslyStamped, offlineTriggers().getInitEpochMillis());
    }

    @Test
    void updateTriggersDoesNotStampInitEpochMillisWhenSwitchingDirectlyToPfw3All() {
        // Admin goes straight to PFW3_ALL without ever entering PFW3_NEW — no init date needed/stamped.
        RemoteRepoOfflineTriggersConfig input =
                triggers(Set.of("bad"), Set.of("x-bad"), RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL);

        service.updateTriggers(input);

        assertNull(offlineTriggers().getInitEpochMillis());
    }

    @Test
    void onContextCreatedDoesNotPersistWhenNoTriggersPersisted() {
        // Fresh install: nothing in DB. Default mode is PFW3_PROBE — probe config stays in-memory until an
        // admin saves via REST; no init-epoch stamping or DB write on startup.
        when(configsService.getConfig(eq(REMOTE_REPO_OFFLINE_TRIGGERS), eq(RemoteRepoOfflineTriggersConfig.class)))
                .thenReturn(null);

        service.onContextCreated();

        verify(configsService, never()).addOrUpdateConfig(
                eq(REMOTE_REPO_OFFLINE_TRIGGERS), any(Object.class), anyLong());
        assertEquals(RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE, offlineTriggers().getMode());
        assertNull(offlineTriggers().getInitEpochMillis());
    }

    @Test
    void onContextCreatedSeedsModeFromSaasConstantWhenNoDbRow() {
        // SaaS fresh install: nothing in DB. The SaaS constant is overridden to PFW3_NEW, which must drive the
        // cached mode even though the in-memory field default is PFW3_PROBE.
        when(aolService.isAol()).thenReturn(true);
        setStringSystemProperty(ConstantValues.unsupported3pfwSaasUpstreamBlockedMode, "PFW3_NEW");
        when(configsService.getConfig(eq(REMOTE_REPO_OFFLINE_TRIGGERS), eq(RemoteRepoOfflineTriggersConfig.class)))
                .thenReturn(null);

        service.onContextCreated();

        assertEquals(RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW, offlineTriggers().getMode());
    }

    @Test
    void evaluateUsesSaasConstantModeWhenDbRowMissing() {
        // No DB row + SaaS constant=PFW3_ALL: a URL match must offline the repo, because PFW3_ALL enforces URL
        // writes for every repo (PFW3_PROBE — the field default — would only log).
        when(aolService.isAol()).thenReturn(true);
        setStringSystemProperty(ConstantValues.unsupported3pfwSaasUpstreamBlockedMode, "PFW3_ALL");
        when(configsService.getConfig(eq(REMOTE_REPO_OFFLINE_TRIGGERS), eq(RemoteRepoOfflineTriggersConfig.class)))
                .thenReturn(null);
        stubMarked(Set.of());
        RemoteRepoOfflineTriggersConfig defaults = new RemoteRepoOfflineTriggersConfig();
        String knownBadHost = defaults.getUrlSubstrings().iterator().next();
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://" + knownBadHost + "/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.onContextCreated();
        service.evaluate();

        verify(repo.getRepoTypeConfig()).setOffline(true);
        verify(repositoryService).updateRepoConfig(repo);
    }

    @Test
    void dbModeWinsOverConstantWhenBothSet() {
        // Constant says PFW3_ALL, DB row says PFW3_OFF — DB must win, so the cron does not offline anything.
        when(aolService.isAol()).thenReturn(true);
        setStringSystemProperty(ConstantValues.unsupported3pfwSaasUpstreamBlockedMode, "PFW3_ALL");
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_OFF));
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://bad-host.example.com/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.evaluate();

        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
        verify(repositoryService, never()).updateRepoConfig(any());
    }

    @Test
    void constantWinsWhenDbRowHasNullMode() {
        // DB row exists but its mode is null (legacy data / explicit null). Per chosen semantics, the constant
        // takes over rather than falling back to PFW3_PROBE.
        when(aolService.isAol()).thenReturn(true);
        setStringSystemProperty(ConstantValues.unsupported3pfwSaasUpstreamBlockedMode, "PFW3_ALL");
        RemoteRepoOfflineTriggersConfig legacy =
                triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE);
        legacy.setMode(null);
        stubTriggers(legacy);
        stubMarked(Set.of());
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://bad-host.example.com/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.evaluate();

        verify(repo.getRepoTypeConfig()).setOffline(true);
        verify(repositoryService).updateRepoConfig(repo);
    }

    @Test
    void invalidSaasConstantValueFallsBackToProbe() {
        // Garbage in the SaaS system property must not throw — the resolver returns PFW3_PROBE so the cron stays
        // in detect-only mode. The repo URL contains one of DEFAULT_URL_SUBSTRINGS so that a stray PFW3_ALL would
        // visibly offline; under PFW3_PROBE the assertion is meaningful.
        when(aolService.isAol()).thenReturn(true);
        setStringSystemProperty(ConstantValues.unsupported3pfwSaasUpstreamBlockedMode, "NOT_A_MODE");
        when(configsService.getConfig(eq(REMOTE_REPO_OFFLINE_TRIGGERS), eq(RemoteRepoOfflineTriggersConfig.class)))
                .thenReturn(null);
        stubMarked(Set.of());
        String knownBadHost =
                new RemoteRepoOfflineTriggersConfig().getUrlSubstrings().iterator().next();
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://" + knownBadHost + "/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.onContextCreated();
        service.evaluate();

        assertEquals(RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE, offlineTriggers().getMode());
        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
    }

    @Test
    void onContextCreatedSeedsModeFromShConstantWhenNoDbRowOnSelfHosted() {
        // Self-hosted fresh install: nothing in DB. The SH constant is overridden to PFW3_NEW, which must drive
        // the cached mode. isAol is already stubbed false in @BeforeEach.
        setStringSystemProperty(ConstantValues.unsupported3pfwShUpstreamBlockedMode, "PFW3_NEW");
        when(configsService.getConfig(eq(REMOTE_REPO_OFFLINE_TRIGGERS), eq(RemoteRepoOfflineTriggersConfig.class)))
                .thenReturn(null);

        service.onContextCreated();

        assertEquals(RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW, offlineTriggers().getMode());
    }

    @Test
    void evaluateUsesShConstantModeWhenDbRowMissingOnSelfHosted() {
        // No DB row, isAol=false, SH constant=PFW3_ALL: URL match offlines the repo.
        setStringSystemProperty(ConstantValues.unsupported3pfwShUpstreamBlockedMode, "PFW3_ALL");
        when(configsService.getConfig(eq(REMOTE_REPO_OFFLINE_TRIGGERS), eq(RemoteRepoOfflineTriggersConfig.class)))
                .thenReturn(null);
        stubMarked(Set.of());
        String knownBadHost =
                new RemoteRepoOfflineTriggersConfig().getUrlSubstrings().iterator().next();
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://" + knownBadHost + "/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.onContextCreated();
        service.evaluate();

        verify(repo.getRepoTypeConfig()).setOffline(true);
        verify(repositoryService).updateRepoConfig(repo);
    }

    @Test
    void selfHostedIgnoresSaasConstant() {
        // Cross-channel guard: an aggressive SaaS constant must not bleed into a self-hosted deployment. SH is
        // left at default (PFW3_PROBE), so even though SaaS says PFW3_ALL, the cron stays in detect-only mode.
        setStringSystemProperty(ConstantValues.unsupported3pfwSaasUpstreamBlockedMode, "PFW3_ALL");
        when(configsService.getConfig(eq(REMOTE_REPO_OFFLINE_TRIGGERS), eq(RemoteRepoOfflineTriggersConfig.class)))
                .thenReturn(null);
        stubMarked(Set.of());
        String knownBadHost =
                new RemoteRepoOfflineTriggersConfig().getUrlSubstrings().iterator().next();
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://" + knownBadHost + "/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.onContextCreated();
        service.evaluate();

        assertEquals(RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE, offlineTriggers().getMode());
        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
    }

    @Test
    void onContextCreatedLogsResolvedMode() {
        // The startup log lets ops verify the active mode without inspecting state via REST.
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL));
        stubMarked(Set.of());
        ListAppender<ILoggingEvent> appender = attachLogAppender();

        try {
            service.onContextCreated();

            String message = findStartupLog(appender);
            assertTrue(message.contains("mode=PFW3_ALL"), message);
        } finally {
            detachLogAppender(appender);
        }
    }

    private static ListAppender<ILoggingEvent> attachLogAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(RemoteRepoOfflineGuardServiceImpl.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        return appender;
    }

    private static void detachLogAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(RemoteRepoOfflineGuardServiceImpl.class);
        logger.detachAppender(appender);
    }

    private static String findStartupLog(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("Remote repo offline guard initialized"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("startup INFO log not found; captured: " + appender.list));
    }

    @Test
    void saasIgnoresShConstant() {
        // Mirror: an aggressive SH constant must not bleed into a SaaS deployment.
        when(aolService.isAol()).thenReturn(true);
        setStringSystemProperty(ConstantValues.unsupported3pfwShUpstreamBlockedMode, "PFW3_ALL");
        when(configsService.getConfig(eq(REMOTE_REPO_OFFLINE_TRIGGERS), eq(RemoteRepoOfflineTriggersConfig.class)))
                .thenReturn(null);
        stubMarked(Set.of());
        String knownBadHost =
                new RemoteRepoOfflineTriggersConfig().getUrlSubstrings().iterator().next();
        RemoteRepoConfig repo = remoteRepo("npm-remote", "https://" + knownBadHost + "/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(repo));

        service.onContextCreated();
        service.evaluate();

        assertEquals(RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE, offlineTriggers().getMode());
        verify(repo.getRepoTypeConfig(), never()).setOffline(anyBoolean());
    }

    @Test
    void onContextCreatedDoesNotStampInitEpochMillisWhenTriggersAlreadyPersisted() {
        // Upgrade / restart path: a config already exists in DB. Honor its mode/initEpochMillis verbatim and
        // never re-stamp — re-stamping would silently reshuffle which repos are "new" vs "existing".
        long previouslyStamped = 1_600_000_000_000L;
        RemoteRepoOfflineTriggersConfig fromDb =
                triggers(Set.of("bad"), Set.of("x-bad"), RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW);
        fromDb.setInitEpochMillis(previouslyStamped);
        stubTriggers(fromDb);
        stubMarked(Set.of());

        service.onContextCreated();

        verify(configsService, never()).addOrUpdateConfig(
                eq(REMOTE_REPO_OFFLINE_TRIGGERS), any(Object.class), anyLong());
        assertEquals(previouslyStamped, offlineTriggers().getInitEpochMillis());
    }

    private RemoteRepoOfflineTriggersConfig offlineTriggers() {
        return service.getRemoteRepoOfflineGuardTriggersConfig();
    }

    // ---- Metrics ----

    @Test
    void probeWouldOfflineGaugeRegisteredWithProbeModeTag() {
        stubTriggers(triggers(Set.of(), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE));
        stubMarked(Set.of());
        service.onContextCreated();
        assertNotNull(meterRegistry.find("remote.repo.offline.guard.probe.would.offline")
                .tag("mode", "PFW3_PROBE").gauge());
        assertNull(meterRegistry.find("remote.repo.offline.guard.offlined.repos").gauge());
    }

    @Test
    void offlinedReposGaugeRegisteredWithCorrectModeTag() {
        stubTriggers(triggers(Set.of(), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW));
        stubMarked(Set.of());
        service.onContextCreated();
        assertNotNull(meterRegistry.find("remote.repo.offline.guard.offlined.repos")
                .tag("mode", "PFW3_NEW").gauge());
        assertNull(meterRegistry.find("remote.repo.offline.guard.probe.would.offline").gauge());
    }

    @Test
    void noModeSpecificGaugesRegisteredInOffMode() {
        stubTriggers(triggers(Set.of(), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_OFF));
        stubMarked(Set.of());
        service.onContextCreated();
        assertNull(meterRegistry.find("remote.repo.offline.guard.probe.would.offline").gauge());
        assertNull(meterRegistry.find("remote.repo.offline.guard.offlined.repos").gauge());
    }

    @Test
    void probeWouldOfflineGaugeCountsMatchingOnlineRepos() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE));
        stubMarked(Set.of());
        service.onContextCreated();
        RemoteRepoConfig match1 = remoteRepo("repo1", "https://bad-host.example.com/repo", false);
        RemoteRepoConfig match2 = remoteRepo("repo2", "https://bad-host.example.com/repo", false);
        RemoteRepoConfig safe = remoteRepo("repo3", "https://safe.example.com/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(match1, match2, safe));

        service.evaluate();

        assertEquals(2.0,
                meterRegistry.get("remote.repo.offline.guard.probe.would.offline").gauge().value(), 0.001);
    }

    @Test
    void probeWouldOfflineGaugeResetsOnEachEvaluateCycle() {
        stubTriggers(triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE));
        stubMarked(Set.of());
        service.onContextCreated();
        RemoteRepoConfig match = remoteRepo("repo1", "https://bad-host.example.com/repo", false);
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(match));
        service.evaluate();
        assertEquals(1.0,
                meterRegistry.get("remote.repo.offline.guard.probe.would.offline").gauge().value(), 0.001);

        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of());
        service.evaluate();
        assertEquals(0.0,
                meterRegistry.get("remote.repo.offline.guard.probe.would.offline").gauge().value(), 0.001);
    }

    @Test
    void probeWouldOfflineGaugeIgnoresAlreadyOfflineAndWhitelistedRepos() {
        RemoteRepoOfflineTriggersConfig t =
                triggers(Set.of("bad-host"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE);
        t.setWhitelistedRepoKeys(Set.of("whitelisted"));
        stubTriggers(t);
        stubMarked(Set.of());
        service.onContextCreated();
        RemoteRepoConfig alreadyOffline = remoteRepo("repo1", "https://bad-host.example.com/repo", true);
        RemoteRepoConfig whitelisted = remoteRepo("whitelisted", "https://bad-host.example.com/repo", false);
        RemoteRepoConfig wouldOffline = remoteRepo("repo3", "https://bad-host.example.com/repo", false);
        when(repositoryService.getRemoteRepoConfigs())
                .thenReturn(List.of(alreadyOffline, whitelisted, wouldOffline));

        service.evaluate();

        assertEquals(1.0,
                meterRegistry.get("remote.repo.offline.guard.probe.would.offline").gauge().value(), 0.001);
    }

    @Test
    void modeChangeFromProbeToAllSwapsGaugesAndUpdatesTag() {
        stubTriggers(triggers(Set.of(), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE));
        stubMarked(Set.of());
        service.onContextCreated();
        assertNotNull(meterRegistry.find("remote.repo.offline.guard.probe.would.offline").gauge());
        assertNull(meterRegistry.find("remote.repo.offline.guard.offlined.repos").gauge());

        service.updateTriggers(triggers(Set.of("bad"), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_ALL));

        assertNull(meterRegistry.find("remote.repo.offline.guard.probe.would.offline").gauge());
        assertNotNull(meterRegistry.find("remote.repo.offline.guard.offlined.repos")
                .tag("mode", "PFW3_ALL").gauge());
    }

    @Test
    void reloadTriggersFromDbSwapsGaugesOnModeChange() {
        stubTriggers(triggers(Set.of(), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_PROBE));
        stubMarked(Set.of());
        service.onContextCreated();
        assertNotNull(meterRegistry.find("remote.repo.offline.guard.probe.would.offline").gauge());

        stubTriggers(triggers(Set.of(), Set.of(), RemoteRepoOfflineTriggersConfig.Mode.PFW3_NEW));
        service.reloadTriggersFromDb();

        assertNull(meterRegistry.find("remote.repo.offline.guard.probe.would.offline").gauge());
        assertNotNull(meterRegistry.find("remote.repo.offline.guard.offlined.repos")
                .tag("mode", "PFW3_NEW").gauge());
    }
}
