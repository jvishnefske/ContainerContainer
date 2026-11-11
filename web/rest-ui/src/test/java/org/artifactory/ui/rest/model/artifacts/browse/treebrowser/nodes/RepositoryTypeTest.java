package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.testng.annotations.Test;

import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType.byConfig;
import static org.testng.Assert.assertEquals;

/**
 * @author Alexei Vainshtein
 */
public class RepositoryTypeTest {

    @Test
    public void testByDescriptor() {
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        assertEquals(byConfig(localRepoDescriptor), RepositoryType.LOCAL);

        FederatedRepoDescriptor federatedRepoDescriptor = new FederatedRepoDescriptor();
        assertEquals(byConfig(federatedRepoDescriptor), RepositoryType.FEDERATED);
    }
}