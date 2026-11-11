package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.repo.RepoType;

import java.util.List;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_SWIFT_EXTERNAL_DEPENDENCIES_PATTERNS;

/**
 * @author Michael Akushsky
 * @author Natalie Kirillov
 */
@Getter
@Setter
public class SwiftTypeSpecificConfigModel extends TypeSpecificConfigModelBase {
    private Boolean enableExternalDependencies = false;
    protected Boolean listRemoteFolderItems = DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE;
    private List<String> externalPatterns = DEFAULT_SWIFT_EXTERNAL_DEPENDENCIES_PATTERNS;

    @Override
    public RepoType getRepoType() {
        return RepoType.Swift;
    }
}
