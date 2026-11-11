package org.artifactory.ui.rest.model.utils.bundle;

import jakarta.annotation.Nonnull;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.BaseNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.artifactory.common.ConstantValues.lifecycleManifestVisible;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TreeBrowserBundleUtilsTest extends ArtifactoryHomeBoundJUnitTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void removeHiddenReleaseBundleNodes(boolean visible) {
        setStringSystemProperty(lifecycleManifestVisible, String.valueOf(visible));
        String bundleManifestPath = "some-RB-name/some-RB-version/release-bundle.json.evd";
        String bundleArtifactPath = "some-RB-name/some-RB-version/artifacts/generic/file.zip";
        BaseNode bundleManifestNode = mockBaseNode(bundleManifestPath);
        BaseNode bundleArtifactNode = mockBaseNode(bundleArtifactPath);
        List<BaseNode> bundleNodes = List.of(bundleManifestNode, bundleArtifactNode);
        List<BaseNode> result = TreeBrowserBundleUtils.removeHiddenReleaseBundleNodes(bundleNodes);
        List<BaseNode> expected = visible ? bundleNodes : List.of(bundleArtifactNode);
        assertEquals(expected, result);
    }

    @Nonnull
    private BaseNode mockBaseNode(@Nonnull String path) {
        BaseNode node = mock(BaseNode.class);
        when(node.getRepoKey()).thenReturn(DEFAULT_RBV2_REPO);
        when(node.getPath()).thenReturn(path);
        return node;
    }
}