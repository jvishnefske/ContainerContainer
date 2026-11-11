package org.artifactory.ui.rest.model.artifacts.browse.treebrowser;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Andrey Tashlitsky
 */
@Data
@AllArgsConstructor
public class GeneralConfigModel {

    private boolean trashEnabled;
    private boolean treebrowserFolderCompact;
}
