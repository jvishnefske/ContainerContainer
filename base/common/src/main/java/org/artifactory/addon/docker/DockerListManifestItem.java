package org.artifactory.addon.docker;

import lombok.Getter;
import lombok.Setter;

/**
 * UI model for list.manifest.json items.
 *
 * @author BarakH
 */
@Getter
@Setter
public class DockerListManifestItem {

    private String os;
    private String arch;
    private String variant;
    private String digest;
    private long manifestSize;
}
