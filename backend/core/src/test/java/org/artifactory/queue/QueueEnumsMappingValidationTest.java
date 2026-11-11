package org.artifactory.queue;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueReductionBehaviours;
import org.artifactory.queue.behaviors.QueueReductionBehavioursImpl;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;

@Test
public class QueueEnumsMappingValidationTest {
    @Mock
    private ArtifactoryHome home;
    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        setProperty(ConstantValues.federatedSkipCreateEliminateDeleteOnDeduplication, "false");
        ArtifactoryHome.bind(home);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        autoCloseable.close();
    }
    @Test
    public void testQueueCursorType() {
        for (QueueCursorType queueCursorType : QueueCursorType.values()) {
            NodeEventCursorType nodeEventCursorType = QueueCursorTypeMapper.get(queueCursorType);
            Assert.assertNotNull(nodeEventCursorType);
            Assert.assertTrue(nodeEventCursorType.isEventIdType());
        }
    }

    @Test
    public void testQueueReductionBehaviours() {
        for (QueueReductionBehaviours queueReductionBehaviours : QueueReductionBehaviours.values()) {
            QueueReductionBehavioursImpl queueReductionBehavioursImpl =
                    QueueReductionBehavioursImpl.get(queueReductionBehaviours);
            Assert.assertNotNull(queueReductionBehavioursImpl);
        }
    }

    private void setProperty(ConstantValues property, String value) {
        ArtifactorySystemProperties systemProperties = new ArtifactorySystemProperties();
        when(home.getArtifactoryProperties()).thenReturn(systemProperties);
        systemProperties.setProperty(property.getPropertyName(), value);
    }
}
