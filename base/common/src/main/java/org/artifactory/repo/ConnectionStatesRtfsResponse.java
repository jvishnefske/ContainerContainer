package org.artifactory.repo;

import java.util.List;


/**
 * This record represents the connection states result from the Rtfs service
 *
 * @author Shahar Palmor
 */

public record ConnectionStatesRtfsResponse(List<MemberConnection> memberConnections) {
}

