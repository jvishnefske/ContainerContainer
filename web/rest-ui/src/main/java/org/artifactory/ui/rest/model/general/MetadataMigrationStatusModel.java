package org.artifactory.ui.rest.model.general;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Andrey Tashlitsky
 */
@Data
@AllArgsConstructor
public class MetadataMigrationStatusModel {

    private boolean metadataMigrationInProgress;
}
