package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.util.JsonUtil;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_NIMMODEL_REGISTRY_URL;

/**
 * @author Einas Ashkar
 */

@Getter
@Setter
public class NimModelTypeSpecificConfigModel extends TypeSpecificConfigModelBase {
    private Boolean listRemoteFolderItems = false;
    private String url = DEFAULT_NIMMODEL_REGISTRY_URL;

    @Override
    public RepoType getRepoType() {
        return RepoType.NimModel;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}

