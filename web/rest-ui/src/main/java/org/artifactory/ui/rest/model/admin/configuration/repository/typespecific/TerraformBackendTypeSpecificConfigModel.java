package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.repo.RepoType;

/**
 * @author asafz
 */
@Getter
@Setter
public class TerraformBackendTypeSpecificConfigModel extends TypeSpecificConfigModelBase {

    private boolean encryptStates = true;

    @Override
    public RepoType getRepoType() {
        return RepoType.TerraformBackend;
    }
}

