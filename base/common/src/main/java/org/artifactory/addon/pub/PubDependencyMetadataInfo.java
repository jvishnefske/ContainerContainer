package org.artifactory.addon.pub;

import lombok.Data;

/**
 * @author Shahaf Golan
 */
@Data
public class PubDependencyMetadataInfo {
    private final String name;
    private final String version;
}