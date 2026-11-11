package org.artifactory.rest.resource.federation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.federation.GridTopologyChangeType;
import org.artifactory.model.GridTopology;

/**
 * DTO for grid topology change events from external Grid service.
 * This represents a notification that a grid topology has been created, updated, or deleted.
 *
 * @author Tomer Ben Kereth
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GridTopologyChangeEvent {

    /**
     * The type of change.
     */
    private GridTopologyChangeType changeType;

    /**
     * The grid topology ID that was changed.
     * Required for all event types.
     */
    private String gridTopologyId;

    /**
     * The full grid topology data.
     * Required for CREATED and UPDATED events.
     * Null for DELETED events.
     */
    private GridTopology gridTopology;
}

