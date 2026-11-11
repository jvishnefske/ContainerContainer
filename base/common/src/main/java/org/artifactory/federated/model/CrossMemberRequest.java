package org.artifactory.federated.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.artifactory.federated.service.RequestTunnelingService;
import org.springframework.http.HttpMethod;

import java.util.Map;

/**
 * @author Tamar Rosen
 * Represents a request to be tunneled accross the federation
 * refPath may contain a template variable ${repoKey} which will be replaced with the actual repoKey
 * @see RequestTunnelingService#REF_PATH_REPO_TEMPLATE
 */
@Builder
@Getter
public class CrossMemberRequest {
    @NonNull
    String repoKey;
    @NonNull
    String refPath;
    @NonNull
    HttpMethod method;
    String payload;
    Map<String, String> headers;
}
