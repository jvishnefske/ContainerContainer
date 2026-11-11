package org.artifactory.rest.services.permissions;

import com.jfrog.build.acl.model.BuildNameAndRepo;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.release.bundle.ReleaseBundleService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.bundle.BundleNameAndRepo;
import org.artifactory.bundle.BundleType;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.security.BuildAcl;
import org.artifactory.security.BuildPermissionTarget;
import org.artifactory.security.PermissionTargetNaming;
import org.artifactory.security.ReleaseBundleAcl;
import org.artifactory.security.ReleaseBundlePermissionTarget;
import org.artifactory.security.RepoAcl;
import org.artifactory.security.RepoPermissionTarget;
import org.artifactory.security.permissions.PermissionTargetModel;
import org.artifactory.security.permissions.RepoPermissionTargetModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Oleksandr Legenkiy
 */
class RestSecurityRequestHandlerV2ImplTest {

    @Mock
    private BuildFacade buildFacade;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ReleaseBundleService releaseBundleService;
    @Mock
    private UserGroupService userGroupService;
    @Mock
    private CentralConfigService configService;
    @Mock
    private SecurityService securityService;
    @Mock
    private RepositoryService repositoryService;
    @InjectMocks
    private RestSecurityRequestHandlerV2Impl requestHandler;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testGetBuildsPerPatternsWithSpecialChars() {
        List<BuildNameAndRepo> builds = new ArrayList<>();
        builds.add(new BuildNameAndRepo("%SOME?/_Build!.", "someRepo"));
        builds.add(new BuildNameAndRepo("%S*ME?/_ot her%build!.", "someRepo"));
        when(buildFacade.getBuildsInfoInternally(any())).thenReturn(builds);

        List<String> includePatterns = Collections.singletonList("%S*ME?/_ot her%build!./**");
        List<String> excludePatterns = Collections.singletonList("%SOME?/_Build!./**");
        List<String> results = requestHandler.getBuildsPerPatterns(includePatterns, excludePatterns, "buildRepo");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("%S*ME?/_ot her%build!.", results.get(0));
    }

    @Test
    void testGetAllReposAcls() {
        RepoPermissionTarget rpt1 = mock(RepoPermissionTarget.class);
        when(rpt1.getName()).thenReturn("Anything");
        RepoAcl repoAcl1 = mock(RepoAcl.class);
        when(repoAcl1.getPermissionTarget()).thenReturn(rpt1);
        when(authorizationService.isInternalAcl(repoAcl1)).thenReturn(false);

        RepoPermissionTarget rpt2 = mock(RepoPermissionTarget.class);
        when(rpt2.getName()).thenReturn("Any Remote");
        RepoAcl repoAcl2 = mock(RepoAcl.class);
        when(repoAcl2.getPermissionTarget()).thenReturn(rpt2);
        when(authorizationService.isInternalAcl(repoAcl2)).thenReturn(false);

        RepoPermissionTarget rpt3 = mock(RepoPermissionTarget.class);
        when(rpt3.getName()).thenReturn("INTERNAL_proj:DEV:Project Admin:REPO");
        RepoAcl repoAcl3 = mock(RepoAcl.class);
        when(repoAcl3.getPermissionTarget()).thenReturn(rpt3);
        when(authorizationService.isInternalAcl(repoAcl3)).thenReturn(true);

        List<RepoAcl> repoAcls = List.of(repoAcl1, repoAcl2, repoAcl3);
        when(authorizationService.getAllRepoAcls(any())).thenReturn(repoAcls);
        Map<String, RepoAcl> allReposAcls = requestHandler.getAllReposAcls();
        assertEquals(2, allReposAcls.size());
        assertEquals(Set.of("Anything", "Any Remote"), allReposAcls.keySet());
    }

    @Test
    void testGetAllBuildsAcls() {
        BuildPermissionTarget bpt1 = mock(BuildPermissionTarget.class);
        when(bpt1.getName()).thenReturn("Anything");
        BuildAcl buildAcl1 = mock(BuildAcl.class);
        when(buildAcl1.getPermissionTarget()).thenReturn(bpt1);
        when(authorizationService.isInternalAcl(buildAcl1)).thenReturn(false);

        BuildPermissionTarget bpt2 = mock(BuildPermissionTarget.class);
        when(bpt2.getName()).thenReturn("Any Remote");
        BuildAcl buildAcl2 = mock(BuildAcl.class);
        when(buildAcl2.getPermissionTarget()).thenReturn(bpt2);
        when(authorizationService.isInternalAcl(buildAcl2)).thenReturn(false);

        BuildPermissionTarget bpt3 = mock(BuildPermissionTarget.class);
        when(bpt3.getName()).thenReturn("INTERNAL_proj:DEV:Project Admin:REPO");
        BuildAcl buildAcl3 = mock(BuildAcl.class);
        when(buildAcl3.getPermissionTarget()).thenReturn(bpt3);
        when(authorizationService.isInternalAcl(buildAcl3)).thenReturn(true);

        List<BuildAcl> buildAcls = List.of(buildAcl1, buildAcl2, buildAcl3);
        when(authorizationService.getAllBuildAcls(any())).thenReturn(buildAcls);
        Map<String, BuildAcl> allReposAcls = requestHandler.getAllBuildsAcls();
        assertEquals(2, allReposAcls.size());
        assertEquals(Set.of("Anything", "Any Remote"), allReposAcls.keySet());
    }

    @Test
    void testGetAllReleaseBundleAcls() {
        ReleaseBundlePermissionTarget rbpt1 = mock(ReleaseBundlePermissionTarget.class);
        when(rbpt1.getName()).thenReturn("Anything");
        ReleaseBundleAcl rbAcl1 = mock(ReleaseBundleAcl.class);
        when(rbAcl1.getPermissionTarget()).thenReturn(rbpt1);
        when(authorizationService.isInternalAcl(rbAcl1)).thenReturn(false);

        ReleaseBundlePermissionTarget rbpt2 = mock(ReleaseBundlePermissionTarget.class);
        when(rbpt2.getName()).thenReturn("Any Remote");
        ReleaseBundleAcl rbAcl2 = mock(ReleaseBundleAcl.class);
        when(rbAcl2.getPermissionTarget()).thenReturn(rbpt2);
        when(authorizationService.isInternalAcl(rbAcl2)).thenReturn(false);

        ReleaseBundlePermissionTarget rbpt3 = mock(ReleaseBundlePermissionTarget.class);
        when(rbpt3.getName()).thenReturn("INTERNAL_proj:DEV:Project Admin:REPO");
        ReleaseBundleAcl rbAcl3 = mock(ReleaseBundleAcl.class);
        when(rbAcl3.getPermissionTarget()).thenReturn(rbpt3);
        when(authorizationService.isInternalAcl(rbAcl3)).thenReturn(true);

        List<ReleaseBundleAcl> rbAcls = List.of(rbAcl1, rbAcl2, rbAcl3);
        when(authorizationService.getAllReleaseBundleAcls(any())).thenReturn(rbAcls);
        Map<String, ReleaseBundleAcl> allReposAcls = requestHandler.getAllReleaseBundleAcls();
        assertEquals(2, allReposAcls.size());
        assertEquals(Set.of("Anything", "Any Remote"), allReposAcls.keySet());
    }

    @Test
    void testGetReleaseBundlesByReposAndPatterns() {
        List<String> repos = List.of("release-bundle-repo");
        List<String> includePatterns = List.of("bundle*/**");
        List<String> excludePatterns = List.of("bundle-1*/**");
        BundleType bundleType = BundleType.SOURCE;

        when(releaseBundleService.getAllCompletedBundles(bundleType)).thenReturn(List.of(
                new BundleNameAndRepo("myb", "release-bundle-repo"),
                new BundleNameAndRepo("bundle-1", "release-bundle-repo"),
                new BundleNameAndRepo("bundle1", "release-bundle-repo"),
                new BundleNameAndRepo("bundle3", "release-bundle-repo"),
                new BundleNameAndRepo("bundle2", "wrong-bundle-repo")
        ));

        List<String> names =
                requestHandler.getReleaseBundlesByReposAndPatterns(repos, includePatterns, excludePatterns, bundleType);

        assertEquals(List.of("bundle1", "bundle3"), names);

    }

    @Test
    void createPermissionTarget_shouldThrowBadRequestException_whenRepoRepositoriesIsEmpty() {
        // Arrange
        String entityKey = "test-permission";
        PermissionTargetModel model = new PermissionTargetModel();
        model.setName(entityKey);

        RepoPermissionTargetModel repoModel = new RepoPermissionTargetModel();
        repoModel.setRepositories(new HashSet<>());
        repoModel.setIncludePatterns(List.of("**"));
        model.setRepo(repoModel);

        when(securityService.isAdmin()).thenReturn(true);
        when(authorizationService.getRepoAcl(anyString())).thenReturn(null);
        when(authorizationService.getBuildAcl(anyString())).thenReturn(null);
        when(authorizationService.getReleaseBundleAcl(anyString())).thenReturn(null);

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                requestHandler.createPermissionTarget(entityKey, model, PermissionTargetNaming.NAMING_DISPLAY)
        );

        assertEquals("Permission target must contain at least one repository.", exception.getMessage());
    }

    @Test
    void createPermissionTarget_shouldThrowBadRequestException_whenBuildRepositoriesIsEmpty() {
        // Arrange
        String entityKey = "test-permission";
        PermissionTargetModel model = new PermissionTargetModel();
        model.setName(entityKey);

        RepoPermissionTargetModel buildModel = new RepoPermissionTargetModel();
        buildModel.setRepositories(new HashSet<>());
        buildModel.setIncludePatterns(List.of("**"));
        model.setBuild(buildModel);

        when(securityService.isAdmin()).thenReturn(true);
        when(authorizationService.getRepoAcl(anyString())).thenReturn(null);
        when(authorizationService.getBuildAcl(anyString())).thenReturn(null);
        when(authorizationService.getReleaseBundleAcl(anyString())).thenReturn(null);

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                requestHandler.createPermissionTarget(entityKey, model, PermissionTargetNaming.NAMING_DISPLAY)
        );

        assertEquals("Permission target must contain at least one repository.", exception.getMessage());
    }

    @Test
    void updatePermissionTarget_shouldThrowBadRequestException_whenRepoRepositoriesIsEmpty() {
        // Arrange
        String entityKey = "test-permission";
        PermissionTargetModel model = new PermissionTargetModel();
        model.setName(entityKey);

        RepoPermissionTargetModel repoModel = new RepoPermissionTargetModel();
        repoModel.setRepositories(new HashSet<>());
        repoModel.setIncludePatterns(List.of("**"));
        model.setRepo(repoModel);

        RepoAcl existingRepoAcl = mock(RepoAcl.class);
        RepoPermissionTarget existingTarget = mock(RepoPermissionTarget.class);
        when(existingRepoAcl.getPermissionTarget()).thenReturn(existingTarget);
        when(existingTarget.getRepoKeys()).thenReturn(Set.of("existing-repo"));
        when(authorizationService.canManage(existingTarget)).thenReturn(true);

        when(authorizationService.getRepoAcl(entityKey)).thenReturn(existingRepoAcl);
        when(authorizationService.getBuildAcl(entityKey)).thenReturn(null);
        when(authorizationService.getReleaseBundleAcl(entityKey)).thenReturn(null);

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                requestHandler.updatePermissionTarget(entityKey, model, PermissionTargetNaming.NAMING_DISPLAY)
        );

        assertEquals("Permission target must contain at least one repository.", exception.getMessage());
    }

    @Test
    void updatePermissionTarget_shouldThrowBadRequestException_whenBuildRepositoriesIsEmpty() {
        // Arrange
        String entityKey = "test-permission";
        PermissionTargetModel model = new PermissionTargetModel();
        model.setName(entityKey);

        RepoPermissionTargetModel buildModel = new RepoPermissionTargetModel();
        buildModel.setRepositories(new HashSet<>());
        buildModel.setIncludePatterns(List.of("**"));
        model.setBuild(buildModel);

        BuildAcl existingBuildAcl = mock(BuildAcl.class);
        BuildPermissionTarget existingTarget = mock(BuildPermissionTarget.class);
        when(existingBuildAcl.getPermissionTarget()).thenReturn(existingTarget);
        when(existingTarget.getRepoKeys()).thenReturn(Set.of("artifactory-build-info"));
        when(authorizationService.canManage(existingTarget)).thenReturn(true);

        when(authorizationService.getRepoAcl(entityKey)).thenReturn(null);
        when(authorizationService.getBuildAcl(entityKey)).thenReturn(existingBuildAcl);
        when(authorizationService.getReleaseBundleAcl(entityKey)).thenReturn(null);

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                requestHandler.updatePermissionTarget(entityKey, model, PermissionTargetNaming.NAMING_DISPLAY)
        );

        assertEquals("Permission target must contain at least one repository.", exception.getMessage());
    }
}
