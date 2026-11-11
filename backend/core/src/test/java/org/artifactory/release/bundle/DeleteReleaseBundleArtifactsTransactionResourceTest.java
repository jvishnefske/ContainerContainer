package org.artifactory.release.bundle;

import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class DeleteReleaseBundleArtifactsTransactionResourceTest {

    @Mock
    private InternalReleaseBundleService service;

    private DeleteReleaseBundleArtifactsTransactionResource resource;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        resource = new DeleteReleaseBundleArtifactsTransactionResource();
        resource.init(service, new ArtifactsBundleModel(), List.of(), Set.of());
    }

    @Test
    public void afterConnectionReleased_shouldUndeployIfNeeded_whenCommittedTrue() {
        resource.init(service, new ArtifactsBundleModel(), List.of(), Set.of());
        resource.afterConnectionReleased(true);
        verify(service, times(1)).undeployIfNeeded(any(), any(), any());
    }

    @Test
    public void afterConnectionReleased_shouldNotUndeployIfNeeded_whenCommittedFalse() {
        resource.init(service, new ArtifactsBundleModel(), List.of(), Set.of());
        resource.afterConnectionReleased(false);
        verify(service, times(0)).undeployIfNeeded(any(), any(), any());
    }

    @Test
    public void init_shouldInitializeFieldsCorrectly() {
        ArtifactsBundleModel bundleModel = new ArtifactsBundleModel();
        List<BundleNode> bundleNodes = List.of(new BundleNode());
        Set<Long> nodeIdsWithArtifactsToDelete = Set.of(1L);

        resource.init(service, bundleModel, bundleNodes, nodeIdsWithArtifactsToDelete);

        assertEquals(service, resource.getInternalReleaseBundleService());
        assertEquals(bundleModel, resource.getArtifactsBundle());
        assertEquals(bundleNodes, resource.getBundleNodes());
        assertEquals(nodeIdsWithArtifactsToDelete, resource.getNodeIdsWithArtifactsToDelete());
    }
}