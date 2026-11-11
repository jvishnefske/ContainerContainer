package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildImportSettings;
import lombok.NonNull;
import com.jfrog.build.tx.BuildTxRunner;
import org.artifactory.common.status.MutableStatusHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class BuildServiceHybridMigrationTest {

    @Test
    void whenHybridMigrationThenBuildAndReposAreNotDeleted() {
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        BuildImportServiceForTest buildService = new BuildImportServiceForTest();
        BuildImportSettings importSettings = new BuildImportSettings(null, false, true, status);
        buildService.importFrom(importSettings);
        assertFalse(buildService.buildsAndReposAreDeleted);
    }

    @Test
    void whenStandardImportThenBuildAndReposAreDeleted() {
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        BuildImportServiceForTest buildService = new BuildImportServiceForTest();
        BuildImportSettings importSettings = new BuildImportSettings(null, false, false, status);
        buildService.importFrom(importSettings);
        assertTrue(buildService.buildsAndReposAreDeleted);
    }

    private static class BuildImportServiceForTest extends BuildImportService {

        private boolean buildsAndReposAreDeleted = false;

        public BuildImportServiceForTest() {
            super(
                    mock(BuildBridgeToArtifactory.class),
                    mock(BuildTxRunner.class),
                    mock(BuildCreationService.class),
                    mock(BuildDatabaseService.class),
                    mock(BuildRepoService.class)
            );
        }

        @Override
        public void deleteBuildsAndRepositories(@NonNull BuildImportSettings settings) {
            buildsAndReposAreDeleted = true;
        }

        @Override
        public void importBuildFiles(@NonNull BuildImportSettings settings) {
        }
    }
}