package org.artifactory.queue.register;

import org.artifactory.event.queue.QueueConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalQueueConfigurationRegistrarTest {

    @Test
    void testShouldRegisterDefault() {
        GlobalQueueConfigurationRegistrar registrar = new GlobalQueueConfigurationRegistrar() {
            @Override
            public QueueConfiguration getQueueConfiguration() {
                return null; // Not used in this test
            }

            @Override
            public String getQueueName() {
                return "testQueue";
            }
        };
        assertTrue(registrar.shouldRegister(), "Default shouldRegister should return true");
    }
}