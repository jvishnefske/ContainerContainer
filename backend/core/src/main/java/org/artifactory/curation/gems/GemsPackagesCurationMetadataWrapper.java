package org.artifactory.curation.gems;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.artifactory.ph.curation.PackagesCurationMetadataWrapper;

import java.util.List;

/**
 * Wrapper for Gems compact index metadata.
 * Holds compact index lines (excluding the header) as the metadata payload.
 */
public class GemsPackagesCurationMetadataWrapper extends PackagesCurationMetadataWrapper {

    @Getter
    @Setter
    private String packageName;

    public GemsPackagesCurationMetadataWrapper(@NonNull List<String> metadataLines) {
        super(metadataLines);
    }

    @SuppressWarnings("unchecked")
    public @NonNull List<String> getMetadataLines() {
        return (List<String>) super.getMetadata();
    }
}
