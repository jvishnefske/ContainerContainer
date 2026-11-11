package org.artifactory.schedule;

import jakarta.annotation.Nonnull;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.ReplicationBaseDescriptor;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class BaseTaskServiceDescriptorHandlerTest extends ArtifactoryHomeBoundTest {

    @BeforeMethod
    public void setup() {
        homeStub.setProperty(ConstantValues.taskRetryEnable.getPropertyName(), "true");
    }

    @Test
    public void testActivateWithRetry() {
        TestBaseTaskService testBaseTaskService = spy(new TestBaseTaskService());
        ReplicationBaseDescriptor oldReplication = createReplication();
        ReplicationBaseDescriptor newReplication = createReplication();
        TaskService taskService = mock(TaskService.class);

        doThrow(new IllegalStateException("First try fails")).doNothing().when(testBaseTaskService)
                .activate(any(ReplicationBaseDescriptor.class), anyBoolean(), any(BasicStatusHolder.class));
        testBaseTaskService.activateWithRetry(taskService, newReplication, oldReplication, 0);
        verify(taskService, times(2)).cancelTasks(testBaseTaskService.getPredicate(oldReplication), true);
        verify(testBaseTaskService, times(2))
                .activateWithRetry(eq(taskService), eq(newReplication), eq(oldReplication), anyInt());
    }

    @Test
    public void testActivateWithRetryFails() {
        TestBaseTaskService testBaseTaskService = spy(new TestBaseTaskService());
        ReplicationBaseDescriptor oldReplication = createReplication();
        ReplicationBaseDescriptor newReplication = createReplication();
        TaskService taskService = mock(TaskService.class);

        IllegalStateException exception = new IllegalStateException("First try fails");
        doThrow(exception).when(testBaseTaskService)
                .activate(any(ReplicationBaseDescriptor.class), anyBoolean(), any(BasicStatusHolder.class));
        try {
            testBaseTaskService.activateWithRetry(taskService, newReplication, oldReplication, 0);
            Assert.fail("Expected to fail");
        } catch (IllegalStateException e) {
            Assert.assertEquals(e, exception);
        }
        verify(taskService, times(4)).cancelTasks(testBaseTaskService.getPredicate(oldReplication), true);
        verify(testBaseTaskService, times(4))
                .activateWithRetry(eq(taskService), eq(newReplication), eq(oldReplication), anyInt());
    }

    @Test
    public void testActivateWithoutRetry() {
        homeStub.setProperty(ConstantValues.taskRetryEnable.getPropertyName(), "false");
        TestBaseTaskService testBaseTaskService = spy(new TestBaseTaskService());
        ReplicationBaseDescriptor oldReplication = createReplication();
        ReplicationBaseDescriptor newReplication = createReplication();
        TaskService taskService = mock(TaskService.class);

        IllegalStateException exception = new IllegalStateException("First try fails");
        doThrow(exception).when(testBaseTaskService)
                .activate(any(ReplicationBaseDescriptor.class), anyBoolean(), any(BasicStatusHolder.class));
        try {
            testBaseTaskService.activateWithRetry(taskService, newReplication, oldReplication, 0);
            Assert.fail("Expected to fail");
        } catch (IllegalStateException e) {
            Assert.assertEquals(e, exception);
        }
        verify(taskService, times(1)).cancelTasks(testBaseTaskService.getPredicate(oldReplication), true);
        verify(testBaseTaskService, times(1))
                .activateWithRetry(eq(taskService), eq(newReplication), eq(oldReplication), anyInt());
    }

    @Nonnull
    private ReplicationBaseDescriptor createReplication() {
        ReplicationBaseDescriptor oldReplication = new LocalReplicationDescriptor();
        oldReplication.setRepoKey("rep-key");
        oldReplication.setCronExp("0 4 9/12 * * ?");
        oldReplication.setEnabled(true);
        return oldReplication;
    }

    static class TestBaseTaskService extends BaseTaskServiceDescriptorHandler<ReplicationBaseDescriptor> {

        @Override
        public String jobName() {
            return null;
        }

        @Override
        public List<ReplicationBaseDescriptor> getNewDescriptors() {
            return null;
        }

        @Override
        public List<ReplicationBaseDescriptor> getOldDescriptors() {
            return null;
        }

        @Override
        public Predicate<Task> getAllPredicate() {
            return null;
        }

        @Override
        public Predicate<Task> getPredicate(@Nonnull ReplicationBaseDescriptor descriptor) {
            return task -> true;
        }

        @Override
        public void activate(@Nonnull ReplicationBaseDescriptor descriptor, boolean manual,
                BasicStatusHolder statusHolder) {

        }

        @Override
        public ReplicationBaseDescriptor findOldFromNew(@Nonnull ReplicationBaseDescriptor newDescriptor) {
            return null;
        }
    }
}