package org.artifactory.repo.interceptor.config.projects;

import org.artifactory.common.ConstantValues;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RemoveRepoFromProjectConfigurationInterceptorTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ProjectsService projectsService;
    @InjectMocks
    private RemoveRepoFromProjectConfigurationInterceptor interceptor;
    private AutoCloseable mocks;

    @BeforeEach
    public void setup() {
        setStringSystemProperty(ConstantValues.removeRepositoryFlowV2Enabled, Boolean.TRUE.toString());
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
        getBound().getArtifactoryProperties().removeProperty(ConstantValues.removeRepositoryFlowV2Enabled);
    }

    @Test
    void interceptorIsNoopWhenV2FlowEnabled() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", Generic);
        RepoConfigChange deleteChange = RepoConfigChange.deleteOf(localRepoConfig);
        RepoConfigChanges repoConfigChanges = new RepoConfigChanges(deleteChange);

        interceptor.onBeforeSave(repoConfigChanges);

        verify(projectsService, never()).removeRepositoryFromCurrentProject(anyString());
    }
}