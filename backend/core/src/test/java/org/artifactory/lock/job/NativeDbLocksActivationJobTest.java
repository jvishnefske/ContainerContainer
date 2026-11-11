package org.artifactory.lock.job;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.lock.ClusterNativeDbLocksMechanismStatus;
import org.artifactory.lock.NativeDbLocksMechanismStatus;
import org.artifactory.lock.service.NativeDbLocksActivationService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.AolUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.quartz.JobExecutionContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NativeDbLocksActivationJobTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private MutableCentralConfigDescriptor mutableCentralConfigDescriptor;
    @Mock
    private NativeDbLocksActivationService statusService;

    private TestNativeDbLocksActivationJob job;
    private AutoCloseable mocks;
    private MockedStatic<AolUtils> aolUtils;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        aolUtils = Mockito.mockStatic(AolUtils.class);
        aolUtils.when(AolUtils::isAol).thenReturn(true);
        job = new TestNativeDbLocksActivationJob();
        when(artifactoryContext.beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        when(artifactoryContext.beanForType(NativeDbLocksActivationService.class)).thenReturn(statusService);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableCentralConfigDescriptor);
        when(mutableCentralConfigDescriptor.isForceNativeDbLocksMechanismInAutoMode()).thenReturn(false);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        aolUtils.close();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void onExecuteActivatesNativeLocksAndUnschedulesWhenClusterReady() throws Exception {
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.READY_NOT_IN_USE);
        when(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext)).thenReturn(true);
        when(statusService.resolveClusterStatus(artifactoryContext)).thenReturn(
                new ClusterNativeDbLocksMechanismStatus(List.of(), List.of()));

        job.onExecute(mock(JobExecutionContext.class));

        verify(mutableCentralConfigDescriptor).setForceNativeDbLocksMechanismInAutoMode(true);
        verify(centralConfigService).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
        assertEquals(1, job.getCancelAttempts());
    }

    @Test
    void onExecuteUnschedulesWhenActivationNoLongerPending() throws Exception {
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.IN_USE);

        job.onExecute(mock(JobExecutionContext.class));

        verify(centralConfigService, never()).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
        assertEquals(1, job.getCancelAttempts());
    }

    @Test
    void onExecuteRetriesWhenClusterIsNotReady() throws Exception {
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.READY_NOT_IN_USE);
        when(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext)).thenReturn(true);
        when(statusService.resolveClusterStatus(artifactoryContext)).thenReturn(
                new ClusterNativeDbLocksMechanismStatus(List.of(), List.of("art-2")));

        job.onExecute(mock(JobExecutionContext.class));

        verify(centralConfigService, never()).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
        assertEquals(0, job.getCancelAttempts());
    }

    @Test
    void onExecuteRetriesWhenInfrastructureNotReady() throws Exception {
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.READY_NOT_IN_USE);
        when(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext)).thenReturn(false);

        job.onExecute(mock(JobExecutionContext.class));

        verify(centralConfigService, never()).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
        assertEquals(0, job.getCancelAttempts());
        verify(statusService, never()).resolveClusterStatus(artifactoryContext);
    }

    @Test
    void onExecuteUnschedulesWithoutSaveWhenForceNativeAlreadySetOnMutableDescriptor() throws Exception {
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.READY_NOT_IN_USE);
        when(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext)).thenReturn(true);
        when(statusService.resolveClusterStatus(artifactoryContext)).thenReturn(
                new ClusterNativeDbLocksMechanismStatus(List.of(), List.of()));
        when(mutableCentralConfigDescriptor.isForceNativeDbLocksMechanismInAutoMode()).thenReturn(true);

        job.onExecute(mock(JobExecutionContext.class));

        verify(centralConfigService, never()).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
        assertEquals(1, job.getCancelAttempts());
    }

    @Test
    void onExecuteRetriesWhenActivationSaveFails() throws Exception {
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.READY_NOT_IN_USE);
        when(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext)).thenReturn(true);
        when(statusService.resolveClusterStatus(artifactoryContext)).thenReturn(
                new ClusterNativeDbLocksMechanismStatus(List.of(), List.of()));
        doThrow(new RuntimeException("save failed")).when(centralConfigService)
                .saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);

        job.onExecute(mock(JobExecutionContext.class));

        verify(centralConfigService).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
        assertEquals(0, job.getCancelAttempts());
    }

    @Test
    void onExecuteUnschedulesWhenLocalStatusIsSupportedNotReady() throws Exception {
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.SUPPORTED_NOT_READY);

        job.onExecute(mock(JobExecutionContext.class));

        verify(centralConfigService, never()).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
        assertEquals(1, job.getCancelAttempts());
        verify(statusService, never()).resolveClusterStatus(artifactoryContext);
    }

    @Test
    void onExecuteUnschedulesWhenLocalStatusIsNotSupported() throws Exception {
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.NOT_SUPPORTED);

        job.onExecute(mock(JobExecutionContext.class));

        verify(centralConfigService, never()).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
        assertEquals(1, job.getCancelAttempts());
        verify(statusService, never()).resolveClusterStatus(artifactoryContext);
    }

    @Test
    void onExecuteSkipsWhenNotOnSaas() throws Exception {
        aolUtils.when(AolUtils::isAol).thenReturn(false);

        job.onExecute(mock(JobExecutionContext.class));

        verify(statusService, never()).resolveLocalStatus();
        verify(centralConfigService, never()).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
        assertEquals(0, job.getCancelAttempts());
    }

    @Test
    void onExecuteSkipsWhenContextIsNotBound() throws Exception {
        ArtifactoryContextThreadBinder.unbind();

        job.onExecute(mock(JobExecutionContext.class));

        verify(statusService, never()).resolveLocalStatus();
        verify(centralConfigService, never()).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
        assertEquals(0, job.getCancelAttempts());
    }

    @Test
    void onExecuteRetriesWhenPeerAlreadyInUse() throws Exception {
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.READY_NOT_IN_USE);
        when(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext)).thenReturn(true);
        when(statusService.resolveClusterStatus(artifactoryContext)).thenReturn(
                new ClusterNativeDbLocksMechanismStatus(
                        List.of(
                                new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-1",
                                        NativeDbLocksMechanismStatus.READY_NOT_IN_USE),
                                new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-2",
                                        NativeDbLocksMechanismStatus.IN_USE)),
                        List.of()));

        job.onExecute(mock(JobExecutionContext.class));

        verify(centralConfigService, never()).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
        assertEquals(0, job.getCancelAttempts());
    }

    @Test
    void onExecuteRetriesWhenUnexpectedExceptionOccurs() throws Exception {
        when(statusService.resolveLocalStatus()).thenThrow(new RuntimeException("transient failure"));

        job.onExecute(mock(JobExecutionContext.class));

        verify(centralConfigService, never()).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
        assertEquals(0, job.getCancelAttempts());
    }

    @Test
    void onExecuteCompletesActivationWhenUnscheduleFailsAfterSuccessfulSave() throws Exception {
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.READY_NOT_IN_USE);
        when(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext)).thenReturn(true);
        when(statusService.resolveClusterStatus(artifactoryContext)).thenReturn(
                new ClusterNativeDbLocksMechanismStatus(List.of(), List.of()));
        job.setCancelThrows(true);

        job.onExecute(mock(JobExecutionContext.class));

        verify(mutableCentralConfigDescriptor).setForceNativeDbLocksMechanismInAutoMode(true);
        verify(centralConfigService).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
        assertEquals(1, job.getCancelAttempts());
    }

    private static class TestNativeDbLocksActivationJob extends NativeDbLocksActivationJob {

        private int cancelAttempts;
        private boolean cancelThrows;

        void setCancelThrows(boolean cancelThrows) {
            this.cancelThrows = cancelThrows;
        }

        int getCancelAttempts() {
            return cancelAttempts;
        }

        @Override
        protected void cancelMyself() {
            cancelAttempts++;
            if (cancelThrows) {
                throw new RuntimeException("unschedule failed");
            }
        }
    }
}
