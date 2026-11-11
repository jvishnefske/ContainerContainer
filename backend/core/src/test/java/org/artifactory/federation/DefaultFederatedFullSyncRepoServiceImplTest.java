package org.artifactory.federation;

import org.testng.annotations.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Sivan Shafrir
 */
public class DefaultFederatedFullSyncRepoServiceImplTest {
    @Test
    void testUnsupportedMethodForDryFullSync() {
        FederatedFullSyncRepoService repoServiceUnderTest = new DefaultFederatedFullSyncRepoServiceImpl();
        assertThrows(UnsupportedOperationException.class,
                () -> repoServiceUnderTest.deployDryFullSyncFile("name", null, "jpd",
                        "localRepo", "remoteRepo"));
    }

}
