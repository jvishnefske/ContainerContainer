package org.artifactory.ui.rest.model.admin.configuration.projects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UnassignedResourceStats {

    private String resourceType;
    private int totalCount;
    private int unassignedCount;
    private Integer unassignedUniqueCount = null; // optional field
}
