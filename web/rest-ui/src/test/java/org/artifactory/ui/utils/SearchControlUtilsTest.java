package org.artifactory.ui.utils;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.search.SearchControlsBase;
import org.artifactory.api.search.artifact.ArtifactSearchControls;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.projects.ProjectsService;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.search.AqlUISearchModel;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.search.AqlUiSearchControls;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Oleksandr Legenkiy
 */
@Test
public class SearchControlUtilsTest {

    private static final String TEST_PROJ_KEY = "testProject";
    private static final String REPO_ID = "repo";

    @BeforeMethod
    public void setup() {
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties artifactProperties = mock(ArtifactorySystemProperties.class);
        when(artifactProperties.getLongProperty(ConstantValues.searchInProjectMaxReposToProcess)).thenReturn(10L);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactProperties);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterMethod
    public void unbind() {
        ArtifactoryHome.unbind();
    }

    public void testNarrowSearchToProject_noProject() {
        ProjectsService projectsServiceMock = mock(ProjectsService.class);
        SearchControlsBase searchControls = new ArtifactSearchControls();
        List<String> selectedRepos = Collections.singletonList("someRepo");
        searchControls.setSelectedRepoForSearch(selectedRepos);
        // User has selected a repo for search.
        assertProjectsLimitationsNotApplied(searchControls, "", projectsServiceMock, selectedRepos, 0);
        assertProjectsLimitationsNotApplied(searchControls, "default", projectsServiceMock, selectedRepos, 0);
        assertProjectsLimitationsNotApplied(searchControls, "    ", projectsServiceMock, selectedRepos, 0);
        assertProjectsLimitationsNotApplied(searchControls, null, projectsServiceMock, selectedRepos, 0);

        // No selected repos and no project.
        selectedRepos = Collections.emptyList();
        searchControls.setSelectedRepoForSearch(selectedRepos);
        assertProjectsLimitationsNotApplied(searchControls, "", projectsServiceMock, selectedRepos, 0);
        assertProjectsLimitationsNotApplied(searchControls, "default", projectsServiceMock, selectedRepos, 0);
        assertProjectsLimitationsNotApplied(searchControls, "    ", projectsServiceMock, selectedRepos, 0);
        assertProjectsLimitationsNotApplied(searchControls, null, projectsServiceMock, selectedRepos, 0);
    }

    public void testNarrowSearchToProject_noReposSelected() {
        ProjectsService projectsServiceMock = mock(ProjectsService.class);
        SearchControlsBase searchControls = new ArtifactSearchControls();
        // No repos selected by user.
        searchControls.setSelectedRepoForSearch(Collections.emptyList());
        // Project has more repos than the limit.
        Set<String> projectRepos = createProjRepos(11);
        when(projectsServiceMock.getProjectRepositoriesKeys(TEST_PROJ_KEY, false)).thenReturn(projectRepos);
        SearchControlUtils.narrowSearchToProjectRepos(searchControls, TEST_PROJ_KEY, projectsServiceMock,
                createRepoServiceMock(11));
        assertNotNull(searchControls.getSelectedRepoForSearch());
        assertEquals(searchControls.getSelectedRepoForSearch().size(), 10);
        assertTrue(projectRepos.containsAll(searchControls.getSelectedRepoForSearch()));
        assertFalse(searchControls.isSearchShouldBeSkipped());
        assertTrue(searchControls.isLimitSearchResultsForProjects());

        // Project has less repos than the limit.
        searchControls.setSelectedRepoForSearch(Collections.emptyList());
        projectRepos = createProjRepos(5);
        when(projectsServiceMock.getProjectRepositoriesKeys(TEST_PROJ_KEY, false)).thenReturn(projectRepos);
        SearchControlUtils.narrowSearchToProjectRepos(searchControls, TEST_PROJ_KEY, projectsServiceMock,
                createRepoServiceMock(5));
        assertNotNull(searchControls.getSelectedRepoForSearch());
        assertEquals(searchControls.getSelectedRepoForSearch().size(), 5);
        assertTrue(projectRepos.containsAll(searchControls.getSelectedRepoForSearch()));
        assertFalse(searchControls.isSearchShouldBeSkipped());
        assertFalse(searchControls.isLimitSearchResultsForProjects());
    }

    public void testNarrowSearchToProject_reposSelectedLessThanLimit() {
        ProjectsService projectsServiceMock = mock(ProjectsService.class);
        SearchControlsBase searchControls = new ArtifactSearchControls();
        List<String> userSelectedRepos = new ArrayList<>(5);
        userSelectedRepos.add("userRepo1");
        userSelectedRepos.add("projRepo19"); // this one belongs to the test project
        userSelectedRepos.add("userRepo2");
        userSelectedRepos.add("projRepo2-cache"); // belongs to project. It's remote, so is sent with -cache from ui.
        userSelectedRepos.add("userRepo4");
        searchControls.setSelectedRepoForSearch(userSelectedRepos);
        Set<String> projectRepos = createProjRepos(20);
        when(projectsServiceMock.getProjectRepositoriesKeys(TEST_PROJ_KEY, false)).thenReturn(projectRepos);
        // Only the selected repos that belong to the test project should be left.
        SearchControlUtils.narrowSearchToProjectRepos(searchControls, TEST_PROJ_KEY, projectsServiceMock,
                createRepoServiceMock(20));
        assertNotNull(searchControls.getSelectedRepoForSearch());
        assertEquals(searchControls.getSelectedRepoForSearch().size(), 2);
        assertTrue(searchControls.getSelectedRepoForSearch().contains("projRepo19"));
        // "projRepo2-cache" should be kept with -cache even though the project repos list has it without -cache.
        assertTrue(searchControls.getSelectedRepoForSearch().contains("projRepo2-cache"));
        assertFalse(searchControls.isSearchShouldBeSkipped());
        assertTrue(searchControls.isLimitSearchResultsForProjects());
    }

    public void testNarrowSearchToProject_reposSelectedMoreThanLimit() {
        ProjectsService projectsServiceMock = mock(ProjectsService.class);
        SearchControlsBase searchControls = new ArtifactSearchControls();
        // A user has selected 15 repos, 12 of them belong to the project, only 10 of those 12 should be left.
        List<String> userSelectedRepos = new ArrayList<>(15);
        userSelectedRepos.add("userRepo1"); // this one does not belong to the test project
        userSelectedRepos.add("projRepo19");
        userSelectedRepos.add("userRepo2"); // this one does not belong to the test project
        userSelectedRepos.add("projRepo2");
        userSelectedRepos.add("userRepo4"); // this one does not belong to the test project
        userSelectedRepos.add("projRepo6");
        userSelectedRepos.add("projRepo7");
        userSelectedRepos.add("projRepo8");
        userSelectedRepos.add("projRepo9");
        userSelectedRepos.add("projRepo10");
        userSelectedRepos.add("projRepo11");
        userSelectedRepos.add("projRepo12");
        userSelectedRepos.add("projRepo13");
        userSelectedRepos.add("projRepo14");
        userSelectedRepos.add("projRepo15");
        searchControls.setSelectedRepoForSearch(userSelectedRepos);
        Set<String> projectRepos = createProjRepos(20);
        when(projectsServiceMock.getProjectRepositoriesKeys(TEST_PROJ_KEY, false)).thenReturn(projectRepos);
        // Only the selected repos that belong to the test project should be left.
        SearchControlUtils.narrowSearchToProjectRepos(searchControls, TEST_PROJ_KEY, projectsServiceMock,
                createRepoServiceMock(20));
        assertNotNull(searchControls.getSelectedRepoForSearch());
        assertEquals(searchControls.getSelectedRepoForSearch().size(), 10);
        assertTrue(projectRepos.containsAll(searchControls.getSelectedRepoForSearch()));
        assertFalse(searchControls.isSearchShouldBeSkipped());
        assertTrue(searchControls.isLimitSearchResultsForProjects());

        // Test a case when proj has no repos. The controls should have no selected repos and a
        // "searchShouldBeSkipped" flag should be true
        when(projectsServiceMock.getProjectRepositoriesKeys(TEST_PROJ_KEY, false)).thenReturn(
                Collections.emptySet());
        SearchControlUtils.narrowSearchToProjectRepos(searchControls, TEST_PROJ_KEY, projectsServiceMock,
                createRepoServiceMock(20));
        assertNotNull(searchControls.getSelectedRepoForSearch());
        assertTrue(searchControls.getSelectedRepoForSearch().isEmpty());
        assertTrue(searchControls.isSearchShouldBeSkipped());
        assertTrue(searchControls.isLimitSearchResultsForProjects());
    }

    public void testNarrowSearchToProjectRepos_localCacheRepo() {
        ProjectsService projectsServiceMock = mock(ProjectsService.class);
        SearchControlsBase searchControls = new ArtifactSearchControls();
        List<String> userSelectedRepos = Collections.emptyList();
        searchControls.setSelectedRepoForSearch(userSelectedRepos);
        Set<String> projectRepos = createProjRepos(3);
        when(projectsServiceMock.getProjectRepositoriesKeys(TEST_PROJ_KEY, false)).thenReturn(projectRepos);

        RepositoryService repoServiceMock = mock(RepositoryService.class);
        LocalRepoDescriptor localDescriptorMock = new LocalRepoDescriptor();
        localDescriptorMock.setKey("projRepo3");
        LocalCacheRepoDescriptor localCacheDescriptorMock = new LocalCacheRepoDescriptor();
        localCacheDescriptorMock.setKey("projRepo2-cache");

        // remote repo with no cache
        when(repoServiceMock.localFederatedOrCachedRepoDescriptorByKey("projRepo1")).thenReturn(null);
        // remote repo with cache
        when(repoServiceMock.localFederatedOrCachedRepoDescriptorByKey("projRepo2"))
                .thenReturn(localCacheDescriptorMock);
        // local repo
        when(repoServiceMock.localFederatedOrCachedRepoDescriptorByKey("projRepo3")).thenReturn(localDescriptorMock);

        SearchControlUtils
                .narrowSearchToProjectRepos(searchControls, TEST_PROJ_KEY, projectsServiceMock, repoServiceMock);

        assertNotNull(searchControls.getSelectedRepoForSearch());
        // -cache suffix should be added.
        assertTrue(searchControls.getSelectedRepoForSearch().contains("projRepo2-cache"));
        // a remote repo (from project) that has no cache should be excluded from the search.
        assertFalse(searchControls.getSelectedRepoForSearch().contains("projRepo1"));
        // a simple local repo should be present.
        assertTrue(searchControls.getSelectedRepoForSearch().contains("projRepo3"));
        assertFalse(searchControls.isSearchShouldBeSkipped());
        assertFalse(searchControls.isLimitSearchResultsForProjects());
    }

    public void testNarrowAqlSearchToProject_noProject() {
        ProjectsService projectsServiceMock = mock(ProjectsService.class);
        List<AqlUISearchModel> searchModels = new ArrayList<>(1);
        searchModels
                .add(new AqlUISearchModel(REPO_ID, AqlComparatorEnum.equals, Collections.singletonList("someRepo1")));
        // User has selected a repo for search.
        assertProjectsLimitationsNotApplied(searchModels, "", projectsServiceMock, 0);
        assertProjectsLimitationsNotApplied(searchModels, "default", projectsServiceMock, 0);
        assertProjectsLimitationsNotApplied(searchModels, "    ", projectsServiceMock, 0);
        assertProjectsLimitationsNotApplied(searchModels, null, projectsServiceMock, 0);

        // No selected repos and no project.
        searchModels = Collections.emptyList();
        assertProjectsLimitationsNotApplied(searchModels, "", projectsServiceMock, 0);
        assertProjectsLimitationsNotApplied(searchModels, "default", projectsServiceMock, 0);
        assertProjectsLimitationsNotApplied(searchModels, "    ", projectsServiceMock, 0);
        assertProjectsLimitationsNotApplied(searchModels, null, projectsServiceMock, 0);
    }

    public void testNarrowAqlSearchToProject_noReposSelected() {
        ProjectsService projectsServiceMock = mock(ProjectsService.class);
        List<AqlUISearchModel> searchModels = new ArrayList<>(1);
        // No repos selected by user.
        searchModels.add(new AqlUISearchModel(REPO_ID, AqlComparatorEnum.equals, Collections.emptyList()));
        // Project has more repos than the limit.
        Set<String> projectRepos = createProjRepos(11);
        when(projectsServiceMock.getProjectRepositoriesKeys(TEST_PROJ_KEY, false)).thenReturn(projectRepos);
        AqlUiSearchControls searchControls =
                SearchControlUtils.narrowAqlSearchToProjectRepos(searchModels, TEST_PROJ_KEY, projectsServiceMock,
                        createRepoServiceMock(11));
        List<String> resultRepos = getRepos(searchControls.getSearchModels());
        assertNotNull(resultRepos);
        assertEquals(resultRepos.size(), 10);
        assertTrue(projectRepos.containsAll(resultRepos));
        assertFalse(searchControls.isSearchShouldBeSkipped());
        assertTrue(searchControls.isLimitSearchResultsForProjects());

        // Project has less repos than the limit.
        projectRepos = createProjRepos(5);
        searchModels.clear();
        searchModels.add(new AqlUISearchModel(REPO_ID, AqlComparatorEnum.equals, Collections.emptyList()));
        when(projectsServiceMock.getProjectRepositoriesKeys(TEST_PROJ_KEY, false)).thenReturn(projectRepos);
        searchControls =
                SearchControlUtils.narrowAqlSearchToProjectRepos(searchModels, TEST_PROJ_KEY, projectsServiceMock,
                        createRepoServiceMock(5));
        resultRepos = getRepos(searchControls.getSearchModels());
        assertNotNull(resultRepos);
        assertEquals(resultRepos.size(), 5);
        assertTrue(projectRepos.containsAll(resultRepos));
        assertFalse(searchControls.isSearchShouldBeSkipped());
        assertFalse(searchControls.isLimitSearchResultsForProjects());
    }

    public void testNarrowAqlSearchToProject_reposSelectedLessThanLimit() {
        ProjectsService projectsServiceMock = mock(ProjectsService.class);
        List<String> userSelectedRepos = new ArrayList<>(5);
        userSelectedRepos.add("userRepo1");
        userSelectedRepos.add("projRepo19"); // this one belongs to the test project
        userSelectedRepos.add("userRepo2");
        userSelectedRepos.add("projRepo2-cache"); // belongs to project. It's remote, so is sent with -cache from ui.
        userSelectedRepos.add("userRepo4");
        List<AqlUISearchModel> searchModels = new ArrayList<>(1);
        searchModels.add(new AqlUISearchModel(REPO_ID, AqlComparatorEnum.equals, userSelectedRepos));
        Set<String> projectRepos = createProjRepos(20);
        when(projectsServiceMock.getProjectRepositoriesKeys(TEST_PROJ_KEY, false)).thenReturn(projectRepos);
        // Only the selected repos that belong to the test project should be left.
        AqlUiSearchControls searchControls =
                SearchControlUtils.narrowAqlSearchToProjectRepos(searchModels, TEST_PROJ_KEY, projectsServiceMock,
                        createRepoServiceMock(20));
        List<String> resultRepos = getRepos(searchControls.getSearchModels());
        assertNotNull(resultRepos);
        assertEquals(resultRepos.size(), 2);
        assertTrue(resultRepos.contains("projRepo19"));
        // "projRepo2-cache" should be kept with -cache even though the project repos list has it without -cache.
        assertTrue(resultRepos.contains("projRepo2-cache"));
        assertFalse(searchControls.isSearchShouldBeSkipped());
        assertTrue(searchControls.isLimitSearchResultsForProjects());
    }

    public void testNarrowAqlSearchToProject_reposSelectedMoreThanLimit() {
        ProjectsService projectsServiceMock = mock(ProjectsService.class);
        // A user has selected 15 repos, 12 of them belong to the project, only 10 of those 12 should be left.
        List<String> userSelectedRepos = new ArrayList<>(15);
        userSelectedRepos.add("userRepo1"); // this one does not belong to the test project
        userSelectedRepos.add("projRepo19");
        userSelectedRepos.add("userRepo2"); // this one does not belong to the test project
        userSelectedRepos.add("projRepo2");
        userSelectedRepos.add("userRepo4"); // this one does not belong to the test project
        userSelectedRepos.add("projRepo6");
        userSelectedRepos.add("projRepo7");
        userSelectedRepos.add("projRepo8");
        userSelectedRepos.add("projRepo9");
        userSelectedRepos.add("projRepo10");
        userSelectedRepos.add("projRepo11");
        userSelectedRepos.add("projRepo12");
        userSelectedRepos.add("projRepo13");
        userSelectedRepos.add("projRepo14");
        userSelectedRepos.add("projRepo15");
        List<AqlUISearchModel> searchModels = new ArrayList<>(1);
        searchModels.add(new AqlUISearchModel(REPO_ID, AqlComparatorEnum.equals, userSelectedRepos));
        Set<String> projectRepos = createProjRepos(20);
        when(projectsServiceMock.getProjectRepositoriesKeys(TEST_PROJ_KEY, false)).thenReturn(projectRepos);
        // Only the selected repos that belong to the test project should be left.
        AqlUiSearchControls searchControls =
                SearchControlUtils.narrowAqlSearchToProjectRepos(searchModels, TEST_PROJ_KEY, projectsServiceMock,
                        createRepoServiceMock(20));
        List<String> resultRepos = getRepos(searchControls.getSearchModels());
        assertNotNull(resultRepos);
        assertEquals(resultRepos.size(), 10);
        assertTrue(projectRepos.containsAll(resultRepos));
        assertFalse(searchControls.isSearchShouldBeSkipped());
        assertTrue(searchControls.isLimitSearchResultsForProjects());

        // Test a case when proj has no repos. The controls should have no selected repos and a
        // "searchShouldBeSkipped" flag should be true
        when(projectsServiceMock.getProjectRepositoriesKeys(TEST_PROJ_KEY, false)).thenReturn(
                Collections.emptySet());
        searchControls =
                SearchControlUtils.narrowAqlSearchToProjectRepos(searchModels, TEST_PROJ_KEY, projectsServiceMock,
                        createRepoServiceMock(20));
        resultRepos = getRepos(searchControls.getSearchModels());
        assertNotNull(resultRepos);
        assertTrue(resultRepos.isEmpty());
        assertTrue(searchControls.isSearchShouldBeSkipped());
        assertTrue(searchControls.isLimitSearchResultsForProjects());
    }

    private Set<String> createProjRepos(int size) {
        Set<String> result = new HashSet<>(size);
        for (int i = 1; i <= size; i++) {
            result.add("projRepo" + i);
        }
        return result;
    }

    private void assertProjectsLimitationsNotApplied(SearchControlsBase searchControls, String projectKey,
            ProjectsService service, List<String> selectedRepos, int projReposNumber) {
        SearchControlUtils.narrowSearchToProjectRepos(searchControls, projectKey, service,
                createRepoServiceMock(projReposNumber));
        assertEquals(searchControls.getSelectedRepoForSearch(), selectedRepos);
        assertFalse(searchControls.isLimitSearchResultsForProjects());
        assertFalse(searchControls.isSearchShouldBeSkipped());
    }

    private void assertProjectsLimitationsNotApplied(List<AqlUISearchModel> searchModels, String projectKey,
            ProjectsService service, int projReposNumber) {
        AqlUiSearchControls searchControls =
                SearchControlUtils.narrowAqlSearchToProjectRepos(searchModels, projectKey, service,
                        createRepoServiceMock(projReposNumber));
        assertEquals(getRepos(searchControls.getSearchModels()), getRepos(searchModels));
        assertFalse(searchControls.isLimitSearchResultsForProjects());
        assertFalse(searchControls.isSearchShouldBeSkipped());
    }

    private List<String> getRepos(List<AqlUISearchModel> searchModels) {
        return searchModels.stream()
                .filter(item -> REPO_ID.equals(item.getId())).findFirst().map(AqlUISearchModel::getValues)
                .orElse(Collections.emptyList());
    }

    private RepositoryService createRepoServiceMock(int repoNumber) {
        RepositoryService result = mock(RepositoryService.class);
        String projKey;
        for (int i = 1; i <= repoNumber; i++) {
            projKey = "projRepo" + i;
            LocalRepoDescriptor descriptorMock = mock(LocalRepoDescriptor.class);
            when(descriptorMock.getKey()).thenReturn(projKey);
            when(result.localFederatedOrCachedRepoDescriptorByKey(projKey)).thenReturn(descriptorMock);
        }
        return result;
    }

}
