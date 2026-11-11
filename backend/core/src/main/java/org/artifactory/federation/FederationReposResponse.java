package org.artifactory.federation;

import org.artifactory.repo.RepoDetails;

import java.util.List;

/**
 * @author Alexei Vainshtein
 */
public class FederationReposResponse {

    private List<RepoDetails> repos;
    private String url;
    private String federatedBaseURL;
    private String error;

    public FederationReposResponse() {
    }

    public FederationReposResponse(String url) {
        this.url = url;
    }

    public FederationReposResponse(String url, String error) {
        this.url = url;
        this.error = error;
    }

    public List<RepoDetails> getRepos() {
        return repos;
    }

    public String getUrl() {
        return url;
    }

    public String getFederatedBaseURL() {
        return federatedBaseURL;
    }

    public String getError() {
        return error;
    }

    public void setRepos(List<RepoDetails> repos) {
        this.repos = repos;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setFederatedBaseURL(String federatedBaseURL) {
        this.federatedBaseURL = federatedBaseURL;
    }

    public void setError(String error) {
        this.error = error;
    }

}
