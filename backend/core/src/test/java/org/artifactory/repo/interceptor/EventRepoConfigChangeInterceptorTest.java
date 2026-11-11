package org.artifactory.repo.interceptor;

import org.artifactory.exception.WorkerStopException;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventRepoConfigChangeInterceptorTest {

    @Mock
    private RepoConfigCudEventEmitter eventEmitter;
    @InjectMocks
    private EventRepoConfigChangeInterceptor underTest;

    @Test
    void onBeforeSave_shouldDelegateToEmitter_forEachCudChange() {
        RepoConfig repo = userManagedRepo("libs-release-local");
        RepoConfigChange change = RepoConfigChange.createOf(repo);
        RepoConfigChanges changes = new RepoConfigChanges(change);

        underTest.onBeforeSave(changes);

        verify(eventEmitter).emitBefore(change);
        verify(eventEmitter, never()).emitAfter(change);
    }

    @Test
    void onAfterSave_shouldDelegateToEmitter_forEachCudChange() {
        RepoConfig repo = userManagedRepo("libs-release-local");
        RepoConfigChange change = RepoConfigChange.createOf(repo);
        RepoConfigChanges changes = new RepoConfigChanges(change);

        underTest.onAfterSave(changes);

        verify(eventEmitter).emitAfter(change);
        verify(eventEmitter, never()).emitBefore(change);
    }

    @Test
    void onBeforeSave_shouldAbortRemainingChanges_whenEmitterStopsFirstRepo() {
        RepoConfigChange firstChange = RepoConfigChange.deleteOf(userManagedRepo("repo-one"));
        RepoConfigChange secondChange = RepoConfigChange.deleteOf(userManagedRepo("repo-two"));
        RepoConfigChanges changes = RepoConfigChanges.of(List.of(firstChange, secondChange));
        WorkerStopException stopException = new WorkerStopException("blocked by worker");
        doThrow(stopException).when(eventEmitter).emitBefore(firstChange);

        WorkerStopException thrown = assertThrows(WorkerStopException.class, () -> underTest.onBeforeSave(changes));

        assertSame(stopException, thrown);
        verify(eventEmitter).emitBefore(firstChange);
        verify(eventEmitter, never()).emitBefore(secondChange);
    }

    @Test
    void onBeforeSave_shouldDelegateAllCudChanges_withoutFilteringLocally() {
        RepoConfig systemRepo = mock(RepoConfig.class);
        when(systemRepo.getKey()).thenReturn(BUILDINFO_REPO_KEY);
        when(systemRepo.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        RepoConfigChange systemChange = RepoConfigChange.createOf(systemRepo);
        RepoConfigChanges changes = new RepoConfigChanges(systemChange);

        underTest.onBeforeSave(changes);

        verify(eventEmitter).emitBefore(systemChange);
    }

    private static RepoConfig userManagedRepo(String repoKey) {
        RepoConfig repo = mock(RepoConfig.class);
        when(repo.getKey()).thenReturn(repoKey);
        when(repo.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        return repo;
    }
}
