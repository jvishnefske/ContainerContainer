package org.artifactory.repo.interceptor;

import org.artifactory.common.ConstantValues;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.repo.interceptor.repocud.RepoConfigEventDataBuilder;
import org.artifactory.unifiedevent.UnifiedEventService;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.repo.RepoConfigEventData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepoConfigCudEventEmitterTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private RepoConfigEventDataBuilder eventDataBuilder;
    @Mock
    private UnifiedEventService unifiedEventService;

    private AutoCloseable mocks;
    private RepoConfigCudEventEmitter underTest;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        getOrCreateArtifactoryHomeStub();
        underTest = new RepoConfigCudEventEmitter(eventDataBuilder, unifiedEventService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void emitBefore_shouldSkip_whenWorkerFlagDisabled() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudWorkerEventsEnabled, "false");
        RepoConfigChange change = RepoConfigChange.createOf(mockRepo("my-repo"));

        underTest.emitBefore(change);

        verify(unifiedEventService, never()).sendRepoConfigEvent(any(), any(), any());
    }

    @Test
    void emitAfter_shouldSkip_whenBothFlagsDisabled() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudWorkerEventsEnabled, "false");
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudJfbusEventsEnabled, "false");
        RepoConfigChange change = RepoConfigChange.createOf(mockRepo("my-repo"));

        underTest.emitAfter(change);

        verify(unifiedEventService, never()).sendRepoConfigEvent(any(), any(), any());
    }

    @Test
    void emitAfter_shouldBuildAndPublish_whenOnlyJfbusFlagEnabled() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudWorkerEventsEnabled, "false");
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudJfbusEventsEnabled, "true");
        RepoConfigChange change = RepoConfigChange.createOf(mockRepo("my-repo"));
        RepoConfigEventData eventData = RepoConfigEventData.builder().build();
        when(eventDataBuilder.build(eq(change), eq(SupportedUnifiedEvents.AFTER_REPO_CREATE), eq(null), any()))
                .thenReturn(eventData);

        underTest.emitAfter(change);

        verify(unifiedEventService).sendRepoConfigEvent(
                eq(SupportedUnifiedEvents.AFTER_REPO_CREATE), eq(eventData), eq("my-repo"));
    }

    @Test
    void emitBefore_shouldSkip_whenRepoIsSystemManaged() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudWorkerEventsEnabled, "true");
        RepoConfigChange change = RepoConfigChange.createOf(mockRepo(BUILDINFO_REPO_KEY));

        underTest.emitBefore(change);

        verify(unifiedEventService, never()).sendRepoConfigEvent(any(), any(), any());
    }

    @Test
    void emitBefore_shouldPropagateWorkerStopException() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudWorkerEventsEnabled, "true");
        RepoConfigChange change = RepoConfigChange.createOf(mockRepo("my-repo"));
        RepoConfigEventData eventData = RepoConfigEventData.builder().build();
        when(eventDataBuilder.build(eq(change), eq(SupportedUnifiedEvents.BEFORE_REPO_CREATE), eq(null), any()))
                .thenReturn(eventData);
        doThrow(new WorkerStopException("blocked")).when(unifiedEventService)
                .sendRepoConfigEvent(eq(SupportedUnifiedEvents.BEFORE_REPO_CREATE), eq(eventData), eq("my-repo"));

        assertThrows(WorkerStopException.class, () -> underTest.emitBefore(change));
    }

    @Test
    void emitBefore_shouldPublishUpdateEvent_whenRepoUpdated() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudWorkerEventsEnabled, "true");
        RepoConfig oldRepo = mockRepo("my-repo");
        RepoConfig newRepo = mockRepo("my-repo");
        RepoConfigChange change = RepoConfigChange.updateOf(oldRepo, newRepo);
        RepoConfigEventData eventData = RepoConfigEventData.builder().build();
        when(eventDataBuilder.build(eq(change), eq(SupportedUnifiedEvents.BEFORE_REPO_UPDATE), eq(null), any()))
                .thenReturn(eventData);

        underTest.emitBefore(change);

        verify(unifiedEventService).sendRepoConfigEvent(
                eq(SupportedUnifiedEvents.BEFORE_REPO_UPDATE), eq(eventData), eq("my-repo"));
    }

    @Test
    void emitAfter_shouldPublishDeleteWithStatusCode_whenPartialDeleteOccurred() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.repoCudWorkerEventsEnabled, "true");
        RepoConfigChange change = RepoConfigChange.deleteOf(mockRepo("my-repo"));
        RepoConfigEventData eventData = RepoConfigEventData.builder().build();
        when(eventDataBuilder.build(eq(change), eq(SupportedUnifiedEvents.AFTER_REPO_DELETE), eq(207), any()))
                .thenReturn(eventData);

        underTest.emitAfter(change, 207);

        verify(unifiedEventService).sendRepoConfigEvent(
                eq(SupportedUnifiedEvents.AFTER_REPO_DELETE), eq(eventData), eq("my-repo"));
    }

    private static RepoConfig mockRepo(String repoKey) {
        RepoConfig repo = mock(RepoConfig.class);
        when(repo.getKey()).thenReturn(repoKey);
        when(repo.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        return repo;
    }
}
