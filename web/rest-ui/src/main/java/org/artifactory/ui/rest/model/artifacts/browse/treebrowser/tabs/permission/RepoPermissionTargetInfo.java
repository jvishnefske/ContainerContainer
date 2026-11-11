package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.permission;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.ui.rest.model.common.PermissionTargetInfo;

import java.util.Set;

/**
 * UI model for repo Effective Permission tab
 *
 * @author nadavy
 */
@Getter
@Setter
public class RepoPermissionTargetInfo extends PermissionTargetInfo {

    private Set<String> repoKeys;

    public RepoPermissionTargetInfo() {
    }
}