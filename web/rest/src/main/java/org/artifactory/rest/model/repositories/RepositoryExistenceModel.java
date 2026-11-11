package org.artifactory.rest.model.repositories;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.repo.RepoDetailsType;

import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RepositoryExistenceModel {
    private boolean exists;
    private Set<RepoDetailsType> matchingRepoTypes;
    private String projectKey;
}