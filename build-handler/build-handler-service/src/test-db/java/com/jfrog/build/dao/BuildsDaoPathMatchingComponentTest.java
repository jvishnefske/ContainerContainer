package com.jfrog.build.dao;

import com.jfrog.build.model.api.ModuleArtifact;
import com.jfrog.build.test.database.base.DatabaseTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildsDaoPathMatchingComponentTest extends DatabaseTestBase {

    @Autowired
    private BuildsDao buildsDao;

    @BeforeEach
    void setup() {
        importSql("/sql/dml/build-path-matching.sql");
    }

    @Test
    void getModuleArtifact_whenNoPromotion_thenTakeFirstSuitableArtifactNode() throws SQLException {
        List<ModuleArtifact> artifacts = buildsDao.getModuleArtifact(
                "build-without-promotions",
                "1",
                "1",
                "module-1",
                BUILDINFO_REPO_KEY,
                true
        );
        assertEquals(1, artifacts.size());
        String repoKey = artifacts.getFirst().getRepoKey();
        assertTrue(repoKey.equals("repo-01") || repoKey.equals("repo-02"));
    }

    @Test
    void getModuleArtifact_whenPromotionExists_thenTakeArtifactNodeFromPromotionRepo() throws SQLException {
        List<ModuleArtifact> artifacts = buildsDao.getModuleArtifact(
                "build-with-promotions",
                "1",
                "1",
                "module-2",
                BUILDINFO_REPO_KEY,
                true
        );
        assertEquals(1, artifacts.size());
        assertEquals("repo-02", artifacts.getFirst().getRepoKey());
    }
}
