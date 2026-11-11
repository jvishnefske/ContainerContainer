package org.artifactory.federation;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * @author Daniel simantov
 * This class displays the depth of the current JPD to other federation mirrors,
 * such as the number of events that need to be sent to the mirror and the number
 * of events that have failed during the process.
 */
@AllArgsConstructor
@Getter
public class FederatedMirrorEventsStatusInfo {
    private String localKey;
    private List<MirrorEventsStatusInfo> mirrorEventsStatusInfo;
}
