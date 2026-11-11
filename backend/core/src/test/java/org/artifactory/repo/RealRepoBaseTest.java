package org.artifactory.repo;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.exception.IncludeExcludeException;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.resource.UnfoundRepoResourceReason;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.GlobalExcludes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.artifactory.descriptor.repo.SnapshotVersionBehaviorDescriptor.UNIQUE;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
public class RealRepoBaseTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private InternalArtifactoryContext contextMock;
    @Mock
    private InternalRepositoryService internalRepoService;
    @Mock
    private CentralConfigService centralConfigService;

    private AutoCloseable mocks;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(contextMock);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testGlobalExcludeForRealRepo() {
        LocalRepoDescriptor repoDescriptor = new LocalRepoDescriptor();
        repoDescriptor.setKey("repoKey");
        repoDescriptor.setSnapshotVersionBehavior(UNIQUE);
        DbLocalRepo dbLocalRepo =
                new DbLocalRepo<>(repoDescriptor, repoDescriptor.toRepositoryConfig(), internalRepoService, null,
                        centralConfigService);
        Assertions.assertEquals(GlobalExcludes.getGlobalExcludes().size(), dbLocalRepo.excludes.size());
    }

    @Test
    void testCreateUnfoundRepoResourceWithIncludeExcludeException() {
        StatusHolder statusHolder = Mockito.mock(StatusHolder.class);
        when(statusHolder.isError()).thenReturn(true);
        IncludeExcludeException includeExcludeException = new IncludeExcludeException(404, null, null);
        when(statusHolder.getException()).thenReturn(includeExcludeException);
        when(statusHolder.getStatusMsg()).thenReturn("statusMsg");
        when(statusHolder.getStatusCode()).thenReturn(404);

        UnfoundRepoResource unfoundRepoResource =
                RealRepoBase.createUnfoundRepoResource(new RepoPathImpl("repoKey", "path"), statusHolder);
        Assertions.assertEquals(404, unfoundRepoResource.getStatusCode());
        Assertions.assertEquals("statusMsg", unfoundRepoResource.getDetail());
        Assertions.assertEquals(UnfoundRepoResourceReason.Reason.INCLUDE_EXCLUDE_PATTERN,
                                unfoundRepoResource.getReason());
    }

    @Test
    void testCreateUnfoundRepoResource() {
        StatusHolder statusHolder = Mockito.mock(StatusHolder.class);
        when(statusHolder.isError()).thenReturn(true);
        NotFoundException notFoundException = new NotFoundException("not found");
        when(statusHolder.getException()).thenReturn(notFoundException);
        when(statusHolder.getStatusMsg()).thenReturn("statusMsg");
        when(statusHolder.getStatusCode()).thenReturn(404);

        UnfoundRepoResource unfoundRepoResource =
                RealRepoBase.createUnfoundRepoResource(new RepoPathImpl("repoKey", "path"), statusHolder);
        Assertions.assertEquals(404, unfoundRepoResource.getStatusCode());
        Assertions.assertEquals("statusMsg", unfoundRepoResource.getDetail());
        Assertions.assertEquals(UnfoundRepoResourceReason.Reason.UNDEFINED, unfoundRepoResource.getReason());
    }

    @Test
    void testCreatePatternReturnsNullForNullOrEmptyExcludes() {
        DbLocalRepo<?, ?> repo = createTestRepo();

        homeStub.setProperty(ConstantValues.doubleStarOptimizationForExcludes, "false");
        Assertions.assertNull(repo.createPattern(null));
        Assertions.assertNull(repo.createPattern(Collections.emptyList()));

        homeStub.setProperty(ConstantValues.doubleStarOptimizationForExcludes, "true");
        Assertions.assertNull(repo.createPattern(null));
        Assertions.assertNull(repo.createPattern(Collections.emptyList()));
    }

    @Test
    void testOptimizedAndStandardPatternsAreEquivalent_defaultGlobalExcludes() {
        List<String> excludes = List.of(
                "**/*~", "**/#*#", "**/.#*", "**/%*%", "**/._*",
                "**/CVS", "**/CVS/**", "**/.cvsignore",
                "**/SCCS", "**/SCCS/**",
                "**/vssver.scc", "**/.svn", "**/.svn/**", "**/.DS_Store");

        List<String> matching = List.of(
                "amir~", "repo/amir~",
                "CVS", "folder/CVS", ".DS_Store", "folder/.DS_Store",
                ".svn", "folder/.svn", "folder/.svn/entries",
                "SCCS", "deep/SCCS/file.txt");
        List<String> nonMatching = List.of(
                "repo/amir", "some/deep/folder/file.jar",
                "a/b/c.txt", "folder/SCCS-backup/file.txt");

        assertBothModesEquivalent(excludes, matching, nonMatching);
    }

    @Test
    void testOptimizedAndStandardPatternsAreEquivalent_mixedExcludes() {
        // Mix of **/ patterns and non-**/ patterns — exercises the two-bucket split in createOptimizedPattern
        List<String> excludes = List.of("**/CVS/**", "org/apache/**", "**/secret.txt", "internal/private");

        List<String> matching = List.of(
                "CVS/file", "deep/CVS/file",
                "org/apache/commons/lang.jar",
                "secret.txt", "deep/secret.txt",
                "internal/private");
        List<String> nonMatching = List.of(
                "org/jfrog/file.jar", "not-internal/private", "CVS-backup/file");

        assertBothModesEquivalent(excludes, matching, nonMatching);
    }

    @Test
    void testOptimizedAndStandardPatternsAreEquivalent_largeBlocklist() {
        // 200 synthetic blocklist entries starting with **/, mimicking a compromised-package blocklist
        List<String> excludes = new ArrayList<>(200);
        for (int i = 0; i < 200; i++) {
            excludes.add("**/blocked-pkg-" + i + "-1.0." + (i % 10) + ".tgz");
        }

        List<String> matching = List.of(
                "blocked-pkg-0-1.0.0.tgz",
                "deep/path/blocked-pkg-50-1.0.0.tgz",
                "node_modules/blocked-pkg-199-1.0.9.tgz");
        List<String> nonMatching = List.of(
                "org/apache/commons/commons-lang3-3.12.0.jar",
                "blocked-pkg-999-1.0.0.tgz",
                "blocked-pkg-0-2.0.0.tgz");

        assertBothModesEquivalent(excludes, matching, nonMatching);
    }

    @Test
    void testOptimizedAndStandardPatternsAreEquivalent_allNonDoubleStarPatterns() {
        // Only non-**/ patterns — createOptimizedPattern must leave them unchanged
        List<String> excludes = List.of("org/apache/**", "internal/private", "com/evil/*");

        List<String> matching = List.of(
                "org/apache/commons/file.jar", "internal/private", "com/evil/pkg.jar");
        List<String> nonMatching = List.of(
                "org/jfrog/file.jar", "internal/public", "com/safe/pkg.jar");

        assertBothModesEquivalent(excludes, matching, nonMatching);
    }

    private DbLocalRepo<?, ?> createTestRepo() {
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setKey("test-repo");
        descriptor.setSnapshotVersionBehavior(UNIQUE);
        return new DbLocalRepo<>(descriptor, descriptor.toRepositoryConfig(), internalRepoService, null,
                centralConfigService);
    }

    private void assertBothModesEquivalent(
            List<String> excludes, List<String> shouldMatch, List<String> shouldNotMatch) {
        DbLocalRepo<?, ?> repo = createTestRepo();

        homeStub.setProperty(ConstantValues.doubleStarOptimizationForExcludes, "false");
        Pattern standardPattern = repo.createPattern(excludes);

        homeStub.setProperty(ConstantValues.doubleStarOptimizationForExcludes, "true");
        Pattern optimizedPattern = repo.createPattern(excludes);

        Assertions.assertNotNull(standardPattern, "Standard pattern must not be null for non-empty excludes");
        Assertions.assertNotNull(optimizedPattern, "Optimized pattern must not be null for non-empty excludes");

        for (String path : shouldMatch) {
            Assertions.assertTrue(standardPattern.matcher(path).matches(),
                    "Standard pattern: expected match for '" + path + "'");
            Assertions.assertTrue(optimizedPattern.matcher(path).matches(),
                    "Optimized pattern: expected match for '" + path + "'");
        }
        for (String path : shouldNotMatch) {
            Assertions.assertFalse(standardPattern.matcher(path).matches(),
                    "Standard pattern: expected no match for '" + path + "'");
            Assertions.assertFalse(optimizedPattern.matcher(path).matches(),
                    "Optimized pattern: expected no match for '" + path + "'");
        }
    }
}
