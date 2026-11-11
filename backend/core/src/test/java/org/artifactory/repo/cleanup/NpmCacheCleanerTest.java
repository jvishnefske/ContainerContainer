package org.artifactory.repo.cleanup;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.sapi.search.VfsComparatorType;
import org.artifactory.sapi.search.VfsQuery;
import org.artifactory.sapi.search.VfsQueryResult;
import org.artifactory.sapi.search.VfsQueryRow;
import org.artifactory.sapi.search.VfsQueryService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.common.ConstantValues.npmVirtualCacheCleanupBatchSize;
import static org.artifactory.common.ConstantValues.npmVirtualCacheItemTtlSeconds;
import static org.artifactory.common.ConstantValues.npmVirtualCacheNonAuditItemTtlSeconds;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Yoaz Menda
 */
public class NpmCacheCleanerTest extends ArtifactoryHomeBoundTest {
    private static final Logger log = LoggerFactory.getLogger(NpmCacheCleanerTest.class);

    private NpmVirtualCacheCleaner npmCacheCleaner;

    @Mock
    private RepositoryService repoService;

    @Mock
    private VfsQueryService vfsQueryService;

    @Mock
    private VfsQuery vfsQuery;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private VirtualRepoConfig virtualRepoConfig;

    @Mock
    private VfsQueryResult queryResult;

    @Mock
    private VfsQueryRow vfsRow;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        npmCacheCleaner = new NpmVirtualCacheCleaner(repoService, vfsQueryService);
        setupMocks();
    }

    private void setupMocks() {
        when(vfsQuery.execute(anyLong())).thenReturn(queryResult);
        when(vfsQueryService.createQuery()).thenReturn(vfsQuery);
        when(vfsQuery.expectedResult(any())).thenReturn(vfsQuery);
        when(vfsQuery.setSingleRepoKey(any())).thenReturn(vfsQuery);
        when(vfsQuery.addPathFilter(any())).thenReturn(vfsQuery);
        when(vfsQuery.prop(any())).thenReturn(vfsQuery);
        when(vfsQuery.comp(any())).thenReturn(vfsQuery);
        ItemInfo itemInfo = mock(ItemInfo.class);
        when(itemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("npm", "aaa/bbb"));
        when(vfsRow.getItem()).thenReturn(itemInfo);
        when(vfsQuery.val(anyLong())).thenReturn(vfsQuery);
        when(vfsQuery.val(anyString())).thenReturn(vfsQuery);
        when(repoService.undeploy(any(RepoPath.class))).thenReturn(new BasicStatusHolder());
        when(virtualRepoConfig.getKey()).thenReturn("repoKey");

    }

    @AfterMethod
    public void reset() throws Exception {
        mocks.close();
    }

    @Test
    public void testCleanCacheNoItemsToDelete() {
        when(queryResult.getCount()).thenReturn(0L);
        long result = npmCacheCleaner.cleanupNonAuditCache(virtualRepoConfig);
        assertEquals(0, result);
    }

    @Test
    public void testRunOnlyForNpm() {
        VirtualRepoConfig npmRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Npm);
        VirtualRepoConfig nonNpmRepoConfig = VirtualRepoConfig.create("repoKey", RepoType.Generic);
        assertTrue(npmCacheCleaner.shouldRun(npmRepoConfig));
        assertFalse(npmCacheCleaner.shouldRun(nonNpmRepoConfig));
    }

    @Test
    public void testCleanCacheRunsNonAuditAndAuditPasses() {
        // No items to delete in either pass; we only assert how each pass is scoped.
        npmCacheCleaner.cleanCache(virtualRepoConfig);
        // The audit pass is scoped positively to the ".security" folder...
        verify(vfsQuery, times(1)).addPathFilter(".security/**");
        verify(vfsQuery, times(1)).addPathFilter(any());
        // ...while the non-audit pass excludes that folder with a node_path NOT-LIKE predicate.
        verify(vfsQuery, times(1)).val("*.security*");
        // Both passes query the same virtual repo.
        verify(vfsQuery, times(2)).setSingleRepoKey("repoKey");
    }

    @Test
    public void testAuditPassScopedToSecurityFolder() {
        npmCacheCleaner.cleanupAuditCache(virtualRepoConfig);
        verify(vfsQuery, times(1)).addPathFilter(".security/**");
        // The audit pass relies on the positive path filter, not the node_path exclusion.
        verify(vfsQuery, never()).val("*.security*");
    }

    @Test
    public void testNonAuditPassExcludesAuditFolder() {
        npmCacheCleaner.cleanupNonAuditCache(virtualRepoConfig);
        // The non-audit pass must NOT use a positive path filter; it excludes ".security" via node_path NOT-LIKE.
        verify(vfsQuery, never()).addPathFilter(any());
        verify(vfsQuery, times(1)).prop("node_path");
        verify(vfsQuery, times(1)).comp(VfsComparatorType.NOT_CONTAINS);
        verify(vfsQuery, times(1)).val("*.security*");
    }

    @Test
    public void testAuditAndNonAuditUseDifferentTtls() {
        ArgumentCaptor<Long> expiryCaptor = ArgumentCaptor.forClass(Long.class);
        npmCacheCleaner.cleanCache(virtualRepoConfig);
        verify(vfsQuery, times(2)).val(expiryCaptor.capture());

        // cleanCache runs the non-audit pass first, then the audit pass.
        long nonAuditExpiry = expiryCaptor.getAllValues().get(0);
        long auditExpiry = expiryCaptor.getAllValues().get(1);
        // Non-audit files use the long (ten-day) TTL while audit files use the short TTL, so the non-audit
        // cutoff is further in the past. The gap equals the difference between the two TTLs.
        long expectedGapMillis = (npmVirtualCacheNonAuditItemTtlSeconds.getInt()
                - npmVirtualCacheItemTtlSeconds.getInt()) * 1000L;
        assertTrue(auditExpiry > nonAuditExpiry);
        assertTrue(Math.abs((auditExpiry - nonAuditExpiry) - expectedGapMillis) < 5_000L);
    }

    @Test
    public void testBatchCleanup() {
        homeStub.setProperty(npmVirtualCacheCleanupBatchSize, "3");
        mockResults(10, 3);
        long result = npmCacheCleaner.cleanupNonAuditCache(virtualRepoConfig);
        assertEquals(result, 10);
        verify(vfsQuery, times(5)).execute(3);
    }

    @Test
    public void testBatchCleanupWithErrorDuringCleaning() {
        homeStub.setProperty(npmVirtualCacheCleanupBatchSize, "3");
        mockResults(10, 3);
        when(repoService.undeploy(any(RepoPath.class))).thenAnswer(new Answer<StatusHolder>() {
            int invocationCount = 0;

            @Override
            public StatusHolder answer(InvocationOnMock invocationOnMock) {
                invocationCount++;
                BasicStatusHolder status = new BasicStatusHolder();
                if (invocationCount == 7) {
                    status.error("Failed to undeploy", new RuntimeException("AHH!"), log);
                }
                return status;
            }
        });
        long result = npmCacheCleaner.cleanupNonAuditCache(virtualRepoConfig);
        assertEquals(result, 6);
        verify(vfsQuery, times(3)).execute(3);
    }

    @Test
    public void testBatchCleanupOnlyOneBatchIncomplete() {
        homeStub.setProperty(npmVirtualCacheCleanupBatchSize, "3");
        mockResults(2, 3);
        long result = npmCacheCleaner.cleanupNonAuditCache(virtualRepoConfig);
        assertEquals(result, 2);
        verify(vfsQuery, times(2)).execute(3);
    }

    @Test
    public void testBatchCleanupOnlyOneBatchComplete() {
        homeStub.setProperty(npmVirtualCacheCleanupBatchSize, "3");
        mockResults(3, 3);
        long result = npmCacheCleaner.cleanupNonAuditCache(virtualRepoConfig);
        assertEquals(result, 3);
        verify(vfsQuery, times(2)).execute(3);
    }

    @Test
    public void testCleanUpWithInitialError() {
        homeStub.setProperty(npmVirtualCacheCleanupBatchSize, "3");
        BasicStatusHolder errorStatus = new BasicStatusHolder();
        errorStatus.error("Failed to undeploy", new RuntimeException("AHH!"), log);
        when(repoService.undeploy(any(RepoPath.class))).thenReturn(errorStatus);
        long result = npmCacheCleaner.cleanupNonAuditCache(virtualRepoConfig);
        assertEquals(result, 0);
    }

    // mockResults(4, 2) -> 2 queries will yield 2 results
    // mockResults(9, 2) -> 4 queries will yield 2 results. 5th query yields 1 result
    private void mockResults(int numToDelete, int batchSize) {
        when(queryResult.getCount()).thenAnswer(new Answer() {
            private int expetedInvocations =
                    1 + (numToDelete % batchSize == 0 ? numToDelete / batchSize : (numToDelete / batchSize) + 1);
            private int invocationCount = 0;

            @Override
            public Object answer(InvocationOnMock invocationOnMock) {
                invocationCount++;
                if (invocationCount == expetedInvocations) {
                    return 0L;
                } else if (invocationCount == expetedInvocations - 1 && expetedInvocations != 2) {
                    return (long) (numToDelete % batchSize);
                } else if (invocationCount == expetedInvocations - 1 && expetedInvocations == 2) {
                    return (long) numToDelete;
                } else {
                    return (long) batchSize;
                }
            }
        });
        when(queryResult.getAllRows()).thenAnswer(new Answer() {
            private int expetedInvocations =
                    1 + (numToDelete % batchSize == 0 ? numToDelete / batchSize : (numToDelete / batchSize) + 1);
            private int invocationCount = 0;

            @Override
            public Object answer(InvocationOnMock invocationOnMock) {
                invocationCount++;
                int numOfResults;
                if (invocationCount == expetedInvocations) {
                    numOfResults = 0;
                } else if (invocationCount == expetedInvocations - 1 && expetedInvocations != 2) {
                    numOfResults = (numToDelete % batchSize);
                } else if (invocationCount == expetedInvocations - 1 && expetedInvocations == 2) {
                    numOfResults = numToDelete;
                } else {
                    numOfResults = batchSize;
                }
                return generateRows(numOfResults);
            }

            private List<VfsQueryRow> generateRows(int numOfResults) {
                ArrayList<VfsQueryRow> results = new ArrayList<>();
                for (int i = 0; i < numOfResults; i++) {
                    results.add(vfsRow);
                }
                return results;
            }
        });
    }
}