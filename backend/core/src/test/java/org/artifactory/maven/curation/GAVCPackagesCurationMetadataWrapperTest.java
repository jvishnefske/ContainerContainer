package org.artifactory.maven.curation;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GAVCPackagesCurationMetadataWrapperTest {

    @Test
    void getMetadata() {
        Metadata metadata = new Metadata();
        metadata.setGroupId("group");
        metadata.setArtifactId("artifact");
        Metadata metadataToCheck = new GAVCPackagesCurationMetadataWrapper(metadata).getMetadata();
        assertEquals("group", metadataToCheck.getGroupId());
        assertEquals("artifact", metadataToCheck.getArtifactId());
    }
}