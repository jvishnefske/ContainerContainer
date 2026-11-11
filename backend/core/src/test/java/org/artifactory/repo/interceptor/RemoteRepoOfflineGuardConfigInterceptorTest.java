package org.artifactory.repo.interceptor;

import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.service.RemoteRepoOfflineGuardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Hussein Alshqerat
 */
@ExtendWith(MockitoExtension.class)
class RemoteRepoOfflineGuardConfigInterceptorTest {

    @Mock
    RemoteRepoOfflineGuardService offlineGuardService;

    RemoteRepoOfflineGuardConfigInterceptor interceptor;
    AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        interceptor = new RemoteRepoOfflineGuardConfigInterceptor(offlineGuardService);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    private static RemoteRepoConfig remoteWith(String url, boolean offline) {
        RemoteRepoConfig repo = mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig typeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(repo.getRepoTypeConfig()).thenReturn(typeConfig);
        lenient().when(typeConfig.getUrl()).thenReturn(url);
        lenient().when(typeConfig.isOffline()).thenReturn(offline);
        return repo;
    }

    private static RepoConfigChanges updateChange(String repoKey, RepoConfig oldCfg, RepoConfig newCfg) {
        RepoConfigChange change = new RepoConfigChange(repoKey, REPO_UPDATE, newCfg, oldCfg);
        return new RepoConfigChanges(Map.of(RepoDetailsType.REMOTE, List.of(change)));
    }

    private static RepoConfigChanges createChange(String repoKey, RepoConfig newCfg) {
        RepoConfigChange change = new RepoConfigChange(repoKey, REPO_CREATE, newCfg, null);
        return new RepoConfigChanges(Map.of(RepoDetailsType.REMOTE, List.of(change)));
    }

    @Test
    void notifiesGuardWhenUrlChanges() {
        RepoConfigChanges changes = updateChange("npm-remote",
                remoteWith("https://old.example.com", false),
                remoteWith("https://new.example.com", false));

        interceptor.onAfterSave(changes);

        verify(offlineGuardService).onRemoteRepoUrlChanged("npm-remote", "https://new.example.com");
    }

    @Test
    void noNotificationWhenUrlUnchanged() {
        RepoConfigChanges changes = updateChange("npm-remote",
                remoteWith("https://same.example.com", false),
                remoteWith("https://same.example.com", false));

        interceptor.onAfterSave(changes);

        verify(offlineGuardService, never()).onRemoteRepoUrlChanged(any(), any());
    }

    @Test
    void onBeforeSaveBlocksReonlineWhenGuardStillMatches() {
        RemoteRepoConfig oldCfg = remoteWith("https://bad-host.example.com", true);
        RemoteRepoConfig newCfg = remoteWith("https://bad-host.example.com", false);
        when(offlineGuardService.isReonlineBlocked(eq("npm-remote"), eq("https://bad-host.example.com")))
                .thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> interceptor.onBeforeSave(updateChange("npm-remote", oldCfg, newCfg)));

        assertTrue(ex.getMessage().contains("Contact JFrog support"));
    }

    @Test
    void onBeforeSaveAllowsReonlineWhenGuardClears() {
        RemoteRepoConfig oldCfg = remoteWith("https://bad-host.example.com", true);
        RemoteRepoConfig newCfg = remoteWith("https://good-host.example.com", false);
        when(offlineGuardService.isReonlineBlocked(eq("npm-remote"), eq("https://good-host.example.com")))
                .thenReturn(false);

        interceptor.onBeforeSave(updateChange("npm-remote", oldCfg, newCfg));
    }

    @Test
    void onBeforeSaveSkipsWhenAdminKeepsRepoOffline() {
        RemoteRepoConfig oldCfg = remoteWith("https://bad-host.example.com", true);
        RemoteRepoConfig newCfg = remoteWith("https://bad-host.example.com", true);

        interceptor.onBeforeSave(updateChange("npm-remote", oldCfg, newCfg));

        verify(offlineGuardService, never()).isReonlineBlocked(any(), any());
    }

    @Test
    void onBeforeSaveSkipsWhenRepoWasAlreadyOnline() {
        RemoteRepoConfig oldCfg = remoteWith("https://bad-host.example.com", false);
        RemoteRepoConfig newCfg = remoteWith("https://bad-host.example.com", false);

        interceptor.onBeforeSave(updateChange("npm-remote", oldCfg, newCfg));

        verify(offlineGuardService, never()).isReonlineBlocked(any(), any());
    }

    @Test
    void onBeforeSaveSkipsWhenNewConfigIsNotRemote() {
        RemoteRepoConfig oldCfg = remoteWith("https://bad-host.example.com", true);
        VirtualRepoConfig newCfg = mock(VirtualRepoConfig.class);

        interceptor.onBeforeSave(updateChange("npm-remote", oldCfg, newCfg));

        verify(offlineGuardService, never()).isReonlineBlocked(any(), any());
    }

    @Test
    void onBeforeSaveSkipsWhenOldConfigIsNotRemote() {
        VirtualRepoConfig oldCfg = mock(VirtualRepoConfig.class);
        RemoteRepoConfig newCfg = remoteWith("https://bad-host.example.com", false);

        interceptor.onBeforeSave(updateChange("npm-remote", oldCfg, newCfg));

        verify(offlineGuardService, never()).isReonlineBlocked(any(), any());
    }

    @Test
    void onAfterSaveSwallowsExceptionsFromGuard() {
        RemoteRepoConfig oldCfg = remoteWith("https://old.example.com", false);
        RemoteRepoConfig newCfg = remoteWith("https://new.example.com", false);
        org.mockito.Mockito.doThrow(new RuntimeException("guard exploded"))
                .when(offlineGuardService).onRemoteRepoUrlChanged(any(), any());

        interceptor.onAfterSave(updateChange("npm-remote", oldCfg, newCfg));

        verify(offlineGuardService).onRemoteRepoUrlChanged("npm-remote", "https://new.example.com");
    }

    @Test
    void onAfterSaveSkipsWhenNewConfigIsNotRemote() {
        RemoteRepoConfig oldCfg = remoteWith("https://old.example.com", false);
        VirtualRepoConfig newCfg = mock(VirtualRepoConfig.class);

        interceptor.onAfterSave(updateChange("npm-remote", oldCfg, newCfg));

        verify(offlineGuardService, never()).onRemoteRepoUrlChanged(any(), any());
    }

    @Test
    void onAfterSaveSkipsWhenOldConfigIsNotRemote() {
        VirtualRepoConfig oldCfg = mock(VirtualRepoConfig.class);
        RemoteRepoConfig newCfg = remoteWith("https://new.example.com", false);

        interceptor.onAfterSave(updateChange("npm-remote", oldCfg, newCfg));

        verify(offlineGuardService, never()).onRemoteRepoUrlChanged(any(), any());
    }

    @Test
    void onBeforeSaveBlocksCreateWhenGuardSaysSo() {
        RemoteRepoConfig newCfg = remoteWith("https://bad-host.example.com", false);
        when(offlineGuardService.isCreateBlocked("npm-remote", "https://bad-host.example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> interceptor.onBeforeSave(createChange("npm-remote", newCfg)));

        assertTrue(ex.getMessage().contains("Contact JFrog support"));
    }

    @Test
    void onBeforeSaveAllowsCreateWhenGuardClears() {
        RemoteRepoConfig newCfg = remoteWith("https://safe.example.com", false);
        when(offlineGuardService.isCreateBlocked("npm-remote", "https://safe.example.com")).thenReturn(false);

        interceptor.onBeforeSave(createChange("npm-remote", newCfg));
    }

    @Test
    void onBeforeSaveSkipsCreateWhenNewConfigIsNotRemote() {
        VirtualRepoConfig newCfg = mock(VirtualRepoConfig.class);

        interceptor.onBeforeSave(createChange("npm-virtual", newCfg));

        verify(offlineGuardService, never()).isCreateBlocked(any(), any());
    }

    @Test
    void onBeforeSaveBlocksUpdateUrlWhenGuardSaysSo() {
        RemoteRepoConfig oldCfg = remoteWith("https://good.example.com", false);
        RemoteRepoConfig newCfg = remoteWith("https://bad-host.example.com", false);
        when(offlineGuardService.isUpdateUrlBlocked("npm-remote",
                "https://good.example.com", "https://bad-host.example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> interceptor.onBeforeSave(updateChange("npm-remote", oldCfg, newCfg)));

        assertTrue(ex.getMessage().contains("Contact JFrog support"));
    }

    @Test
    void onBeforeSaveAllowsUpdateUrlWhenGuardClears() {
        RemoteRepoConfig oldCfg = remoteWith("https://good.example.com", false);
        RemoteRepoConfig newCfg = remoteWith("https://still-good.example.com", false);
        when(offlineGuardService.isUpdateUrlBlocked("npm-remote",
                "https://good.example.com", "https://still-good.example.com")).thenReturn(false);

        interceptor.onBeforeSave(updateChange("npm-remote", oldCfg, newCfg));
    }
}
