package org.artifactory.storage.db.bundle.service;

import org.artifactory.bundle.BundleType;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

/**
 * @author Eli Skoran 10/08/2020
 */
public class ArtifactBundlesServiceTest {

    private ArtifactBundlesService service;

    @Mock
    private ArtifactBundlesDao artifactBundlesDao;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new ArtifactBundlesServiceImpl(artifactBundlesDao);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        Mockito.verifyNoMoreInteractions(artifactBundlesDao);
        mocks.close();
    }

    @Test
    public void testGetArtifactsBundle() throws SQLException {
        // test execution
        service.getArtifactsBundle("bundle1", "version1", BundleType.SOURCE);

        //verify
        verify(artifactBundlesDao).getArtifactsBundle("bundle1", "version1", BundleType.SOURCE);
    }

    @Test
    public void testGetArtifactsBundleWithException() throws SQLException {
        when(artifactBundlesDao.getArtifactsBundle("bundle1", "version1", BundleType.SOURCE))
                .thenThrow(new SQLException("DB DOWN!!!"));

        // test execution
        assertThrows(StorageException.class,
                () -> service.getArtifactsBundle("bundle1", "version1", BundleType.SOURCE)
        );

        //verify
        verify(artifactBundlesDao).getArtifactsBundle("bundle1", "version1", BundleType.SOURCE);
    }

    @Test
    public void testGetBundleNodes() throws SQLException {
        // test execution
        service.getBundleNodes(11111);

        //verify
        verify(artifactBundlesDao).getBundleNodes(11111);
    }

    @Test
    public void testGetBundleNodesWithException() throws SQLException {
        when(artifactBundlesDao.getBundleNodes(22222))
                .thenThrow(new SQLException("DB DOWN!!!"));

        // test execution
        assertThrows(StorageException.class,
                () -> service.getBundleNodes(22222)
        );

        //verify
        verify(artifactBundlesDao).getBundleNodes(22222);
    }
}
