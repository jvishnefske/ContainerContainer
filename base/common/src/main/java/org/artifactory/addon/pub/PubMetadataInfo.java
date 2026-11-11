package org.artifactory.addon.pub;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * @author Shahaf Golan
 */
@Getter
@AllArgsConstructor
public class PubMetadataInfo {
    private final PubInfo info;
    private final List<PubDependencyMetadataInfo> dependencies;
    private final List<PubDependencyMetadataInfo> devDependencies;
    private final List<PubEnvironmentMetadataInfo> environment;
}