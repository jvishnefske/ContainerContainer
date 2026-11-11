package org.artifactory.federation;

import org.artifactory.event.queue.QueueEvent;
import org.jfrog.rtfs.dto.properties.NodePath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * @author Shahar Barel
 */
public class DefaultFederatedRepoPropertiesServiceImplTest {

    @Test
    public void testGetProperties() {
        DefaultFederatedRepoPropertiesServiceImpl service = new DefaultFederatedRepoPropertiesServiceImpl();
        assertThrows(UnsupportedOperationException.class, () ->
                service.getProperties(List.of(new NodePath(".", "test.txt")), "test-repo")
        );
    }

    @Test
    public void testGetPropsByRepoKey() {
        DefaultFederatedRepoPropertiesServiceImpl service = new DefaultFederatedRepoPropertiesServiceImpl();
        QueueEvent event = mock(QueueEvent.class);
        assertThrows(UnsupportedOperationException.class, () ->
                service.getPropsByRepoKey(List.of(event))
        );
    }
}
