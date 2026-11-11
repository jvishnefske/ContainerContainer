package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildPatternArtifactsRequestWithRepo;
import com.jfrog.build.service.BuildArtifactService;
import com.jfrog.build.service.BuildUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.jfrog.build.api.dependency.BuildPatternArtifacts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetArtifactsByPatternsCommandTest {

    private BuildGetArtifactsByPatternsCommand command;

    private BuildUrlService urlService;
    private BuildArtifactService artifactService;

    @BeforeEach
    void setUp() {
        urlService = mock(BuildUrlService.class);
        artifactService = mock(BuildArtifactService.class);
        command = new BuildGetArtifactsByPatternsCommand(urlService, artifactService);
    }

    @Test
    void getArtifactsByPatterns_normalizesRepoAndDelegatesToArtifactService() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(urlService.getServletContextUrl(same(request))).thenReturn("https://acme.com/artifactory");
        BuildPatternArtifactsRequestWithRepo req =
                new BuildPatternArtifactsRequestWithRepo("some-build-name", "LATEST", "phoenix-build-info", "phoenix");
        BuildPatternArtifacts artifact = mock(BuildPatternArtifacts.class);
        when(artifactService.getBuildPatternArtifacts(req, "https://acme.com/artifactory")).thenReturn(artifact);

        List<BuildPatternArtifacts> result = command.getArtifactsByPatterns(request, List.of(req));

        assertEquals(List.of(artifact), result);
        assertEquals("phoenix-build-info", req.getBuildRepo());
        verify(artifactService).getBuildPatternArtifacts(req, "https://acme.com/artifactory");
    }
}

