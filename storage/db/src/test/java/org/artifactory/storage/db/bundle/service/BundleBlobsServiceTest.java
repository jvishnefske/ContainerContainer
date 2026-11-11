package org.artifactory.storage.db.bundle.service;

import org.artifactory.bundle.BundleType;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.bundle.dao.BundleBlobsDao;
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
public class BundleBlobsServiceTest {

    private BundleBlobsService service;

    @Mock
    private BundleBlobsDao bundleBlobsDao;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new BundleBlobsServiceImpl(bundleBlobsDao);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        Mockito.verifyNoMoreInteractions(bundleBlobsDao);
        mocks.close();
    }

    @Test
    public void testGetBundleBlobData() throws SQLException {
        // test execution
        service.getBundleBlobData("bundle1", "version1", BundleType.SOURCE);

        //verify
        verify(bundleBlobsDao).getBundleBlobData("bundle1", "version1", BundleType.SOURCE);
    }

    @Test
    public void testGetArtifactsBundleWithException() throws SQLException {
        when(bundleBlobsDao.getBundleBlobData("bundle1", "version1", BundleType.SOURCE))
                .thenThrow(new SQLException("DB DOWN!!!"));

        // test execution
        assertThrows(StorageException.class,
                () -> service.getBundleBlobData("bundle1", "version1", BundleType.SOURCE)
        );

        //verify
        verify(bundleBlobsDao).getBundleBlobData("bundle1", "version1", BundleType.SOURCE);
    }
}
