package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.api.repo.RootNodesFilterResult;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.jfrog.commons.continues.ContinueIndexPage;

/**
 * @author Hussein Alshqerat
 */
@Builder
@Data
@EqualsAndHashCode(callSuper = false)
public class NativeNodeFilter extends ContinueIndexPage {
    RepoPath repoPath;
    Properties requestProps;
    boolean updateRootNodesFilterFlag;
    boolean includeChecksums;
    RootNodesFilterResult rootNodesFilterResult;
}