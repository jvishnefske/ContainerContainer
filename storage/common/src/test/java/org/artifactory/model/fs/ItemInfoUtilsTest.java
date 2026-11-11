package org.artifactory.model.fs;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * @author Oleksandr Legenkiy
 */
class ItemInfoUtilsTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String UPDATE_TIMESTAMP_SYS_PROP_KEY = "artifactory.sys.update.file.created.timestamp";

    @Test
    void testConstantValuesRetrievedSuccessfully() {
        homeStub.setProperty(ConstantValues.updateFileCreatedTimestampOnCopyOrMove.getPropertyName(), "true");
        assertTrue(ItemInfoUtils.isUpdateCreatedTimestampOnCopyOrMoveEnabled());
        homeStub.setProperty(ConstantValues.updateFileCreatedTimestampOnCopyOrMove.getPropertyName(), "false");
        assertFalse(ItemInfoUtils.isUpdateCreatedTimestampOnCopyOrMoveEnabled());
    }

    @Test
    void testConstantValuesNotBound() {
        try (MockedStatic<ArtifactoryHome> artifactoryHomeMockedStatic = mockStatic(ArtifactoryHome.class)) {
            artifactoryHomeMockedStatic.when(ArtifactoryHome::get).thenThrow(new RuntimeException("TEST"));
            assertFalse(ItemInfoUtils.isUpdateCreatedTimestampOnCopyOrMoveEnabled());
            try {
                System.setProperty(UPDATE_TIMESTAMP_SYS_PROP_KEY, "true");
                assertTrue(ItemInfoUtils.isUpdateCreatedTimestampOnCopyOrMoveEnabled());
                System.setProperty(UPDATE_TIMESTAMP_SYS_PROP_KEY, "false");
                assertFalse(ItemInfoUtils.isUpdateCreatedTimestampOnCopyOrMoveEnabled());
            } finally {
                System.clearProperty(UPDATE_TIMESTAMP_SYS_PROP_KEY);
            }
        }
    }
}