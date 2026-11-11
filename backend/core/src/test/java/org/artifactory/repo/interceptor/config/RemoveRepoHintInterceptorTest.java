package org.artifactory.repo.interceptor.config;

import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.repo.RepoConfigChange;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.artifactory.event.EventHintType.RemoveRepo;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Tamar Rosen
 */
public class RemoveRepoHintInterceptorTest {
    private ArtifactoryTracer tracer;
    private RemoveRepoHintInterceptor interceptor;

    @BeforeMethod
    public void setup() {
        setupTracer();
        interceptor = new RemoveRepoHintInterceptor(tracer);
    }

    @Test
    public void testRemoveRepoOnBeforeSaveSetHintRemoveRepo() {
        assertFalse(EventLogHintManager.contains(RemoveRepo));
        interceptor.onBeforeSave(
                Map.of(LOCAL, List.of(new RepoConfigChange("repo1", REPO_DELETE, null,
                        LocalRepoConfig.create("repoKey", RepoType.Generic)))));
        assertTrue(EventLogHintManager.contains(RemoveRepo));
    }

    private void setupTracer() {
        tracer = mock(ArtifactoryTracer.class);
    }
}