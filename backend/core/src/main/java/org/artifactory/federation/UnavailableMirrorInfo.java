package org.artifactory.federation;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public class UnavailableMirrorInfo extends MirrorInfo {
    private final String status;
    @JsonCreator
    public UnavailableMirrorInfo(String localRepoKey, String remoteUrl, String remoteRepoKey, String status) {
        super(localRepoKey, remoteUrl, remoteRepoKey);
        this.status = status;
    }
    public UnavailableMirrorInfo(MirrorInfo mirrorInfo, String status) {
        super(mirrorInfo.getLocalRepoKey(), mirrorInfo.getRemoteUrl(), mirrorInfo.getRemoteRepoKey());
        this.status = status;
    }
}
