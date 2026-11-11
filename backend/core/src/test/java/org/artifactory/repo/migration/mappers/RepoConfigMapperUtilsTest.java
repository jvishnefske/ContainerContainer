package org.artifactory.repo.migration.mappers;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.releasebundle.ReleaseBundlesRepoDescriptor;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

import static org.testng.Assert.assertEquals;

public class RepoConfigMapperUtilsTest {

    @Test
    public void testCreateRDbRepoConfig() {
        ReleaseBundlesRepoDescriptor repoDescriptor = new ReleaseBundlesRepoDescriptor();
        repoDescriptor.setType(RepoType.Maven);
        repoDescriptor.setKey("local1");

        byte[] dbInfo = "dbInfo".getBytes(StandardCharsets.UTF_8);
        long createUpdateTime = System.currentTimeMillis();

        DbRepositoryConfig dbRepoConfig = RepoConfigMigrationMapperUtils.createDbRepoConfig(repoDescriptor,
                RepoDetailsType.RELEASE_BUNDLE, dbInfo, createUpdateTime);

        assertEquals(dbRepoConfig.getKey(), "local1");
        assertEquals(dbRepoConfig.getCreated(), createUpdateTime);
        assertEquals(dbRepoConfig.getModified(), createUpdateTime);
        assertEquals(dbRepoConfig.getType(), RepoDetailsType.RELEASE_BUNDLE.typeNameLowercase());
        assertEquals(dbRepoConfig.getRepositoryInfoBlob(), dbInfo);
        assertEquals(dbRepoConfig.getPackageType(), RepoType.Maven.getType());
        assertEquals(dbRepoConfig.getRevision(), 1);
    }
}