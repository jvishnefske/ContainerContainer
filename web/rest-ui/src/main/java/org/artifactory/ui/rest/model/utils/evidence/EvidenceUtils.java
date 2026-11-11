package org.artifactory.ui.rest.model.utils.evidence;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.BaseNode;

import java.util.List;

import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isEvidenceNavigationDisabled;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isEvidencePath;

@UtilityClass
public class EvidenceUtils {

    public static <T extends BaseNode> List<T> removeEvidenceNodes(@NonNull List<T> nodes) {
        if (isEvidenceNavigationDisabled()) {
            return nodes.stream().filter(x -> !isEvidencePath(x.getPath())).toList();
        }
        return nodes;
    }
}