package org.artifactory.model;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for {@link FederatedRepoConfig} convenience methods and copy functionality.
 */
class FederatedRepoConfigTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void getFederatedMembers_shouldReturnFromNewLocation_whenNewLocationHasMembers() {
        // Arrange
        FederatedRepoConfig config = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        FederatedMember member = createMember("http://site1.example.com/test-repo");
        config.getBaseConfig().getFederationConfig().setMembers(List.of(member));
        config.getRepoTypeConfig().setFederatedMembers(new ArrayList<>());

        // Act
        List<FederatedMember> result = config.getFederatedMembers();

        // Assert
        assertEquals(1, result.size());
        assertEquals("http://site1.example.com/test-repo", result.get(0).getUrlOrTemplate());
        assertSame(config.getBaseConfig().getFederationConfig().getMembers(), result);
    }

    @Test
    void getFederatedMembers_shouldFallbackToOldLocation_whenNewLocationIsEmpty() {
        // Arrange
        FederatedRepoConfig config = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        FederatedMember member = createMember("http://site2.example.com/test-repo");
        config.getBaseConfig().getFederationConfig().setMembers(new ArrayList<>());
        config.getRepoTypeConfig().setFederatedMembers(List.of(member));

        // Act
        List<FederatedMember> result = config.getFederatedMembers();

        // Assert
        assertEquals(1, result.size());
        assertEquals("http://site2.example.com/test-repo", result.get(0).getUrlOrTemplate());
        assertSame(config.getRepoTypeConfig().getFederatedMembers(), result);
    }

    @Test
    void getFederatedMembers_shouldFallbackToOldLocation_whenNewLocationIsNull() {
        // Arrange
        FederatedRepoConfig config = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        FederatedMember member = createMember("http://site3.example.com/test-repo");
        config.getBaseConfig().getFederationConfig().setMembers(null);
        config.getRepoTypeConfig().setFederatedMembers(List.of(member));

        // Act
        List<FederatedMember> result = config.getFederatedMembers();

        // Assert
        assertEquals(1, result.size());
        assertEquals("http://site3.example.com/test-repo", result.get(0).getUrlOrTemplate());
    }

    @Test
    void getModificationDate_shouldReturnFromNewLocation_whenNewLocationHasPositiveValue() {
        // Arrange
        FederatedRepoConfig config = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        config.getBaseConfig().getFederationConfig().setModificationDate(12345L);
        config.getRepoTypeConfig().setModificationDate(99999L);

        // Act
        long result = config.getModificationDate();

        // Assert
        assertEquals(12345L, result);
    }

    @Test
    void getModificationDate_shouldFallbackToOldLocation_whenNewLocationIsZero() {
        // Arrange
        FederatedRepoConfig config = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        config.getBaseConfig().getFederationConfig().setModificationDate(0L);
        config.getRepoTypeConfig().setModificationDate(67890L);

        // Act
        long result = config.getModificationDate();

        // Assert
        assertEquals(67890L, result);
    }

    @Test
    void setFederatedMembers_shouldWriteToBothLocations() {
        // Arrange
        FederatedRepoConfig config = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        FederatedMember member = createMember("http://site.example.com/test-repo");
        List<FederatedMember> members = List.of(member);

        // Act
        config.setFederatedMembers(members);

        // Assert
        assertEquals(1, config.getBaseConfig().getFederationConfig().getMembers().size());
        assertEquals(1, config.getRepoTypeConfig().getFederatedMembers().size());
    }

    @Test
    void setModificationDate_shouldWriteToBothLocations() {
        // Arrange
        FederatedRepoConfig config = FederatedRepoConfig.create("test-repo", RepoType.Generic);

        // Act
        config.setModificationDate(54321L);

        // Assert
        assertEquals(54321L, config.getBaseConfig().getFederationConfig().getModificationDate());
        assertEquals(54321L, config.getRepoTypeConfig().getModificationDate());
    }

    @Test
    void copy_shouldCreateIndependentCopyOfFederatedMembers() {
        // Arrange
        FederatedRepoConfig original = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        FederatedMember member = createMember("http://original.example.com/test-repo");
        original.setFederatedMembers(List.of(member));

        // Act
        FederatedRepoConfig copy = RepoConfigCopyFactory.copy(original);

        // Assert
        assertNotNull(copy);
        assertEquals(1, copy.getFederatedMembers().size());
        assertEquals("http://original.example.com/test-repo",
                copy.getFederatedMembers().get(0).getUrlOrTemplate());
        assertNotSame(original.getFederatedMembers(), copy.getFederatedMembers());
        assertNotSame(original.getRepoTypeConfig().getFederatedMembers(),
                copy.getRepoTypeConfig().getFederatedMembers());
    }

    @Test
    void copy_shouldNotAffectOriginal_whenCopyIsModified() {
        // Arrange
        FederatedRepoConfig original = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        FederatedMember member = createMember("http://original.example.com/test-repo");
        original.setFederatedMembers(new ArrayList<>(List.of(member)));

        // Act
        FederatedRepoConfig copy = RepoConfigCopyFactory.copy(original);
        FederatedMember newMember = createMember("http://new.example.com/test-repo");
        copy.getRepoTypeConfig().getFederatedMembers().add(newMember);

        // Assert
        assertEquals(1, original.getFederatedMembers().size());
        assertEquals(2, copy.getFederatedMembers().size());
    }

    @Test
    void copy_shouldPreserveModificationDate() {
        // Arrange
        FederatedRepoConfig original = FederatedRepoConfig.create("test-repo", RepoType.Generic);
        original.setModificationDate(123456789L);

        // Act
        FederatedRepoConfig copy = RepoConfigCopyFactory.copy(original);

        // Assert
        assertEquals(123456789L, copy.getModificationDate());
    }

    private FederatedMember createMember(String url) {
        FederatedMember member = new FederatedMember();
        member.setUrl(url);
        member.setMode("bidirectional");
        member.setEnabled(true);
        return member;
    }
}
