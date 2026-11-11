package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.util.JsonUtil;


/**
 * @author shahafg
 */
@Getter
@Setter
public class HuggingFaceMLTypeSpecificConfigModel extends TypeSpecificConfigModelBase {

    private Boolean listRemoteFolderItems = false;
    private boolean mlRepoLayout = false;

    @Override
    public RepoType getRepoType() {
        return RepoType.HuggingFaceML;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
