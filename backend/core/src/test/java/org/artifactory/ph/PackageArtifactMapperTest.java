package org.artifactory.ph;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.SetMultimap;
import com.jfrog.ph.acl.model.PackageArtifact;
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl;
import org.artifactory.aql.result.rows.FileInfoWithStatisticsItemRow;
import org.artifactory.common.repo.PackageType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import java.util.Date;
import java.util.Set;
import java.util.stream.Stream;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class PackageArtifactMapperTest {

    private static final String PATH = "a/b/c";
    private static final String REPO_KEY = "repo1";
    private static final String USER = "admin";
    private static final String PROP_KEY = "k1";
    private static final String PROP_VALUE = "v1";
    private static final RepoPath REPO_PATH = RepoPathFactory.create(REPO_KEY, PATH);

    @Mock
    private Properties properties;
    private AutoCloseable mocks;


    @BeforeEach
    public void setup() {
        mocks = openMocks(this);

        when(properties.keys()).thenReturn(LinkedHashMultiset.create(Set.of(PROP_KEY)));
        when(properties.get(PROP_KEY)).thenReturn(Set.of(PROP_VALUE));
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
    }

    private void setup(VfsItem item, boolean isFile) {
        when(item.getRepoKey()).thenReturn(REPO_KEY);
        when(item.getName()).thenReturn(REPO_PATH.getName());
        when(item.getPath()).thenReturn(REPO_PATH.getPath());
        when(item.getId()).thenReturn(Long.MAX_VALUE);
        when(item.getCreated()).thenReturn(Long.MAX_VALUE);
        when(item.isFile()).thenReturn(isFile);
        when(item.isFolder()).thenReturn(!isFile);
        when(item.getProperties()).thenReturn(properties);
    }

    private VfsFile file() {
        VfsFile file = mock(VfsFile.class);
        setup(file, true);
        when(file.length()).thenReturn(Long.MAX_VALUE);
        when(file.getSha1()).thenReturn("sha1");
        when(file.getSha2()).thenReturn("sha2");
        when(file.getMd5()).thenReturn("md5");
        FileInfo fileInfo = fileInfo();
        when(file.getInfo()).thenReturn(fileInfo);
        return file;
    }

    private void setup(ItemInfo info) {
        when(info.getRepoPath()).thenReturn(REPO_PATH);
        when(info.getCreated()).thenReturn(Long.MAX_VALUE);
        when(info.getRepoKey()).thenReturn(REPO_PATH.getRepoKey());
        when(info.getLastModified()).thenReturn(Long.MAX_VALUE);
        when(info.getName()).thenReturn(REPO_PATH.getName());
        when(info.getRelPath()).thenReturn(REPO_PATH.getPath());
        when(info.getModifiedBy()).thenReturn(USER);
        when(info.getLastUpdated()).thenReturn(Long.MAX_VALUE);
        when(info.getCreatedBy()).thenReturn(USER);
    }

    private FileInfo fileInfo() {
        FileInfo fileInfo = mock(FileInfo.class);
        setup(fileInfo);
        when(fileInfo.getSha1()).thenReturn("sha1");
        when(fileInfo.getSha2()).thenReturn("sha2");
        when(fileInfo.getMd5()).thenReturn("md5");
        return fileInfo;
    }

    private VfsFolder folder() {
        VfsFolder folder = mock(VfsFolder.class);
        setup(folder, false);
        return folder;
    }

    @Test
    public void testFile() {

        PackageArtifact artifact = PackageArtifactMapper.artifact(file(), PackageType.CRAN);

        assertEquals(artifact.getLastUpdated(), Long.MAX_VALUE);
        assertEquals(artifact.getPath(), PATH);
        assertEquals(artifact.getCreated(), Long.MAX_VALUE);
        assertEquals(artifact.getRepoKey(), REPO_KEY);
        assertEquals(artifact.getLastModified(), Long.MAX_VALUE);
        assertEquals(artifact.getCreatedBy(), USER);
        assertEquals(artifact.getId(), Long.MAX_VALUE);

        assertEquals(artifact.getLength(), Long.MAX_VALUE);
        assertEquals(artifact.getMd5(), "md5");
        assertEquals(artifact.getName(), REPO_PATH.getName());
        assertEquals(artifact.getPackageType(), PackageType.CRAN);
        assertEquals(artifact.getSha1(), "sha1");
        assertEquals(artifact.getSha2(), "sha2");
        assertNotNull(artifact.getProperties());
        assertEquals(artifact.getProperties().size(), 1);
        assertEquals(artifact.getProperties().get(PROP_KEY).stream().findFirst().orElse(""), PROP_VALUE);
    }

    @Test
    public void testFolder() {

        PackageArtifact artifact = PackageArtifactMapper.artifact(folder(), PackageType.CRAN);

        assertEquals(artifact.getLastUpdated(), 0);
        assertEquals(artifact.getPath(), PATH);
        assertEquals(artifact.getCreated(), Long.MAX_VALUE);
        assertEquals(artifact.getRepoKey(), REPO_KEY);
        assertEquals(artifact.getLastModified(), 0);
        assertEquals(artifact.getCreatedBy(), null);
        assertEquals(artifact.getId(), Long.MAX_VALUE);

        assertEquals(artifact.getLength(), 0);
        assertEquals(artifact.getMd5(), null);
        assertEquals(artifact.getName(), REPO_PATH.getName());
        assertEquals(artifact.getPackageType(), PackageType.CRAN);
        assertEquals(artifact.getSha1(), null);
        assertEquals(artifact.getSha2(), null);
        assertNotNull(artifact.getProperties());
        assertEquals(artifact.getProperties().size(), 1);
        assertEquals(artifact.getProperties().get(PROP_KEY).stream().findFirst().orElse(""), PROP_VALUE);
    }

    @Test
    public void testTarget() {
        PackageArtifact source = PackageArtifactMapper.artifact(file(), PackageType.CRAN);
        RepoPath targetRepoPath = RepoPathFactory.create("repo2", source.getPath());
        PackageArtifact target =
                PackageArtifactMapper.artifact(source, targetRepoPath, source.getPackageType());

        assertEquals(target.getLastUpdated(), 0);
        assertEquals(target.getPath(), targetRepoPath.getPath());
        assertEquals(target.getCreated(), Long.MAX_VALUE);
        assertEquals(target.getRepoKey(), targetRepoPath.getRepoKey());
        assertEquals(target.getLastModified(), Long.MAX_VALUE);
        assertEquals(target.getCreatedBy(), null);
        assertEquals(target.getId(), 0);

        assertEquals(target.getLength(), Long.MAX_VALUE);
        assertEquals(target.getMd5(), "md5");
        assertEquals(target.getName(), targetRepoPath.getName());
        assertEquals(target.getPackageType(), PackageType.CRAN);
        assertEquals(target.getSha1(), "sha1");
        assertEquals(target.getSha2(), "sha2");
        assertNotNull(target.getProperties());
        assertEquals(target.getProperties().size(), 1);
        assertEquals(target.getProperties().get(PROP_KEY).stream().findFirst().orElse(""), PROP_VALUE);
    }

    @Test
    public void testFromItemInfoFolderToArtifact() {
        ItemInfo itemInfo = mock(ItemInfo.class);
        when(itemInfo.isFolder()).thenReturn(true);
        when(itemInfo.getCreated()).thenReturn(12L);
        when(itemInfo.getId()).thenReturn(10L);
        when(itemInfo.getRelPath()).thenReturn("relPath");
        when(itemInfo.getRepoKey()).thenReturn("repoKey");
        when(itemInfo.getName()).thenReturn("name");
        when(itemInfo.getLastUpdated()).thenReturn(100L);
        when(itemInfo.getLastModified()).thenReturn(102L);
        when(itemInfo.getCreatedBy()).thenReturn("createdBy");
        when(itemInfo.getModifiedBy()).thenReturn("modifiedBy");
        PackageArtifact artifact = PackageArtifactMapper.artifact(itemInfo);
        Assertions.assertFalse(artifact.isFile());
        Assertions.assertEquals(12L, artifact.getCreated());
        Assertions.assertEquals(10L, artifact.getId());
        Assertions.assertEquals("relPath", artifact.getPath());
        Assertions.assertEquals("repoKey", artifact.getRepoKey());
        Assertions.assertEquals("name", artifact.getName());
        Assertions.assertEquals(100L, artifact.getLastUpdated());
        Assertions.assertEquals(102L, artifact.getLastModified());
        Assertions.assertEquals("createdBy", artifact.getCreatedBy());
        Assertions.assertEquals("modifiedBy", artifact.getModifiedBy());
    }

    @Test
    public void testFromItemInfoFileToArtifact() {
        FileInfo itemInfo = mock(FileInfo.class);
        when(itemInfo.isFolder()).thenReturn(false);
        when(itemInfo.getCreated()).thenReturn(12L);
        when(itemInfo.getId()).thenReturn(10L);
        when(itemInfo.getRelPath()).thenReturn("relPath");
        when(itemInfo.getRepoKey()).thenReturn("repoKey");
        when(itemInfo.getName()).thenReturn("name");
        when(itemInfo.getLastUpdated()).thenReturn(100L);
        when(itemInfo.getLastModified()).thenReturn(102L);
        when(itemInfo.getCreatedBy()).thenReturn("createdBy");
        when(itemInfo.getModifiedBy()).thenReturn("modifiedBy");
        when(itemInfo.getMd5()).thenReturn("md5");
        when(itemInfo.getSha2()).thenReturn("sh2");
        when(itemInfo.getSha1()).thenReturn("sh1");
        PackageArtifact artifact = PackageArtifactMapper.artifact(itemInfo);
        Assertions.assertTrue(artifact.isFile());
        Assertions.assertEquals(12L, artifact.getCreated());
        Assertions.assertEquals(10L, artifact.getId());
        Assertions.assertEquals("relPath", artifact.getPath());
        Assertions.assertEquals("repoKey", artifact.getRepoKey());
        Assertions.assertEquals("name", artifact.getName());
        Assertions.assertEquals(100L, artifact.getLastUpdated());
        Assertions.assertEquals(102L, artifact.getLastModified());
        Assertions.assertEquals("createdBy", artifact.getCreatedBy());
        Assertions.assertEquals("modifiedBy", artifact.getModifiedBy());
        Assertions.assertEquals("md5", artifact.getMd5());
        Assertions.assertEquals("sh2", artifact.getSha2());
        Assertions.assertEquals("sh1", artifact.getSha1());
    }

    @Test
    public void testRepoResource() {

        RepoResource repoResource = mock(RepoResource.class);
        RepoResourceInfo info = mock(RepoResourceInfo.class);
        when(info.getRepoPath()).thenReturn(REPO_PATH);
        when(info.getLastModified()).thenReturn(Long.MAX_VALUE);
        when(info.getSize()).thenReturn(Long.MAX_VALUE);
        when(info.getSha1()).thenReturn("sha1");
        when(info.getSha2()).thenReturn("sha2");
        when(info.getMd5()).thenReturn("md5");
        when(info.getName()).thenReturn(REPO_PATH.getName());

        when(repoResource.getInfo()).thenReturn(info);

        PackageArtifact artifact = PackageArtifactMapper.artifact(repoResource, PackageType.CRAN);

        assertEquals(artifact.getLastUpdated(), 0);
        assertEquals(artifact.getPath(), PATH);
        assertEquals(artifact.getCreated(), 0);
        assertEquals(artifact.getRepoKey(), REPO_KEY);
        assertEquals(artifact.getLastModified(), Long.MAX_VALUE);
        assertEquals(artifact.getCreatedBy(), null);
        assertEquals(artifact.getId(), 0);

        assertEquals(artifact.getLength(), Long.MAX_VALUE);
        assertEquals(artifact.getMd5(), "md5");
        assertEquals(artifact.getName(), REPO_PATH.getName());
        assertEquals(artifact.getPackageType(), PackageType.CRAN);
        assertEquals(artifact.getSha1(), "sha1");
        assertEquals(artifact.getSha2(), "sha2");
        assertNotNull(artifact.getProperties());
        assertEquals(artifact.getProperties().size(), 0);
    }

    @Test
    public void testLazyArtifactProperties() {
        VfsFile file = file();
        PackageArtifact artifact = PackageArtifactMapper.artifact(file, PackageType.CRAN);
        verify(file, never()).getProperties();
        SetMultimap<String, String> multimap = artifact.getProperties();
        verify(file, never()).getProperties();
        assertEquals(multimap.size(), 1);
        assertEquals(multimap.get(PROP_KEY), Set.of(PROP_VALUE));
        verify(file, times(1)).getProperties();
    }

    @Test
    public void testImmutableArtifactProperties() {
        VfsFile file = file();
        PackageArtifact artifact = PackageArtifactMapper.artifact(file, PackageType.CRAN);
        Assertions.assertThrowsExactly(UnsupportedOperationException.class, () -> {
            SetMultimap<String, String> multimap = artifact.getProperties();
            multimap.clear();
        });
    }

    @ParameterizedTest
    @MethodSource("packagePathProvider")
    void testArtifactFromFileInfoWithStatisticsItemRow(String path, String name, String expectedPath) {
        FileInfoWithStatisticsItemRow row = spy(FileInfoWithStatisticsItemRow.class);
        FileInfo fileInfo = mock(FileInfo.class);
        doReturn(fileInfo).when(row).toFileInfo();
        when(row.getCreated()).thenReturn(123456789L);
        when(row.getItemId()).thenReturn(1L);
        when(row.getPath()).thenReturn(path);
        when(row.getName()).thenReturn(name);
        when(row.getRepo()).thenReturn("repoKey");
        when(row.getActualMd5()).thenReturn("md5hash");
        when(row.getActualSha1()).thenReturn("sha1hash");
        when(row.getSha2()).thenReturn("sha2hash");
        when(fileInfo.getLastUpdated()).thenReturn(987654321L);
        when(row.getModified()).thenReturn(123456789L);
        when(row.getCreatedBy()).thenReturn("creator");
        when(row.getModifiedBy()).thenReturn("modifier");
        when(row.getSize()).thenReturn(1024L);
        when(row.getStatDownloaded()).thenReturn(10L);

        PackageArtifact artifact = PackageArtifactMapper.artifact(row);

        Assertions.assertEquals(123456789L, artifact.getCreated());
        Assertions.assertEquals(1L, artifact.getId());
        Assertions.assertEquals(expectedPath, artifact.getPath());
        Assertions.assertEquals("repoKey", artifact.getRepoKey());
        Assertions.assertEquals(name, artifact.getName());
        Assertions.assertEquals("md5hash", artifact.getMd5());
        Assertions.assertEquals("sha1hash", artifact.getSha1());
        Assertions.assertEquals("sha2hash", artifact.getSha2());
        Assertions.assertEquals(987654321L, artifact.getLastUpdated());
        Assertions.assertEquals(123456789L, artifact.getLastModified());
        Assertions.assertEquals("creator", artifact.getCreatedBy());
        Assertions.assertEquals("modifier", artifact.getModifiedBy());
        Assertions.assertEquals(1024L, artifact.getLength());
        Assertions.assertEquals(10L, artifact.getDownloadCount());
        Assertions.assertEquals(HashMultimap.create(), artifact.getProperties());
    }

    private static Stream<Arguments> packagePathProvider() {
        return Stream.of(
                Arguments.of(".", "packageName.nupkg", "packageName.nupkg"),
                Arguments.of("path/to/artifact", "packageName.nupkg", "path/to/artifact/packageName.nupkg"),
                Arguments.of(null, "packageName.nupkg", "null/packageName.nupkg"),
                Arguments.of(" ", "packageName.nupkg", "packageName.nupkg"),
                Arguments.of("", "packageName.nupkg", "packageName.nupkg")
        );
    }

    @Test
    void testArtifactFromAqlItem() {
        AqlBaseFullRowImpl row = mock(AqlBaseFullRowImpl.class);

        when(row.containsKey("statDownloads")).thenReturn(Boolean.TRUE);
        when(row.getCreated()).thenReturn(new java.util.Date(123456789L));
        when(row.getNodeId()).thenReturn(1L);
        when(row.getPath()).thenReturn("path/to/artifact");
        when(row.getRepo()).thenReturn("repoKey");
        when(row.getName()).thenReturn("artifactName");
        when(row.getActualMd5()).thenReturn("md5hash");
        when(row.getActualSha1()).thenReturn("sha1hash");
        when(row.getSha2()).thenReturn("sha2hash");
        when(row.getUpdated()).thenReturn(new Date(987654321L));
        when(row.getModified()).thenReturn(new Date(123456789L));
        when(row.getCreatedBy()).thenReturn("creator");
        when(row.getModifiedBy()).thenReturn("modifier");
        when(row.getSize()).thenReturn(1024L);
        when(row.getDownloads()).thenReturn(100);

        PackageArtifact artifact = PackageArtifactMapper.artifact(row);

        Assertions.assertEquals(123456789L, artifact.getCreated());
        Assertions.assertEquals(1L, artifact.getId());
        Assertions.assertEquals("path/to/artifact/artifactName", artifact.getPath());
        Assertions.assertEquals("repoKey", artifact.getRepoKey());
        Assertions.assertEquals("artifactName", artifact.getName());
        Assertions.assertEquals("md5hash", artifact.getMd5());
        Assertions.assertEquals("sha1hash", artifact.getSha1());
        Assertions.assertEquals("sha2hash", artifact.getSha2());
        Assertions.assertEquals(987654321L, artifact.getLastUpdated());
        Assertions.assertEquals(123456789L, artifact.getLastModified());
        Assertions.assertEquals("creator", artifact.getCreatedBy());
        Assertions.assertEquals("modifier", artifact.getModifiedBy());
        Assertions.assertEquals(1024L, artifact.getLength());
        Assertions.assertEquals(100L, artifact.getDownloadCount());
        Assertions.assertEquals(HashMultimap.create(), artifact.getProperties());
    }

}