package org.artifactory.api.rest.retention;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ArtifactRetentionColdRestoreRequest {
    private String operationId;
    private String namespace;
    private List<String> itemsToRestore;
}