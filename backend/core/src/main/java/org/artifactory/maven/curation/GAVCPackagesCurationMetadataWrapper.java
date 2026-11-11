package org.artifactory.maven.curation;

import lombok.NonNull;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.artifactory.ph.curation.PackagesCurationMetadataWrapper;

public class GAVCPackagesCurationMetadataWrapper extends PackagesCurationMetadataWrapper {
    public GAVCPackagesCurationMetadataWrapper(@NonNull Metadata metadata) {
        super(metadata);
    }

    @Override
    public @NonNull Metadata getMetadata() {
        return (Metadata) super.getMetadata();
    }
}
