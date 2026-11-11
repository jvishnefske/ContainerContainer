package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.federation.FederationReposResponse;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.repo.RepoDetails;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.apache.http.HttpStatus.SC_OK;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class DiscoverMirrorReposTest {

    private FederatedRepoService service;

    @BeforeMethod
    public void setup() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        service = mock(FederatedRepoService.class);
        when(context.beanForType(FederatedRepoService.class)).thenReturn(service);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testExecute() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = mock(ArtifactoryRestResponse.class);

        when(response.responseCode(anyInt())).thenReturn(response);
        FederationConfigModel model = new FederationConfigModel();
        model.setMirrors(new ArrayList<>());
        model.setProxy("proxy");
        when(request.getImodel()).thenReturn(model);

        List<FederationReposResponse> reposFromRemote = new ArrayList<>();
        FederationReposResponse reposResponse = new FederationReposResponse();
        reposResponse.setUrl("url1");
        List<RepoDetails> repoDetailsList = new ArrayList<>();
        repoDetailsList.add(new RepoDetails());
        reposResponse.setRepos(repoDetailsList);
        reposFromRemote.add(reposResponse);

        FederationReposResponse anotherResponse = new FederationReposResponse();
        anotherResponse.setUrl("url2");
        anotherResponse.setError("error");
        reposFromRemote.add(anotherResponse);

        doAnswer(invocationOnMock -> {
            ArrayList list = invocationOnMock.getArgument(0, ArrayList.class);
            assertEquals(list.size(), reposFromRemote.size());
            assertTrue(list.contains(anotherResponse));
            assertTrue(list.contains(reposResponse));
            return null;
        }).when(response).iModel(reposFromRemote);
        when(service.getReposFromRemote(model.mirrorsToUrls(), model.getProxy())).thenReturn(reposFromRemote);
        DiscoverMirrorRepos repos = new DiscoverMirrorRepos();
        repos.execute(request, response);
        verify(response, times(1)).responseCode(SC_OK);
    }
}