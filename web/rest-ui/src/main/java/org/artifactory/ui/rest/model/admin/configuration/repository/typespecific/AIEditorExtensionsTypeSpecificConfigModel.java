package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.util.JsonUtil;

import java.util.List;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_AI_EDITOR_EXTENSIONS_EXTERNAL_DEPENDENCIES_PATTERNS;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_VSCODE_REGISTRY_URL;

/**
 * @author Omri Cohen
 */
@Getter
@Setter
public class AIEditorExtensionsTypeSpecificConfigModel implements TypeSpecificConfigModel {
    private String url = DEFAULT_VSCODE_REGISTRY_URL;
    private Boolean enableExternalDependencies = true;
    private List<String> externalPatterns = Lists.newArrayList(
            DEFAULT_AI_EDITOR_EXTENSIONS_EXTERNAL_DEPENDENCIES_PATTERNS);
    private String externalRemoteRepo = "";
    private boolean listRemoteFolderItems = DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE;

    @Override
    public RepoType getRepoType() {
        return RepoType.AIEditorExtensions;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
