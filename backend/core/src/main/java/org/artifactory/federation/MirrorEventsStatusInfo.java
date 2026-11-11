package org.artifactory.federation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.artifactory.storage.event.EventsTypesStatusInfo;

@Data
@Getter
@AllArgsConstructor
public class MirrorEventsStatusInfo {

    private String remoteUrl;
    private String remoteRepoKey;
    private String status;
    private long createEvents = 0;
    private long updateEvents = 0;
    private long deleteEvents = 0;
    private long propsEvents = 0;
    private long errorEvents = 0;
    private long lagInMS = 0;
    private long eventRegistrationTimeStamp = 0;

    public MirrorEventsStatusInfo(String remoteUrl, String remoteRepoKey,
            EventsTypesStatusInfo eventsTypesStatusInfo, long errorEvents, String status) {
        this.remoteUrl = remoteUrl;
        this.remoteRepoKey = remoteRepoKey;
        this.status = status;
        this.createEvents = eventsTypesStatusInfo.getCreateEvents();
        this.updateEvents = eventsTypesStatusInfo.getUpdateEvents();
        this.deleteEvents = eventsTypesStatusInfo.getDeleteEvents();
        this.propsEvents = eventsTypesStatusInfo.getPropsEvents();
        this.errorEvents = errorEvents;
        this.lagInMS = eventsTypesStatusInfo.getLagInMS();
        this.eventRegistrationTimeStamp = eventsTypesStatusInfo.getEventRegistrationTimeStamp();
    }
}
