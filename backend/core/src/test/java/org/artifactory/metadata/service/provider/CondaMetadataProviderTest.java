package org.artifactory.metadata.service.provider;

import org.artifactory.repo.RepoPathFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author BarakH
 */
class CondaMetadataProviderTest {

    @Test
    void testIsLeadFile() {
        CondaMetadataProvider condaMetadataProvider = new CondaMetadataProvider();
        assertTrue(condaMetadataProvider.isLeadArtifact(RepoPathFactory.create("conda-local", "some/pkg.conda")));
        assertTrue(condaMetadataProvider.isLeadArtifact(RepoPathFactory.create("conda-local", "some/pkg.tar.bz2")));
        assertFalse(condaMetadataProvider.isLeadArtifact(RepoPathFactory.create("conda-local", "some/pkg.null")));
        assertFalse(condaMetadataProvider.isLeadArtifact(RepoPathFactory.create("conda-local", "")));
    }

}