package org.artifactory.rest.common.service.admin.xray;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.addon.xray.XrayRepo;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.model.xray.XrayRepoModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.exception.ForbiddenWebAppException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.projects.ProjectsService.PROJECT_KEY;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Yevdo Abramov
 * Created on 03/03/2021
 */
@Test
public class UpdateXrayIndexReposTest {

    @Mock
    private SecurityService securityService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private RepositoryService repoService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private XrayAddon xrayAddon;

    private AutoCloseable closeableMocks;

    private UpdateXrayIndexRepos updateXrayIndexRepos;

    @BeforeMethod
    public void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);
        updateXrayIndexRepos = new UpdateXrayIndexRepos(projectsService, repoService, addonsManager, securityService);

        doReturn(xrayAddon).when(addonsManager).addonByType(XrayAddon.class);
        when(securityService.isProjectAdmin(anyString())).thenReturn(true);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        closeableMocks.close();
    }

    @Test(expectedExceptions = ForbiddenWebAppException.class,
            expectedExceptionsMessageRegExp = ".*Xray configuration is not enabled.*")
    public void testExecuteXrayDisabled() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse<Void> response = new ArtifactoryRestResponse<>();
        try {
            updateXrayIndexRepos.execute(request, response);
        } finally {
            verify(xrayAddon).isXrayEnabled();
            verifyNoMoreInteractions(projectsService, repoService, xrayAddon);
        }
    }

    @Test
    public void testExecuteWhenReposListIsEmpty() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse<Void> response = new ArtifactoryRestResponse<>();
        when(request.getModels()).thenReturn(Collections.emptyList());
        when(xrayAddon.isXrayEnabled()).thenReturn(true);

        try {
            updateXrayIndexRepos.execute(request, response);
        } finally {
            verify(xrayAddon).isXrayEnabled();
            verify(xrayAddon).updateXraySelectedIndexedRepos(Collections.emptySet(), ProjectsService.DEFAULT_PROJECT);
            verifyNoMoreInteractions(projectsService, repoService, xrayAddon);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Some of the repositories do not belong to the provided project")
    public void testExecuteWhenReposFromAnotherProject() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse<Void> response = new ArtifactoryRestResponse<>();
        List<XrayRepo> repos = List.of(
                new XrayRepoModel("repoProjectA", "generic", "local", 90),
                new XrayRepoModel("repoProjectB", "generic", "local", 90)
        );
        when(request.getModels()).thenReturn(repos);
        when(request.getQueryParamByKey(PROJECT_KEY)).thenReturn("proj");
        when(xrayAddon.isXrayEnabled()).thenReturn(true);
        LocalRepoDescriptor repoDescriptor = new LocalRepoDescriptor();
        when(repoService.repoDescriptorByKey(anyString())).thenReturn(repoDescriptor);
        when(projectsService.getProjectRepositoriesKeys(eq("proj"), anyBoolean()))
                .thenReturn(Set.of("repoProjectA", "repoProjectA2"));

        try {
            updateXrayIndexRepos.execute(request, response);
        } finally {
            verify(xrayAddon).isXrayEnabled();
            verify(repoService, times(2)).repoDescriptorByKey(anyString());
            verify(projectsService).getProjectRepositoriesKeys("proj", false);
            verifyNoMoreInteractions(projectsService, repoService, xrayAddon);
        }
    }

    @Test
    public void testExecuteWhenProjectValidationPassed() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse<Void> response = new ArtifactoryRestResponse<>();
        List<XrayRepo> repos = List.of(
                new XrayRepoModel("repoProjectA", "generic", "local", 90),
                new XrayRepoModel("repoProjectA2", "generic", "local", 90)
        );
        when(request.getModels()).thenReturn(repos);
        when(request.getQueryParamByKey(PROJECT_KEY)).thenReturn("proj");
        when(xrayAddon.isXrayEnabled()).thenReturn(true);
        LocalRepoDescriptor repoDescriptor = new LocalRepoDescriptor();
        when(repoService.repoDescriptorByKey(anyString())).thenReturn(repoDescriptor);
        when(projectsService.getProjectRepositoriesKeys(eq("proj"), anyBoolean()))
                .thenReturn(Set.of("repoProjectA", "repoProjectA2"));

        try {
            updateXrayIndexRepos.execute(request, response);
        } finally {
            verify(xrayAddon).isXrayEnabled();
            verify(repoService, times(2)).repoDescriptorByKey(anyString());
            verify(projectsService).getProjectRepositoriesKeys("proj", false);
            verify(xrayAddon).updateXraySelectedIndexedRepos(repos.stream()
                    .map(XrayRepo::getName)
                    .collect(Collectors.toSet()), "proj");
            verifyNoMoreInteractions(projectsService, repoService, xrayAddon);
        }
    }

    @Test
    public void testExecuteWhenDefaultProject() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse<Void> response = new ArtifactoryRestResponse<>();
        List<XrayRepo> repos = List.of(
                new XrayRepoModel("repoProjectA", "generic", "local", 90),
                new XrayRepoModel("repoProjectB", "generic", "local", 90)
        );
        when(request.getModels()).thenReturn(repos);
        when(xrayAddon.isXrayEnabled()).thenReturn(true);
        LocalRepoDescriptor repoDescriptor = new LocalRepoDescriptor();
        when(repoService.repoDescriptorByKey(anyString())).thenReturn(repoDescriptor);

        try {
            updateXrayIndexRepos.execute(request, response);
        } finally {
            verify(xrayAddon).isXrayEnabled();
            verify(repoService, times(2)).repoDescriptorByKey(anyString());
            verify(xrayAddon).updateXraySelectedIndexedRepos(repos.stream()
                    .map(XrayRepo::getName)
                    .collect(Collectors.toSet()), ProjectsService.DEFAULT_PROJECT);
            verifyNoMoreInteractions(projectsService, repoService, xrayAddon);
        }
    }
}