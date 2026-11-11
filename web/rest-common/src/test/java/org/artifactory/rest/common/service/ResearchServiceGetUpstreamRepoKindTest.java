package org.artifactory.rest.common.service;

import org.artifactory.api.config.VersionInfo;
import org.artifactory.api.repo.UpstreamRepoKind;
import org.artifactory.features.VersionFeature;
import org.testng.annotations.Test;

import java.util.Collections;

import static org.testng.Assert.assertEquals;

public class ResearchServiceGetUpstreamRepoKindTest {

    @Test
    public void whenItIsNotArtifactory() {
        ResearchServiceImpl researchService = new ResearchServiceImpl();
        ResearchServiceImpl.ResearchResponse researchResponse = ResearchServiceImpl.ResearchResponse.notArtifactory();
        ResearchServiceImpl.RepoInfo repoInfo = ResearchServiceImpl.RepoInfo.UNKNOWN;
        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                new ResearchServiceImpl.ArtifactoryCapabilities(researchResponse, repoInfo);

        UpstreamRepoKind upstreamRepoKind = researchService.getUpstreamRepoKind(capabilities);

        assertEquals(upstreamRepoKind, UpstreamRepoKind.NOT_ARTIFACTORY, "repo kind is not artifactory");
    }

    @Test
    public void whenNotSmartRepo() {
        ResearchServiceImpl researchService = new ResearchServiceImpl();
        ResearchServiceImpl.ResearchResponse researchResponse = ResearchServiceImpl.ResearchResponse.artifactory();
        ResearchServiceImpl.RepoInfo repoInfo = ResearchServiceImpl.RepoInfo.NOT_ALLOWED_SMART;
        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                new ResearchServiceImpl.ArtifactoryCapabilities(researchResponse, repoInfo);

        UpstreamRepoKind upstreamRepoKind = researchService.getUpstreamRepoKind(capabilities);

        assertEquals(upstreamRepoKind, UpstreamRepoKind.COMMON, "repo kind is common");
    }

    @Test
    public void whenUnauthorized() {
        ResearchServiceImpl researchService = new ResearchServiceImpl();
        ResearchServiceImpl.ResearchResponse researchResponse = ResearchServiceImpl.ResearchResponse.artifactory();
        ResearchServiceImpl.RepoInfo repoInfo = ResearchServiceImpl.RepoInfo.UNAUTHORIZED;
        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                new ResearchServiceImpl.ArtifactoryCapabilities(researchResponse, repoInfo);

        UpstreamRepoKind upstreamRepoKind = researchService.getUpstreamRepoKind(capabilities);

        assertEquals(upstreamRepoKind, UpstreamRepoKind.UNAUTHORIZED, "repo kind is unauthorized");
    }

    @Test
    public void whenUnknown() {
        ResearchServiceImpl researchService = new ResearchServiceImpl();
        ResearchServiceImpl.ResearchResponse researchResponse = ResearchServiceImpl.ResearchResponse.artifactory();
        ResearchServiceImpl.RepoInfo repoInfo = ResearchServiceImpl.RepoInfo.UNKNOWN;
        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                new ResearchServiceImpl.ArtifactoryCapabilities(researchResponse, repoInfo);

        UpstreamRepoKind upstreamRepoKind = researchService.getUpstreamRepoKind(capabilities);

        assertEquals(upstreamRepoKind, UpstreamRepoKind.UNKNOWN, "repo kind is unknown");
    }

    @Test
    public void whenUnknownButIsArtifactory() {
        // When repoInfo is UNKNOWN but we know it's Artifactory (e.g., license validation failed),
        // we should return UNKNOWN, not NOT_ARTIFACTORY
        ResearchServiceImpl researchService = new ResearchServiceImpl();
        ResearchServiceImpl.ResearchResponse researchResponse = ResearchServiceImpl.ResearchResponse.artifactory();
        ResearchServiceImpl.RepoInfo repoInfo = ResearchServiceImpl.RepoInfo.UNKNOWN;
        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                new ResearchServiceImpl.ArtifactoryCapabilities(researchResponse, repoInfo);

        UpstreamRepoKind upstreamRepoKind = researchService.getUpstreamRepoKind(capabilities);

        assertEquals(upstreamRepoKind, UpstreamRepoKind.UNKNOWN, "repo kind is unknown, not NOT_ARTIFACTORY");
    }

    @Test
    public void whenNotAvailable() {
        ResearchServiceImpl researchService = new ResearchServiceImpl();
        ResearchServiceImpl.ResearchResponse researchResponse = ResearchServiceImpl.ResearchResponse.artifactory();
        ResearchServiceImpl.RepoInfo repoInfo = ResearchServiceImpl.RepoInfo.NOT_AVAILABLE;
        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                new ResearchServiceImpl.ArtifactoryCapabilities(researchResponse, repoInfo);

        UpstreamRepoKind upstreamRepoKind = researchService.getUpstreamRepoKind(capabilities);

        assertEquals(upstreamRepoKind, UpstreamRepoKind.NOT_AVAILABLE, "repo kind is not available");
    }

    @Test
    public void whenAllowedSmartAndVersionIsOk() {
        ResearchServiceImpl researchService = new ResearchServiceImpl();
        VersionInfo versionInfo = new VersionInfo("v", "r");
        VersionFeature versionFeature = new VersionFeature("f", versionInfo);
        ResearchServiceImpl.ResearchResponse researchResponse =
                ResearchServiceImpl.ResearchResponse.artifactoryMeta(true, versionInfo,
                        Collections.singletonList(versionFeature));
        ResearchServiceImpl.RepoInfo repoInfo = ResearchServiceImpl.RepoInfo.ALLOWED_SMART;
        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                new ResearchServiceImpl.ArtifactoryCapabilities(researchResponse, repoInfo);

        UpstreamRepoKind upstreamRepoKind = researchService.getUpstreamRepoKind(capabilities);

        assertEquals(upstreamRepoKind, UpstreamRepoKind.SMART, "repo kind is smart");
    }

    @Test
    public void whenAllowedSmartButVersionIsNull() {
        ResearchServiceImpl researchService = new ResearchServiceImpl();
        VersionInfo versionInfo = new VersionInfo("v", "r");
        VersionFeature versionFeature = new VersionFeature("f", versionInfo);
        ResearchServiceImpl.ResearchResponse researchResponse =
                ResearchServiceImpl.ResearchResponse.artifactoryMeta(true, null,
                        Collections.singletonList(versionFeature));
        ResearchServiceImpl.RepoInfo repoInfo = ResearchServiceImpl.RepoInfo.ALLOWED_SMART;
        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                new ResearchServiceImpl.ArtifactoryCapabilities(researchResponse, repoInfo);

        UpstreamRepoKind upstreamRepoKind = researchService.getUpstreamRepoKind(capabilities);

        assertEquals(upstreamRepoKind, UpstreamRepoKind.COMMON, "repo kind is common");
    }

    @Test
    public void whenAllowedSmartButNoFeatures() {
        ResearchServiceImpl researchService = new ResearchServiceImpl();
        VersionInfo versionInfo = new VersionInfo("v", "r");
        ResearchServiceImpl.ResearchResponse researchResponse =
                ResearchServiceImpl.ResearchResponse.artifactoryMeta(true, versionInfo,
                        Collections.emptyList());
        ResearchServiceImpl.RepoInfo repoInfo = ResearchServiceImpl.RepoInfo.ALLOWED_SMART;
        ResearchServiceImpl.ArtifactoryCapabilities capabilities =
                new ResearchServiceImpl.ArtifactoryCapabilities(researchResponse, repoInfo);

        UpstreamRepoKind upstreamRepoKind = researchService.getUpstreamRepoKind(capabilities);

        assertEquals(upstreamRepoKind, UpstreamRepoKind.COMMON, "repo kind is common");
    }

}
