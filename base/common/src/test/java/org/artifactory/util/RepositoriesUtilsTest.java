package org.artifactory.util;

import org.artifactory.api.jackson.JacksonReader;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetails;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.onboarding.DefaultRepoModel;
import org.mockito.MockedStatic;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class RepositoriesUtilsTest {

    @Test
    public void testAddNonRemoteRepoDetails() {
        String contextUrl = "url";
        List<RepoDetails> detailsList = new ArrayList<>();
        List<RepoConfig> reposToAdd = new ArrayList<>();
        reposToAdd.add(LocalRepoConfig.create("key", RepoType.Generic));
        reposToAdd.add(LocalRepoConfig.create("key2", RepoType.Docker));
        AuthorizationService service = mock(AuthorizationService.class);
        when(service.userHasPermissionsOnRepositoryRoot("key")).thenReturn(true);
        when(service.userHasPermissionsOnRepositoryRoot("key2")).thenReturn(false);
        RepositoriesUtils
                .addNonRemoteRepoConfigDetails(contextUrl, detailsList, reposToAdd, RepoDetailsType.FEDERATED, service);
        assertEquals(detailsList.size(), 1);
        assertEquals(detailsList.get(0).getPackageType(), RepoType.Generic);
        assertEquals(detailsList.get(0).getType(), RepoDetailsType.FEDERATED);
        assertEquals(detailsList.get(0).getUrl(), contextUrl + "/key");
    }

    @Test
    public void testLoadReposTypesAndModelsMap() {
        Map<String, DefaultRepoModel> outputMap = RepositoriesUtils.loadReposTypesAndModelsMap();
        assertNotNull(outputMap);
        assertFalse(outputMap.isEmpty());

        Map<String, String> reposTypesAndLayoutsMap = outputMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getLayout()));

        // validating "/templates/defaultRepository.json" file was loaded correctly using some of the specified repos.
        assertEquals(reposTypesAndLayoutsMap.get(RepoType.NuGet.getType()), RepoLayoutUtils.NUGET_DEFAULT_NAME);
        assertEquals(reposTypesAndLayoutsMap.get(RepoType.Maven.getType()), RepoLayoutUtils.MAVEN_2_DEFAULT_NAME);
        assertEquals(reposTypesAndLayoutsMap.get(RepoType.Npm.getType()), RepoLayoutUtils.NPM_DEFAULT_NAME);
        assertEquals(reposTypesAndLayoutsMap.get(RepoType.Go.getType()), RepoLayoutUtils.GO_DEFAULT_NAME);
        assertEquals(reposTypesAndLayoutsMap.get(RepoType.Generic.getType()), RepoLayoutUtils.SIMPLE_DEFAULT_NAME);
    }

    @Test
    public void testLoadReposTypesAndModelsMap_exceptionThrown() {
        try (MockedStatic<JacksonReader> jacksonReaderMockedStatic = mockStatic(JacksonReader.class)) {
            jacksonReaderMockedStatic
                    .when(() -> JacksonReader.streamAsValueTypeReference(any(), any()))
                    .thenThrow(new IOException());
            Map<String, DefaultRepoModel> outputMap = RepositoriesUtils.loadReposTypesAndModelsMap();
            assertNotNull(outputMap);
            assertTrue(outputMap.isEmpty());
        }
    }

    @Test
    public void testGetRepoLayoutForType() {
        // existing repo types
        assertEquals(RepositoriesUtils.getRepoLayoutForType(RepoType.NuGet.getType()),
                RepoLayoutUtils.NUGET_DEFAULT_NAME);
        assertEquals(RepositoriesUtils.getRepoLayoutForType(RepoType.Maven.getType()),
                RepoLayoutUtils.MAVEN_2_DEFAULT_NAME);
        assertEquals(RepositoriesUtils.getRepoLayoutForType(RepoType.Npm.getType()),
                RepoLayoutUtils.NPM_DEFAULT_NAME);
        assertEquals(RepositoriesUtils.getRepoLayoutForType(RepoType.Go.getType()),
                RepoLayoutUtils.GO_DEFAULT_NAME);
        assertEquals(RepositoriesUtils.getRepoLayoutForType(RepoType.Generic.getType()),
                RepoLayoutUtils.SIMPLE_DEFAULT_NAME);

        // not existing repo types
        assertEquals(RepositoriesUtils.getRepoLayoutForType("notExistingRepoType"),
                RepoLayoutUtils.SIMPLE_DEFAULT_NAME);

        // case insensitive
        assertEquals(RepositoriesUtils.getRepoLayoutForType(RepoType.NuGet.getType().toUpperCase()),
                RepoLayoutUtils.NUGET_DEFAULT_NAME);
        assertEquals(RepositoriesUtils.getRepoLayoutForType(RepoType.Maven.getType().toUpperCase()),
                RepoLayoutUtils.MAVEN_2_DEFAULT_NAME);
        assertEquals(RepositoriesUtils.getRepoLayoutForType(RepoType.Npm.getType().toUpperCase()),
                RepoLayoutUtils.NPM_DEFAULT_NAME);
        assertEquals(RepositoriesUtils.getRepoLayoutForType(RepoType.Go.getType().toUpperCase()),
                RepoLayoutUtils.GO_DEFAULT_NAME);
        assertEquals(RepositoriesUtils.getRepoLayoutForType(RepoType.Generic.getType().toUpperCase()),
                RepoLayoutUtils.SIMPLE_DEFAULT_NAME);
    }
}