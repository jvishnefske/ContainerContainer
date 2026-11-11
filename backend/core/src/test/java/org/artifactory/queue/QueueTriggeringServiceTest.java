package org.artifactory.queue;

import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import lombok.SneakyThrows;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueTriggerDecorator;
import org.artifactory.queue.error.PersistentQueueErrorService;
import org.artifactory.queue.execution.PersistentQueueTriggerWorkItem;
import org.artifactory.queue.execution.QueueTriggeringInternalService;
import org.artifactory.queue.execution.QueueTriggeringServiceImpl;
import org.artifactory.queue.register.PersistentQueueRegistrationService;
import org.artifactory.queue.register.RepoAndCursor;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.queue.services.PersistentQueueImpl;
import org.artifactory.schedule.TaskService;
import org.mockito.Mock;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Test
public class QueueTriggeringServiceTest extends PersistentQueueBaseTest {

    private QueueTriggeringServiceImpl queueTriggeringService;

    @Mock
    private PersistentQueueRegistrationService registrationService;

    @Mock
    private PersistentQueueErrorService errorService = null;

    @SneakyThrows
    @BeforeMethod
    public void setup() {
        queueTriggeringService = spy(new QueueTriggeringServiceImpl(registrationService, mock(TaskService.class),
                    errorService));
        when(artifactoryContext.beanForType(QueueTriggeringInternalService.class)).thenReturn(queueTriggeringService);
        when(conflictGuard.tryToLock(anyLong(), any())).thenReturn(true);
    }

    public void triggerQueuesByTimeForErrors() throws Exception {
        try {
            ArtifactoryHome home = mock(ArtifactoryHome.class);
            ArtifactoryHome.bind(home);

            when(errorService.getErroneousQueues()).thenReturn(Sets.newHashSet(new RepoAndCursor("demo",
                    QueueCursorType.INCREMENTAL_INDEX)));

            ArtifactorySystemProperties props = mock(ArtifactorySystemProperties.class);
            when(home.getArtifactoryProperties()).thenReturn(props);
            when(props.getLongProperty(eq(ConstantValues.persistentQueueCheckTriggerTimeSec))).thenReturn(100L);
            when(props.getLongProperty(eq(ConstantValues.persistentQueueErrorsCheckTriggerTimeSec))).thenReturn(1L);

            // Trigger full to init the last trigger ts.
            final TestingInvocationCountQueueImpl queue = createDemoQueue();
            queue.add(PersistentQueue.TriggerResult.FINISHED_ALL);
            queue.add(PersistentQueue.TriggerResult.NOTHING_TO_DO);
            queue.add(PersistentQueue.TriggerResult.NOTHING_TO_DO);
            mockRegistrationService(queue);

            // Trigger full to init the last trigger ts.
            queueTriggeringService.triggerQueue(new PersistentQueueTriggerWorkItem(queue));

            // Sleep more then the error check interval
            Thread.sleep(1500);

            // Trigger second time, this time should do by error
            queueTriggeringService.triggerQueuesByTime();
            // Expecting trigger by errors flow
            verify(errorService, times(1)).getErroneousQueues();
        } finally {
            ArtifactoryHome.unbind();
        }
    }

    public void testTriggerConstantQueues() {
        PersistentQueue persistentQueue = mock(PersistentQueue.class);
        QueueConfiguration queueConfiguration = mock(QueueConfiguration.class);
        when(persistentQueue.getQueueConfiguration()).thenReturn(queueConfiguration);
        QueueTriggerDecorator queueTriggerDecorator = mock(QueueTriggerDecorator.class);
        when(queueConfiguration.getTriggerDecorator()).thenReturn(queueTriggerDecorator);
        when(queueTriggerDecorator.trigger(any())).thenReturn(() -> {});
        when(registrationService.getConstantQueues()).thenReturn(List.of(persistentQueue));
        queueTriggeringService.triggerConstantQueues();
        verify(queueTriggerDecorator).trigger(any());
    }

    public void triggerQueuesByTime() {
        final TestingInvocationCountQueueImpl queue = createDemoQueue();
        queue.add(PersistentQueue.TriggerResult.FINISHED_ALL);
        queue.add(PersistentQueue.TriggerResult.NOTHING_TO_DO);
        mockRegistrationService(queue);
        queueTriggeringService.triggerQueuesByTime();
        //expecting 2 invocation
        Assert.assertEquals(queue.getInvocationCount(), 2);
        final TestingInvocationCountQueueImpl queue2 = createDemoQueue();
        queue2.add(PersistentQueue.TriggerResult.FINISHED_ALL);
        queue2.add(PersistentQueue.TriggerResult.NOTHING_TO_DO);
        mockRegistrationService(queue2);
        queueTriggeringService.triggerQueuesByTime();
        //expecting 0 because the time from last invocation is less than an hour
        Assert.assertEquals(queue2.getInvocationCount(), 0);
    }

    public void triggerQueues() {
        //trigger before registration - nothing should happened
        queueTriggeringService.triggerQueues("demo", QueueCursorType.INCREMENTAL_INDEX);
        //register a queue and trigger again
        TestingInvocationCountQueueImpl queue = createDemoQueue();
        queue.add(PersistentQueue.TriggerResult.FINISHED_ALL);
        queue.add(PersistentQueue.TriggerResult.NOTHING_TO_DO);
        mockRegistrationService(queue);
        queueTriggeringService.triggerQueues("demo", QueueCursorType.INCREMENTAL_INDEX);
        Assert.assertEquals(queue.getInvocationCount(), 2);
    }

    private void mockRegistrationService(PersistentQueue queue) {
        final List<PersistentQueue> queues = new ArrayList<>();
        queues.add(queue);
        when(registrationService.getAllQueues()).thenReturn(queues);
        when(registrationService.getQueues(any(), any())).thenReturn(queues);
    }

    @Nonnull
    private TestingInvocationCountQueueImpl createDemoQueue() {
        return new TestingInvocationCountQueueImpl(createIncrementalIndexQueue(), "demo");
    }

    private static class TestingInvocationCountQueueImpl extends PersistentQueueImpl {

        //@Delegate
        private final List<TriggerResult> requestedResult = new ArrayList<>();
        private int invocationCount;

        public TestingInvocationCountQueueImpl(QueueConfiguration configuration, String repoKey) {
            super(configuration, repoKey);
        }

        @Override
        protected TriggerResult innerExecute() {
            return requestedResult.get(invocationCount++);
        }

        public int getInvocationCount() {
            return invocationCount;
        }

        public void add(TriggerResult triggerResult) {
            requestedResult.add(triggerResult);
        }
    }
}
