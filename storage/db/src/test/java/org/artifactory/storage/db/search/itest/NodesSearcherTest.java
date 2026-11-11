package org.artifactory.storage.db.search.itest;

import ch.qos.logback.classic.Level;
import com.jfrog.test.JAssert;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.search.NodeSearcher;
import org.artifactory.test.TestUtils;
import org.jfrog.storage.JdbcHelper;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.inject.Inject;
import java.util.Set;
import java.util.stream.Collectors;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Yossi Shaul
 */
@Test
public class NodesSearcherTest extends DbBaseTest {

    @Inject
    private NodeSearcher nodeSearcher;

    @BeforeClass
    public void setup() {
        importSql("/sql/nodes-for-searches.sql");
        TestUtils.setLoggingLevel(JdbcHelper.class, Level.INFO);
    }

    @BeforeMethod(alwaysRun = true)
    public void defaultsForNodeSearcherSha256ShaFirstProbe() {
        NodeSearcher.invalidateSha256ProbeLimitReachedCacheForTests();
        artifactoryHomeBoundTest.setStringSystemProperty(ConstantValues.findFilesBySha256ShaFirstProbeEnabled, "true");
        artifactoryHomeBoundTest.setStringSystemProperty(ConstantValues.findFilesBySha256ShaFirstProbeLimit, "50");
        artifactoryHomeBoundTest.setStringSystemProperty(ConstantValues.repoItemsCacheQueryLimit, "500");
    }

    @Test
    public void findFilesByNameUnderPath() {
        String fileName = "sha256__0e03bdcc26d7a9a57ef3b6f1bf1a210cff6239bff7c8cac72435984032851689";
        Set<FileInfo> nodes = nodeSearcher.findFiles("docker", "hello", fileName);
        assertThat(toRepoPaths(nodes)).containsOnly(
                new RepoPathImpl("docker", "hello/latest/" + fileName),
                new RepoPathImpl("docker", "hello/1.0/" + fileName)
        );
    }

    @Test
    public void findAllManifests() {
        String fileName = "manifest.json";
        Set<FileInfo> nodes = nodeSearcher.findManifests("docker", "hello", false, true, true);
        assertThat(toRepoPaths(nodes)).containsOnly(
                new RepoPathImpl("docker", "hello/latest/" + fileName),
                new RepoPathImpl("docker", "hello/1.0/" + fileName),
                new RepoPathImpl("docker", "hello/1.1/list.manifest.json")
        );
    }

    @Test
    public void findManifestsOnly() {
        String fileName = "manifest.json";
        Set<FileInfo> nodes = nodeSearcher.findManifests("docker", "hello",
                false, true, false);
        assertThat(toRepoPaths(nodes)).containsOnly(
                new RepoPathImpl("docker", "hello/latest/" + fileName),
                new RepoPathImpl("docker", "hello/1.0/" + fileName)
        );
    }

    @Test
    public void findManifestsListOnly() {
        String fileName = "list.manifest.json";
        Set<FileInfo> nodes = nodeSearcher.findManifests("docker", "hello", false,
                false, true);
        assertThat(toRepoPaths(nodes)).containsOnly(
                new RepoPathImpl("docker", "hello/1.1/" + fileName)
        );
    }

    @Test
    public void findFilesByNameFromRootPath() {
        Set<FileInfo> nodes = nodeSearcher.findFiles("docker", "", "manifest.json");
        assertThat(toRepoPaths(nodes)).containsOnly(
                new RepoPathImpl("docker", "hello/latest/manifest.json"),
                new RepoPathImpl("docker", "hello/1.0/manifest.json"),
                new RepoPathImpl("docker", "ubuntu/latest/manifest.json")
        );
    }

    @Test
    public void findFilesByNameDirectlyUnderRootPath() {
        Set<FileInfo> nodes = nodeSearcher.findFiles("repo1", "", "readme.md");
        assertThat(toRepoPaths(nodes)).containsOnly(new RepoPathImpl("repo1", "readme.md"));
    }

    @Test
    public void findFilesMissingRepo() {
        JAssert.assertThrows(IllegalArgumentException.class, () -> nodeSearcher.findFiles(null, "path", "name"));
        JAssert.assertThrows(IllegalArgumentException.class, () -> nodeSearcher.findFiles("repo", null, " "));
    }

    @Test
    public void findFilesBySha256UnderPath() {
        String sha256 = "0e03bdcc26d7a9a57ef3b6f1bf1a210cff6239bff7c8cac72435984032851689";
        Set<FileInfo> nodes = nodeSearcher.findFilesBySha256("docker", "hello", sha256);
        String fieName = "sha256__" + sha256;
        assertThat(toRepoPaths(nodes)).containsOnly(
                new RepoPathImpl("docker", "hello/latest/" + fieName),
                new RepoPathImpl("docker", "hello/1.0/" + fieName)
        );
    }

    @Test
    public void findFilesBySha256FromRootPath() {
        Set<FileInfo> nodes = nodeSearcher.findFilesBySha256("repo1", "",
                "0e03bdcc26d7a9a57ef3b6f1bf1a210cff6239bff7c8cac72435984032851670");
        assertThat(toRepoPaths(nodes)).containsOnly(
                new RepoPathImpl("repo1", "ant/ant/1.5/ant-1.5.jar")
        );
    }

    @Test
    public void findFilesBySha256DirectlyUnderRootPath() {
        Set<FileInfo> nodes = nodeSearcher
                .findFilesBySha256("repo1", "", "0e03bdcc26d7a9a57ef3b6f1bf1a210cff6239bff7c8cac72435984032851671");
        assertThat(toRepoPaths(nodes)).containsOnly(new RepoPathImpl("repo1", "readme.md"));
    }

    @Test
    public void findFilesBySha256MissingRepo() {
        JAssert.assertThrows(IllegalArgumentException.class, () ->
                nodeSearcher.findFilesBySha256(null, "path", "name"));
        JAssert.assertThrows(IllegalArgumentException.class, () ->
                nodeSearcher.findFilesBySha256("repo", null, " "));
    }

    @Test
    public void findFilesBySha256UnderPathUsingPropertySearch() {
        setSha256Ready(false);  // force search by property
        String sha256 = "692c352adcf2821d6988021248da6b276cb738808f69dcc7bbb74a9c952146f7";
        Set<FileInfo> nodes = nodeSearcher.findFilesBySha256("docker", "ubuntu", sha256);
        String fieName = "sha256__" + sha256;
        assertThat(toRepoPaths(nodes)).containsOnly(
                new RepoPathImpl("docker", "ubuntu/latest/" + fieName)
        );
        setSha256Ready(true);
    }

    @Test
    public void findFilesBySha256_shaFirstProbe_dotPathMatchesDotSlashStar() {
        String sha256 = "0e03bdcc26d7a9a57ef3b6f1bf1a210cff6239bff7c8cac72435984032851689";
        Set<FileInfo> nodes = nodeSearcher.findFilesBySha256("docker", ".", sha256);
        String fileName = "sha256__" + sha256;
        assertThat(toRepoPaths(nodes)).containsOnly(
                new RepoPathImpl("docker", "hello/latest/" + fileName),
                new RepoPathImpl("docker", "hello/1.0/" + fileName)
        );
    }

    @Test
    public void findFilesBySha256_shaFirstProbe_inMemoryFilterExcludesOtherPaths() {
        String sha256 = "0e03bdcc26d7a9a57ef3b6f1bf1a210cff6239bff7c8cac72435984032851689";
        Set<FileInfo> nodes = nodeSearcher.findFilesBySha256("docker", "ubuntu", sha256);
        assertThat(nodes).hasSize(0);
    }

    @Test
    public void findFilesBySha256_shaFirstProbe_saturatedProbeStillReturnsAllMatches() {
        artifactoryHomeBoundTest.setStringSystemProperty(ConstantValues.findFilesBySha256ShaFirstProbeLimit, "1");
        String sha256 = "0e03bdcc26d7a9a57ef3b6f1bf1a210cff6239bff7c8cac72435984032851689";
        Set<FileInfo> nodes = nodeSearcher.findFilesBySha256("docker", "hello", sha256);
        String fileName = "sha256__" + sha256;
        assertThat(toRepoPaths(nodes)).containsOnly(
                new RepoPathImpl("docker", "hello/latest/" + fileName),
                new RepoPathImpl("docker", "hello/1.0/" + fileName)
        );
    }

    @Test
    public void findFilesBySha256_shaFirstProbe_secondCallStillCorrectAfterProbeLimitCache() {
        artifactoryHomeBoundTest.setStringSystemProperty(ConstantValues.findFilesBySha256ShaFirstProbeLimit, "1");
        String sha256 = "0e03bdcc26d7a9a57ef3b6f1bf1a210cff6239bff7c8cac72435984032851689";
        String fileName = "sha256__" + sha256;
        nodeSearcher.findFilesBySha256("docker", "hello", sha256);
        Set<FileInfo> nodes = nodeSearcher.findFilesBySha256("docker", "hello", sha256);
        assertThat(toRepoPaths(nodes)).containsOnly(
                new RepoPathImpl("docker", "hello/latest/" + fileName),
                new RepoPathImpl("docker", "hello/1.0/" + fileName)
        );
    }

    @Test
    public void findFilesBySha256_shaFirstProbe_disabledUsesLegacyQueryPath() {
        artifactoryHomeBoundTest.setStringSystemProperty(ConstantValues.findFilesBySha256ShaFirstProbeEnabled, "false");
        String sha256 = "0e03bdcc26d7a9a57ef3b6f1bf1a210cff6239bff7c8cac72435984032851689";
        Set<FileInfo> nodes = nodeSearcher.findFilesBySha256("docker", "hello", sha256);
        String fileName = "sha256__" + sha256;
        assertThat(toRepoPaths(nodes)).containsOnly(
                new RepoPathImpl("docker", "hello/latest/" + fileName),
                new RepoPathImpl("docker", "hello/1.0/" + fileName)
        );
    }

    private void setSha256Ready(boolean ready) {
        InternalDbService dbService = ContextHelper.get().beanForType(InternalDbService.class);
        TestUtils.setField(dbService, "sha256Ready", ready);
    }

    private Set<RepoPath> toRepoPaths(Set<FileInfo> nodes) {
        return nodes.stream().map(ItemInfo::getRepoPath).collect(Collectors.toSet());
    }
}
