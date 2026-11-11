package org.artifactory.storage.event;

import lombok.Data;
import lombok.Getter;

@Data
@Getter
public class EventErrorStatusInfo {
    private String cursor;
    private long countEventErrors;


    public EventErrorStatusInfo(String cursor, long countEventErrors) {
        this.cursor = cursor;
        this.countEventErrors = countEventErrors;
    }

}
