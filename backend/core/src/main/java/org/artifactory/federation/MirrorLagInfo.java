package org.artifactory.federation;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@NoArgsConstructor
public class MirrorLagInfo extends MirrorInfo {
    private long lagInMS;
    private long eventRegistrationTimeStamp;

    public MirrorLagInfo(MirrorInfo mirrorInfo, long lagInMS, long eventRegistrationTimeStamp) {
        super(mirrorInfo.getLocalRepoKey(), mirrorInfo.getRemoteUrl(), mirrorInfo.getRemoteRepoKey());
        this.lagInMS = lagInMS;
        this.eventRegistrationTimeStamp = eventRegistrationTimeStamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        if (!super.equals(o)) {return false;}
        MirrorLagInfo that = (MirrorLagInfo) o;
        return lagInMS == that.lagInMS && eventRegistrationTimeStamp == that.eventRegistrationTimeStamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), lagInMS, eventRegistrationTimeStamp);
    }
}
