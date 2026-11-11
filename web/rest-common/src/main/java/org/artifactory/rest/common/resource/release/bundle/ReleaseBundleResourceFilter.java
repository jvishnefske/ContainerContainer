package org.artifactory.rest.common.resource.release.bundle;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.release.bundle.ReleaseBundleActors;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.List;

@Provider
@Slf4j
@ReleaseBundleResourceBinding
public class ReleaseBundleResourceFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext containerRequestContext) throws IOException {
        List<String> agents = containerRequestContext.getHeaders().get(HttpHeaders.USER_AGENT);
        if (CollectionUtils.isNotEmpty(agents)) {
            boolean fromArtifactoryClient = agents.stream()
                    .anyMatch(agent -> agent.startsWith("artifactory-client-java"));

            // we assume that requests coming from artifactory-client-java are only from Distribution service and not
            // user initiated
            if (fromArtifactoryClient) {
                CurrentActivity.ACTOR.put(ReleaseBundleActors.DISTRIBUTION_SERVICE);
                return;
            }
        }
        CurrentActivity.ACTOR.put(ReleaseBundleActors.EXTERNAL_USER);
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext,
            ContainerResponseContext containerResponseContext) throws IOException {
        CurrentActivity.ACTOR.remove();
    }
}
