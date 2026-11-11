package org.artifactory.repo;

import org.artifactory.repo.cleanup.ArtifactCleanupJob;
import org.artifactory.repo.cleanup.ArtifactCleanupJobUnexclusive;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.artifactsCleanupJobRemoteReplicationJobMutuallyUnexclusive;
import static org.artifactory.repo.ArtifactCleanupJobSelector.getArtifactCleanupJobClass;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Dvir Twina
 */
class ArtifactCleanupJobSelectorTest extends ArtifactoryHomeBoundJUnitTest {

    @AfterEach
    void afterEach() {
        homeStub.setProperty(artifactsCleanupJobRemoteReplicationJobMutuallyUnexclusive,
                artifactsCleanupJobRemoteReplicationJobMutuallyUnexclusive.getDefValue());
    }

    public static Stream<Arguments> testArtifactCleanupJobSelectionSource() {
        return Stream.of(
                Arguments.of(false, ArtifactCleanupJob.class),
                Arguments.of(true, ArtifactCleanupJobUnexclusive.class)
        );
    }

    @ParameterizedTest
    @MethodSource("testArtifactCleanupJobSelectionSource")
    void testArtifactCleanupJobSelection(boolean flagValue, Class<? extends ArtifactCleanupJob> expectedClass) {
        homeStub.setProperty(artifactsCleanupJobRemoteReplicationJobMutuallyUnexclusive, String.valueOf(flagValue));
        Class<? extends ArtifactCleanupJob> artifactCleanupJobClass = getArtifactCleanupJobClass();
        assertEquals(expectedClass, artifactCleanupJobClass,
                "Expected " + expectedClass + " class when mutually exclusive is " + flagValue);
    }
}