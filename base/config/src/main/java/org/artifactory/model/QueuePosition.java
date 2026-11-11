package org.artifactory.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @author Alexei Vainshtein
 */
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Data
public class QueuePosition extends QueueInfo {

    private Long eventId;

    public QueuePosition(String remoteUrl, String remoteRepoKey, String repoKey, Long eventId) {
        super(remoteUrl, remoteRepoKey, repoKey);
        this.eventId = eventId;
    }
}
