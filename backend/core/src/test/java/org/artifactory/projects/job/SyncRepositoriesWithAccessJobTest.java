package org.artifactory.projects.job;

import com.google.common.collect.Sets;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.projects.ProjectsService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.jfrog.access.common.ResourceType;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.quartz.JobExecutionContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
public class SyncRepositoriesWithAccessJobTest {

    @Mock
    private ArtifactoryApplicationContext artiApplicationContextMock;
    @Mock
    private JobExecutionContext jobExecutionContext;
    @Mock
    private RepositoryService repositoryServiceMock;
    @Mock
    private ProjectsService projectServiceMock;
    private MockitoSession mockitoSession;
    private SyncRepositoriesWithAccessJob syncRepositoriesWithAccessJob;

    @BeforeMethod
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .initMocks(this)
                .strictness(Strictness.STRICT_STUBS)
                .startMocking();

        when(artiApplicationContextMock.beanForType(RepositoryService.class)).thenReturn(repositoryServiceMock);
        when(artiApplicationContextMock.beanForType(ProjectsService.class)).thenReturn(projectServiceMock);
        ArtifactoryContextThreadBinder.bind(artiApplicationContextMock);
        syncRepositoriesWithAccessJob = new SyncRepositoriesWithAccessJob();
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void syncLocalRepos() {
        Set<String> localRepos = Sets.newHashSet("docker-local1", "docker-local2");
        when(repositoryServiceMock.getAllRepoKeys()).thenReturn(localRepos);
        syncRepositoriesWithAccessJob.onExecute(jobExecutionContext);
        verify(projectServiceMock, times(1)).syncProjectResources(ResourceType.REPO, localRepos);
    }

    @Test
    public void syncRemoteReposRemoveDashCache() {
        Set<String> repos = Sets.newHashSet("docker-local1", "docker-local2", "docker-remote");
        Set<String> reposWithDashCache = new HashSet<>(repos);
        reposWithDashCache.add("docker-remote-cache");
        when(repositoryServiceMock.getAllRepoKeys()).thenReturn(reposWithDashCache);
        syncRepositoriesWithAccessJob.onExecute(jobExecutionContext);
        verify(projectServiceMock, times(1)).syncProjectResources(ResourceType.REPO, repos);
    }
}