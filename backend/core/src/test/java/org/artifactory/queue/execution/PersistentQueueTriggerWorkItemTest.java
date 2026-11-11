package org.artifactory.queue.execution;

import org.artifactory.queue.services.PersistentQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistentQueueTriggerWorkItemTest {
    private static final String QUEUE_NAME = "testQueue";

    @Mock
    private PersistentQueue mockQueue;

    private PersistentQueueTriggerWorkItem workItem;

    @BeforeEach
    void setUp() {
        lenient().when(mockQueue.getName()).thenReturn(QUEUE_NAME);
        workItem = new PersistentQueueTriggerWorkItem(mockQueue);
    }

    @Test
    void getQueueReturnsCorrectQueue() {
        assertEquals(mockQueue, workItem.getQueue());
    }

    @Test
    void getUniqueKeyReturnsQueueName() {
        assertEquals(QUEUE_NAME, workItem.getUniqueKey());
    }

    @Test
    void equalsReturnsFalseForNull() {
        assertNotEquals(null, workItem);
    }

    @Test
    void equalsReturnsFalseForDifferentClass() {
        assertNotEquals(workItem, new Object());
    }

    @Test
    void equalsReturnsTrueForSameQueueName() {
        PersistentQueueTriggerWorkItem anotherWorkItem = new PersistentQueueTriggerWorkItem(mockQueue);
        assertEquals(workItem, anotherWorkItem);
    }

    @Test
    void equalsReturnsFalseForDifferentQueueName() {
        PersistentQueue mockQueue2 = mock(PersistentQueue.class);
        when(mockQueue2.getName()).thenReturn("anotherQueue");
        PersistentQueueTriggerWorkItem anotherWorkItem = new PersistentQueueTriggerWorkItem(mockQueue2);
        assertNotEquals(workItem, anotherWorkItem);
    }

    @Test
    void hashCodeIsConsistent() {
        int initialHashCode = workItem.hashCode();
        assertEquals(initialHashCode, workItem.hashCode());
    }

    @Test
    void toStringReturnsCorrectFormat() {
        assertEquals("PersistentQueueTriggerWorkItem{" + QUEUE_NAME + "}", workItem.toString());
    }
}