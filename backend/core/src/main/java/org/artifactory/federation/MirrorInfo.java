package org.artifactory.federation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MirrorInfo {
    protected String localRepoKey;
    protected String remoteUrl;
    protected String remoteRepoKey;
}
