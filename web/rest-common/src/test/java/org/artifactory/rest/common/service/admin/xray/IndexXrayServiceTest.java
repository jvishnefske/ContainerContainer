package org.artifactory.rest.common.service.admin.xray;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.rest.common.model.xray.RepositoriesHolder;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * Author: Ivan Perepelytsya
 * Date: 5/12/20.
 */
@Test
public class IndexXrayServiceTest {

    private final String REPOS = "repo1, repo2, repo3";
    private XrayAddon xrayAddon;
    private IndexXrayService indexXrayService;
    private ArtifactoryRestRequest restRequest;
    private RestResponse restResponse;

    @BeforeMethod
    private void prepareMocks(){
        AddonsManager addonsManager = mock(AddonsManager.class);
        xrayAddon = mock(XrayAddon.class);
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);
        when(xrayAddon.isXrayEnabled()).thenReturn(true);
        indexXrayService = new IndexXrayService();
        indexXrayService.setAddonsManager(addonsManager);
        restRequest = mock(ArtifactoryRestRequest.class);
        restResponse = mock(RestResponse.class);
    }

    @Test
    public void testBodyParams() {
        final RepositoriesHolder holder = new RepositoriesHolder();
        holder.setRepos(List.of("a","b","c","d"));
        when(restRequest.getImodel()).thenReturn(holder);
        when(restRequest.getQueryParamByKey("repos")).thenReturn("");

        indexXrayService.execute(restRequest, restResponse);
        assert202Results(4, "a", "b", "c", "d");
    }

    @Test
    public void testNoParams()  {
        when(restRequest.getQueryParamByKey("repos")).thenReturn("");
        when(restRequest.getImodel()).thenReturn(null);
        assert400Results();
    }


    @Test
    public void testXrayDisabled(){
        when(xrayAddon.isXrayEnabled()).thenReturn(false);
        assert400Results();
    }

    @Test
    public void testBothParams() {
        final RepositoriesHolder holder = new RepositoriesHolder();
        holder.setRepos(List.of("a","b","c","d"));
        when(restRequest.getImodel()).thenReturn(holder);

        when(restRequest.getQueryParamByKey("repos")).thenReturn(REPOS);
        indexXrayService.execute(restRequest, restResponse);
        assert202Results(3, "repo1", "repo2", "repo3");
    }

    @Test
    public void testQueryParams() {
        when(restRequest.getQueryParamByKey("repos")).thenReturn(REPOS);
        indexXrayService.execute(restRequest, restResponse);
        assert202Results(3, "repo1", "repo2", "repo3");
    }

    private void assert202Results(int size, String ... args){
        ArgumentCaptor<List> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(xrayAddon).indexRepos(listArgumentCaptor.capture());

        ArgumentCaptor<Integer> responseCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(restResponse).responseCode(responseCaptor.capture());
        assertEquals(responseCaptor.getValue().intValue(), 202);

        final List value = listArgumentCaptor.getValue();
        assertEquals(value.size(), size);
        for (int i = 0; i < size; i++) {
            assertEquals(value.get(i), args[i]);
        }
    }

    private void assert400Results(){
        final ArtifactoryRestResponse artifactoryRestResponse = mock(ArtifactoryRestResponse.class);
        when(restResponse.error(any())).thenReturn(artifactoryRestResponse);
        indexXrayService.execute(restRequest, restResponse);
        ArgumentCaptor<Integer> responseCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(artifactoryRestResponse).responseCode(responseCaptor.capture());
        assertEquals(responseCaptor.getValue().intValue(), 400);
    }
}