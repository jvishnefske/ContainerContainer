package org.artifactory.ph.curation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * @author Ron Malka
 */

@Getter
@AllArgsConstructor
public class PackagesCurationMetadataWrapper {
    @NonNull
    Object metadata;
}
