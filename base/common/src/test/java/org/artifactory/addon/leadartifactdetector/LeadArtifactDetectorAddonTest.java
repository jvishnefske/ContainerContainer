package org.artifactory.addon.leadartifactdetector;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.spy;
import static org.testng.AssertJUnit.assertFalse;

/**
 * @author Natalie Berni
 */
class LeadArtifactDetectorAddonTest {

    private LeadArtifactDetectorAddon leadArtifactDetectorAddon;

    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        leadArtifactDetectorAddon = spy(LeadArtifactDetectorAddon.class);
        autoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void isLeadArtifact() {
        RepoPath repoPath = RepoPathFactory.create(RepoType.Npm.getType(), "npm.zip");
        assertFalse(leadArtifactDetectorAddon.isLeadArtifact(RepoType.Npm, repoPath));
    }

    @Test
    void isPackageConsumptionLead() {
        RepoPath repoPath = RepoPathFactory.create(RepoType.Npm.getType(), "npm.zip");
        assertFalse(leadArtifactDetectorAddon.isPackageConsumptionLead(RepoType.Npm, repoPath));
    }
}