package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildPatternArtifactsRequestWithRepo;
import com.jfrog.build.service.BuildArtifactService;
import com.jfrog.build.service.BuildUrlService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jfrog.build.api.dependency.BuildPatternArtifacts;

import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class BuildGetArtifactsByPatternsCommand {

    private final BuildUrlService urlService;
    private final BuildArtifactService artifactService;

    @NonNull
    public List<BuildPatternArtifacts> getArtifactsByPatterns(
            @NonNull HttpServletRequest request,
            @NonNull List<BuildPatternArtifactsRequestWithRepo> requests) {
        String servletContextUrl = urlService.getServletContextUrl(request);
        return requests.stream()
                .map(pattern -> artifactService.getBuildPatternArtifacts(pattern, servletContextUrl))
                .filter(Objects::nonNull)
                .toList();
    }
}

