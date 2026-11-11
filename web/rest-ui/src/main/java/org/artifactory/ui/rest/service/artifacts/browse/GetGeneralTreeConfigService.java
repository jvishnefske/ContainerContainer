package org.artifactory.ui.rest.service.artifacts.browse;

import lombok.RequiredArgsConstructor;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.GeneralConfigModel;
import org.springframework.stereotype.Component;

/**
 * @author Andrey Tashlitsky
 */
@Component
@RequiredArgsConstructor
public class GetGeneralTreeConfigService implements RestService {

    private final CentralConfigService centralConfigService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        boolean trashEnabled = centralConfigService.getDescriptor().getTrashcanConfig().isEnabled();
        boolean treebrowserFolderCompact = ConstantValues.treebrowserFolderCompact.getBoolean();
        GeneralConfigModel generalConfigModel =
                new GeneralConfigModel(trashEnabled, treebrowserFolderCompact);
        response.iModel(generalConfigModel);
    }
}
