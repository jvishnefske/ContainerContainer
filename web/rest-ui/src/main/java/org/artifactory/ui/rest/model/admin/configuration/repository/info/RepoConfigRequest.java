package org.artifactory.ui.rest.model.admin.configuration.repository.info;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;

import java.util.List;
import java.util.Set;

@Data
@Builder
public class RepoConfigRequest {

    private List<String> repoKeys;
    private Set<RepoDetailsType> repoTypes;
    private Set<RepoType> packageTypes;
    private Set<String> projectKeys;
    private Set<String> environments;
    private String url;
    private Boolean inactive;
    private Boolean replications;
    private Boolean federated;
    private RepoProjectAssociation projectAssociation;
    private String repoKeyToSearch;
    private boolean keepRepoKeysOrder;
    @JsonProperty("isProjectContext")
    private boolean projectContext;
    private Long offset;
    private Long limit;
    private String sortBy;
    private String sortDirection;
    /**
     * Server-side flag derived from the {@code isFedAttrSupported} query param, not from the request body.
     * When true, the federated filter is evaluated by federation members (self + >= 1 mirror).
     */
    @JsonIgnore
    private boolean fedAttrOptIn;
}
