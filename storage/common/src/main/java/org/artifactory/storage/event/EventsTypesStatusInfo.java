package org.artifactory.storage.event;

import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class EventsTypesStatusInfo {
    private String operatorId;
    private String status;
    private long createEvents = 0;
    private long updateEvents = 0;
    private long deleteEvents = 0;
    private long propsEvents = 0;
    private long lagInMS = 0;
    private long eventRegistrationTimeStamp = 0;


    public EventsTypesStatusInfo(String operatorId, String status,
            long createEvents, long updateEvents, long deleteEvents, long propsEvents, long lagInMS,
            long eventRegistrationTimeStamp) {
        this.operatorId = operatorId;
        this.status = status;
        this.createEvents = createEvents;
        this.updateEvents = updateEvents;
        this.deleteEvents = deleteEvents;
        this.propsEvents = propsEvents;
        this.lagInMS = lagInMS;
        this.eventRegistrationTimeStamp = eventRegistrationTimeStamp;
    }
}
