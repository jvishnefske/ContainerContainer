package org.artifactory.repo.service.mover;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.StoringRepo;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Igor Usenko
 */
public class CopyUsingNodesChecksumsTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    MoveMultiStatusHolder status;
    @Mock
    MoverConfig config;
    @Mock
    VfsFile sourceFile;
    @Mock
    MutableVfsFile targetFile;

    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        status = Mockito.mock(MoveMultiStatusHolder.class);
        config = Mockito.mock(MoverConfig.class);
        sourceFile = Mockito.mock(VfsFile.class);
        when(sourceFile.getSha1()).thenReturn("sha1");
        when(sourceFile.getSha2()).thenReturn("sha2");
        when(sourceFile.getMd5()).thenReturn("md5");
        when(sourceFile.length()).thenReturn(42L);
        when(sourceFile.getProperties()).thenReturn(new PropertiesImpl());
        targetFile = Mockito.mock(MutableVfsFile.class);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void whenUseNodesChecksumsDisabled() {
        getBound().setProperty(ConstantValues.copyArtifactUseNodesChecksumsEnabled, "false");

        MoverForTest mover = new MoverForTest(status, config);
        mover.copyVfsFile(sourceFile, targetFile);

        verify(targetFile).tryUsingExistingBinary("sha1", "sha2", "md5", 42L);
    }

    @Test
    public void whenUseNodesChecksumsEnabled() {
        getBound().setProperty(ConstantValues.copyArtifactUseNodesChecksumsEnabled, "true");

        MoverForTest mover = new MoverForTest(status, config);
        mover.copyVfsFile(sourceFile, targetFile);

        verify(targetFile).setFileBinaryInfo("sha1", "sha2", "md5", 42L);
    }

    private static class MoverForTest extends BaseRepoPathMover {

        protected MoverForTest(MoveMultiStatusHolder status, MoverConfig moverConfig) {
            super(status, moverConfig);
        }

        @Override
        protected void beforeOperationOnFolder(VfsItem sourceItem, RepoPath targetRepoPath) {

        }

        @Override
        protected void afterOperationOnFolder(VfsItem sourceItem, RepoRepoPath<StoringRepo> targetRrp,
                VfsFolder targetFolder) {

        }

        @Override
        protected void beforeOperationOnFile(VfsItem sourceItem, RepoPath targetRepoPath) {

        }

        @Override
        protected void operationOnFile(VfsFile sourceItem, RepoRepoPath<StoringRepo> targetRrp,
                InterceptorMoveCopyContext ctx) {

        }

        @Override
        protected VfsItem getSourceItem(VfsItem sourceItem) {
            return null;
        }
    }
}
