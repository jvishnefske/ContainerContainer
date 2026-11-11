package org.artifactory.storage.db.event.entity;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EventsDaoParams {
    private long eventId;
    private boolean inclusive;
    private int limit;
    private String repoKey;
    private String suffix;
    @Builder.Default
    private Boolean afterRepoColumnMigration = true; //  For lower versions before 7.50, using less efficient SQL
}
