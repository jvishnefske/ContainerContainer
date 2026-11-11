package org.artifactory.repo;

import org.artifactory.repo.replication.RemoteReplicationJob;
import org.artifactory.repo.replication.RemoteReplicationJobUnexclusive;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.artifactsCleanupJobRemoteReplicationJobMutuallyUnexclusive;
import static org.artifactory.repo.RemoteReplicationJobSelector.getRemoteReplicationJobClass;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Dvir Twina
 */
class RemoteReplicationJobSelectorTest extends ArtifactoryHomeBoundJUnitTest {

    @AfterEach
    void afterEach() {
        homeStub.setProperty(artifactsCleanupJobRemoteReplicationJobMutuallyUnexclusive,
                artifactsCleanupJobRemoteReplicationJobMutuallyUnexclusive.getDefValue());
    }

    public static Stream<Arguments> testRemoteReplicationJobSelectionSource() {
        return Stream.of(
                Arguments.of("false", RemoteReplicationJob.class),
                Arguments.of("true", RemoteReplicationJobUnexclusive.class)
        );
    }

    @ParameterizedTest
    @MethodSource("testRemoteReplicationJobSelectionSource")
    void testRemoteReplicationJobSelection(String flagValue, Class<? extends RemoteReplicationJob> expectedClass) {
        homeStub.setProperty(artifactsCleanupJobRemoteReplicationJobMutuallyUnexclusive, flagValue);
        Class<? extends RemoteReplicationJob> remoteReplicationJobClass = getRemoteReplicationJobClass();
        assertEquals(expectedClass, remoteReplicationJobClass,
                "Expected " + expectedClass + " class when mutually exclusive is " + flagValue);
    }
}