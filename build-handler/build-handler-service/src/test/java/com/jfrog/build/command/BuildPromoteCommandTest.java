package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.model.api.PromotionResult;
import com.jfrog.build.service.BuildPromotionService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.http.HttpHeaders;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.jfrog.build.api.release.Promotion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildPromoteCommandTest {

    private BuildPromoteCommand command;

    private BuildRepoService repoService;
    private BuildReadService readService;
    private BuildPromotionService promotionService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        readService = mock(BuildReadService.class);
        promotionService = mock(BuildPromotionService.class);
        command = new BuildPromoteCommand(repoService, readService, promotionService);
    }

    @Test
    void promote_failsWhenBuildNameIsBlank() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Promotion promotion = mock(Promotion.class);

        assertThrows(BadRequestException.class,
                () -> command.promote(request, promotion, " ", "1", "artifactory-build-info", null));
    }

    @Test
    void promote_failsWhenBuildNumberIsBlank() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Promotion promotion = mock(Promotion.class);

        assertThrows(BadRequestException.class,
                () -> command.promote(request, promotion, "some-build-name", " ", "artifactory-build-info", null));
    }

    @Test
    void promote_decodesParamsAndPromotesLatestBuildRun() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn("ArtifactoryBuildClient/2.0.0");

        Promotion promotion = mock(Promotion.class);
        when(repoService.getPreferredBuildRepo("br", "phoenix")).thenReturn("phoenix-build-info");

        BuildRun older = mock(BuildRun.class);
        when(older.getStartedDate()).thenReturn(new Date(10));
        BuildRun newer = mock(BuildRun.class);
        when(newer.getStartedDate()).thenReturn(new Date(20));

        when(readService.searchBuildsByNameAndNumberInternal(eq("b n"), eq("1"), eq("phoenix-build-info")))
                .thenReturn(Set.of(older, newer));

        PromotionResult expected = mock(PromotionResult.class);
        when(promotionService.promoteBuild(same(newer), same(promotion))).thenReturn(expected);

        PromotionResult result = command.promote(request, promotion, "b%20n", "1", "br", "phoenix");

        assertSame(expected, result);
        verify(repoService).getPreferredBuildRepo("br", "phoenix");
        verify(readService).searchBuildsByNameAndNumberInternal("b n", "1", "phoenix-build-info");
        verify(promotionService).promoteBuild(newer, promotion);
    }

    @Test
    void promote_failsWhenBuildRunNotFound() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Promotion promotion = mock(Promotion.class);
        when(repoService.getPreferredBuildRepo(any(), any())).thenReturn("artifactory-build-info");
        when(readService.searchBuildsByNameAndNumberInternal(any(), any(), any())).thenReturn(Set.of());

        NotFoundException e = assertThrows(NotFoundException.class,
                () -> command.promote(request, promotion, "some-build-name", "1", null, null));

        assertEquals(
                "Cannot find a build by the name: some-build-name, number: 1, repo: artifactory-build-info",
                e.getMessage());
    }
}

