package org.artifactory.storage.db.repo.itest;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.fs.session.SqlStorageSessionFactory;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.service.RepositoryConfigStorageService;
import org.artifactory.storage.db.spring.DbTransactionManager;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.testng.Assert.assertNotNull;

/**
 * @author Uriah Levy
 */
public class RepositoryConfigStorageServiceDBTest extends DbBaseTest {
    @Autowired
    private RepositoryConfigStorageService storageService;
    @Autowired
    private ArtifactoryJdbcHelper jdbcHelper;

    @AfterMethod
    public void tearDown() {
        storageService.deleteAll();
    }

    @Test
    public void testSeparateTransactionsFlushOfInsertUpdateAndDelete() {
        DbRepositoryConfig repoAConfig = federatedRepo("repo-a", RepoType.Cargo.getType());

        storageService.flushBulkChanges(Set.of(repoAConfig), Set.of(), Set.of(), Set.of(), Set.of());

        DbRepositoryConfig repoFromDb = storageService.findRepositoryByKey("repo-a");
        assertNotNull(repoFromDb);
        Assert.assertEquals(RepoType.Cargo.getType(), repoFromDb.getPackageType());

        DbRepositoryConfig updateMe = federatedRepo("repo-a", RepoType.Maven.getType());

        // Should fail with Duplicate key constraint
        try {
            storageService.flushBulkChanges(Set.of(repoAConfig), Set.of(updateMe), Set.of(), Set.of(), Set.of());
            Assert.fail("Should have caught a Duplicate Key exception");
        } catch (StorageException e) {
            // Ignore
        }

        repoFromDb = storageService.findRepositoryByKey("repo-a");
        assertNotNull(repoFromDb);
        // The Update didn't do anything
        Assert.assertEquals(RepoType.Cargo.getType(), repoFromDb.getPackageType());
    }

    @Test
    public void testFlushBulkChanges() {
        DbRepositoryConfig repoToInsert = federatedRepo("repo-b", RepoType.Npm.getType());

        DbRepositoryConfig repoToUpdate = federatedRepo(repoToInsert.getKey(), RepoType.Generic.getType());

        storageService.flushBulkChanges(Set.of(repoToInsert), Set.of(repoToUpdate), Set.of(), Set.of(), Set.of());

        DbRepositoryConfig repoFromDb = storageService.findRepositoryByKey(repoToInsert.getKey());
        assertNotNull(repoFromDb);
        Assert.assertEquals(RepoType.Generic.getType(), repoFromDb.getPackageType());

        storageService.flushBulkChanges(Set.of(), Set.of(), Set.of(repoToInsert.getKey()), Set.of(), Set.of());
        Assert.assertNull(storageService.findRepositoryByKey(repoToInsert.getKey()));
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp =
            ".*Active transaction is not allowed here.*")
    public void assertNoTransactionDuringFlushRepoConfigChanges() {
        DbTransactionManager txManager =
                new DbTransactionManager(jdbcHelper.getDataSource(), new SqlStorageSessionFactory());
        TransactionStatus transactionStatus = null;
        try {
            transactionStatus = startTransaction(txManager);
            storageService.flushBulkChanges(Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        } finally {
            assertNotNull(transactionStatus);
            txManager.rollback(transactionStatus);
        }
    }

    @Test(enabled = false, invocationCount = 20, description = "This test reproduces a deadlock. In order for it to " +
            "work, the no-tx enforcement inside RepositoryConfigStorageServiceImpl#flushBulkChanges needs to be " +
            "bypassed too")
    public void testConcurrentBulkUpdate() throws ExecutionException, InterruptedException {
        DbRepositoryConfig one = federatedRepo("repo-a", RepoType.Npm.getType());

        DbRepositoryConfig two = federatedRepo("repo-b", RepoType.Generic.getType());

        DbRepositoryConfig three = federatedRepo("repo-c", RepoType.Generic.getType());

        DbRepositoryConfig four = federatedRepo("repo-d", RepoType.Generic.getType());

        DbRepositoryConfig five = federatedRepo("repo-e", RepoType.Generic.getType());


        Set<DbRepositoryConfig> firstSet = new LinkedHashSet<>(List.of(one, two, three, four, five));
        Set<DbRepositoryConfig> secondSet = new LinkedHashSet<>(List.of(five, four, three, two, one));

        storageService.flushBulkChanges(firstSet, Set.of(), Set.of(), Set.of(), Set.of());

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();
        DbTransactionManager txManager =
                new DbTransactionManager(jdbcHelper.getDataSource(), new SqlStorageSessionFactory());
        for (int i = 0; i < 10; i++) {
            int finalI = i;
            Future<?> submission = executorService.submit(() -> {
                TransactionStatus transactionStatus = startTransaction(txManager);
                if (finalI % 2 == 0) {
                    storageService.flushBulkChanges(Set.of(), firstSet, Set.of(), Set.of(), Set.of());
                } else {
                    storageService.flushBulkChanges(Set.of(), secondSet, Set.of(), Set.of(), Set.of());
                }
                txManager.commit(transactionStatus);
            });
            futures.add(submission);
        }

        for (Future<?> future : futures) {
            future.get();
        }
    }

    // repo_type_v2 is irrelevant to these storage-service tests; production forward-fills it, so it stays null here.
    private static DbRepositoryConfig federatedRepo(String key, String packageType) {
        return new DbRepositoryConfig(key, RepoDetailsType.FEDERATED.typeName(), null, packageType,
                1L, 1L, 1L, new byte[] {});
    }

    private TransactionStatus startTransaction(AbstractPlatformTransactionManager txManager) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("StatsTransaction");
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return txManager.getTransaction(def);
    }
}
