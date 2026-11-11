package org.artifactory.ui.rest.model.utils.bundle;

import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.BaseNode;

import java.util.List;

import static org.artifactory.util.bundles.BundleConfigurationUtil.isHiddenReleaseBundlePath;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundleNavigationDisabled;

@UtilityClass
public class TreeBrowserBundleUtils {

    @Nonnull
    public static <T extends BaseNode> List<T> removeHiddenReleaseBundleNodes(@Nonnull List<T> nodes) {
        if (isReleaseBundleNavigationDisabled()) {
            return nodes.stream().filter(TreeBrowserBundleUtils::isHiddenReleaseBundleNode).toList();
        }
        return nodes;
    }

    private static <T extends BaseNode> boolean isHiddenReleaseBundleNode(T node) {
        return !isHiddenReleaseBundlePath(node.getRepoKey(), node.getPath());
    }
}