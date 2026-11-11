package org.artifactory.ui.rest.model.admin.configuration.repository.info;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import org.artifactory.descriptor.repo.RepoType;

import java.util.List;
import java.util.Set;

@Data
@Getter
public class RepoConfigResponse {

    private String repoKey;
    private String repoType;
    private Boolean federated;
    private RepoType packageType;
    private String projectKey;
    private String projectName;
    private Set<String> environments;
    private Set<String> sharedWithProjects;
    private boolean shareWithAllProjects;
    private boolean sharedReadOnly;
    private boolean target;

    private Boolean hasReindexAction;
    private Boolean hasMigrationAction;

    // Local and federated
    private Boolean replications;

    // Remote
    private String url;
    private Boolean hasEnabledReplication;
    private Boolean inactive;

    // Virtual
    private List<String> selectedRepos;

    @JsonProperty("isSmart")
    private Boolean smart;
    private long lastActivity;
    private boolean enableConvertToFederated;
}
