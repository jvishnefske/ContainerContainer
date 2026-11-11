package org.artifactory.repo;

import lombok.Builder;
import lombok.Data;

import java.util.List;


/**
 * Represents a federated repositores in Artifactory along with its base URL.
 * If Artifactory includes a federated base URL, the base URL will encompass the federated base URL.
 * Author: Daniel Simantov
 */

@Data
@Builder
public class UrlReposDetails {
    private String baseUrl;
    private List<RepoDetails> reposDetailsList;
}