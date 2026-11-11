package org.artifactory.mime.version.converter;

import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MimeTypeConverterIsInterestedTest extends ArtifactoryHomeBoundJUnitTest {

    private MimeTypeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new MimeTypeConverter();
    }

    @Test
    void isInterested_shouldReturnFalse_whenSourceIsNull() throws IOException {
        CompoundVersionDetails target = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v7800.get(), null, 0L);

        assertFalse(converter.isInterested(homeStub, null, target));
    }

    @Test
    void isInterested_shouldReturnFalse_whenRollback() throws IOException {
        CompoundVersionDetails source = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v7800.get(), null, 0L);
        CompoundVersionDetails target = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v680.get(), null, 0L);

        assertFalse(converter.isInterested(homeStub, source, target));
    }

    @Test
    void isInterested_shouldReturnFalse_whenSameVersion() throws IOException {
        CompoundVersionDetails source = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v730.get(), null, 0L);
        CompoundVersionDetails target = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v730.get(), null, 0L);

        assertFalse(converter.isInterested(homeStub, source, target));
    }
}
