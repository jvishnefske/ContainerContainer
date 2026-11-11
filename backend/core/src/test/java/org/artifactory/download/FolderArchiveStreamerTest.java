package org.artifactory.download;

import com.google.common.collect.Lists;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.io.SimpleResourceStreamHandle;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.fs.tree.ItemNode;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.traffic.TrafficService;
import org.jfrog.common.ArchiveUtils;
import org.jfrog.common.archive.ArchiveType;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tom Arie
 */
public class FolderArchiveStreamerTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ItemNode itemNode;

    @Mock
    private ItemInfo itemInfo;

    @Mock
    private RepoPath repoPath;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ArtifactoryContext context;
    private AutoCloseable mocks;

    @BeforeMethod
    private void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(AuthorizationService.class)).thenReturn(mock(AuthorizationService.class));
        when(context.beanForType(SecurityService.class)).thenReturn(mock(SecurityService.class));
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test(dataProvider = "provideFolderDownloadAttributes")
    public void testWriteRecursiveWithEmptyDirectory(boolean enabledEmptyDirectories,
            int numberOfWriteArtifactInvocations) throws IOException {
        FolderArchiveStreamer folderArchiveStreamer = new FolderArchiveStreamer(null, null,
                999, 1000, null, false, enabledEmptyDirectories);
        FolderArchiveStreamer spyFolderArchiveStreamer = Mockito.spy(folderArchiveStreamer);
        List<ItemNode> children = Lists.newArrayList();
        setItemNodeMock(children, true);
        doNothing().when(spyFolderArchiveStreamer).writeArtifact(any(RepoPath.class),
                Mockito.anyLong(), Mockito.anyBoolean());
        spyFolderArchiveStreamer.writeRecursive(itemNode);
        verify(spyFolderArchiveStreamer, times(numberOfWriteArtifactInvocations)).
                writeArtifact(any(RepoPath.class), Mockito.anyLong(), Mockito.anyBoolean());
    }

    @DataProvider
    public static Object[][] provideFolderDownloadAttributes() {
        return new Object[][] {
                //enabledEmptyDirectories
                {false, 0},
                {true, 1}
        };
    }

    private void setItemNodeMock(List<ItemNode> children, boolean isFolder) {
        if (isFolder) {
            when(itemNode.isFolder()).thenReturn(true);
            when(itemInfo.getRepoPath()).thenReturn(repoPath);
            when(itemNode.getChildren()).thenReturn(children);
            when(itemNode.getItemInfo()).thenReturn(itemInfo);
        } else {
            when(itemNode.isFolder()).thenReturn(false);
        }
    }

    @Test
    public void testWriteRecusiveWithChildArtifact() throws IOException {
        //given:
        FolderArchiveStreamer folderArchiveStreamer = new FolderArchiveStreamer(null, null,
                999, 1000, null, false, false);
        FolderArchiveStreamer spyFolderArchiveStreamer = Mockito.spy(folderArchiveStreamer);
        List<ItemNode> children = Lists.newArrayList();
        setItemNodeMock(children, false);
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        when(itemNode.getItemInfo()).thenReturn(fileInfo);
        when(fileInfo.getRepoPath()).thenReturn(repoPath);
        when(fileInfo.getSize()).thenReturn(5020L);
        doNothing().when(spyFolderArchiveStreamer).writeArtifact(any(RepoPath.class),
                Mockito.anyLong(), Mockito.anyBoolean());
        //when:
        spyFolderArchiveStreamer.writeRecursive(itemNode);
        //then:
        verify(spyFolderArchiveStreamer, times(1)).
                writeArtifact(any(RepoPath.class), Mockito.anyLong(), Mockito.anyBoolean());
    }

    @Test(dataProvider = "providePathInsideRoot")
    public void testWriteArtifact(String relativePath, long fileSize, boolean isDirectory) throws IOException {
        //given
        RepoPath repoPath = new RepoPathImpl("libs-releases-local", relativePath);
        SimpleResourceStreamHandle simpleResourceStreamHandle =
                new SimpleResourceStreamHandle(new ByteArrayInputStream(new byte[1024]));
        doAnswer(invocationOnMock -> {
            return simpleResourceStreamHandle;
        }).when(repositoryService).getResourceStreamHandle(any(RepoPath.class));
        FolderArchiveStreamer folderArchiveStreamer = new FolderArchiveStreamer(repoPath, ArchiveType.ZIP,
                999, 1000, null, false, true);
        OutputStream outputStream = mock(OutputStream.class);
        folderArchiveStreamer.archiveOutputStream = ArchiveUtils.createArchiveOutputStream
                (outputStream, ArchiveType.ZIP);
        TrafficService trafficService = Mockito.mock(TrafficService.class);
        when(context.beanForType(TrafficService.class)).thenReturn(trafficService);
        homeStub.setProperty(ConstantValues.downloadStatsEnabled, "false");
        //when:
        folderArchiveStreamer.writeArtifact(repoPath, fileSize, isDirectory);
        //then:
        verify(outputStream, atLeastOnce()).write(Mockito.any(), Mockito.anyInt(), Mockito.anyInt());
    }

    @DataProvider
    public static Object[][] providePathInsideRoot() {
        return new Object[][] {
                //relativePath, fileSize, isDirectory
                {"a/emptyFolder/", 0L, true},
                {"artifact.jar", 20L, false}
        };
    }
}