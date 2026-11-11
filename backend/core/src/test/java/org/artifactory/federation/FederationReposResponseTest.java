package org.artifactory.federation;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.RepoDetails;
import org.artifactory.util.CollectionUtils;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class FederationReposResponseTest {

    @Test
    public void testDefaultValues() {
        FederationReposResponse reposResponse = new FederationReposResponse();
        assertTrue(StringUtils.isBlank(reposResponse.getError()));
        assertTrue(StringUtils.isBlank(reposResponse.getUrl()));
        assertTrue(StringUtils.isBlank(reposResponse.getFederatedBaseURL()));
        assertTrue(CollectionUtils.isNullOrEmpty(reposResponse.getRepos()));
    }

    @Test
    public void testNonDefaultValues() {
        String url = "url";
        FederationReposResponse reposResponse = new FederationReposResponse(url);
        assertTrue(StringUtils.isBlank(reposResponse.getError()));
        assertEquals(reposResponse.getUrl(), url);
        assertTrue(CollectionUtils.isNullOrEmpty(reposResponse.getRepos()));

        reposResponse = new FederationReposResponse(url, "Error");
        assertEquals(reposResponse.getUrl(), url);
        assertEquals(reposResponse.getError(), "Error");
        assertTrue(CollectionUtils.isNullOrEmpty(reposResponse.getRepos()));

        reposResponse.setError("error2");
        reposResponse.setUrl("url2");
        reposResponse.setFederatedBaseURL("urlFederated2");
        List<RepoDetails> repoDetailsList = new ArrayList<>();
        repoDetailsList.add(new RepoDetails());
        reposResponse.setRepos(repoDetailsList);

        assertEquals(reposResponse.getRepos(), repoDetailsList);
        assertEquals(reposResponse.getUrl(), "url2");
        assertEquals(reposResponse.getFederatedBaseURL(), "urlFederated2");
        assertEquals(reposResponse.getError(), "error2");
    }


}