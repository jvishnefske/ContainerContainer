package org.artifactory.sh.cleanup;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.sh.cleanup.minor.TrashAndBinariesGCProvider;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class GCProviderTest extends ArtifactoryHomeBoundTest {

    public void testShouldPrintReport() {
        TrashAndBinariesGCProvider trashAndBinariesGCProvider = new TrashAndBinariesGCProvider(mock(
                RepositoryService.class), mock(TrashService.class), mock(GCService.class));
        assertTrue(trashAndBinariesGCProvider.shouldReportAfterBatch());
    }
}