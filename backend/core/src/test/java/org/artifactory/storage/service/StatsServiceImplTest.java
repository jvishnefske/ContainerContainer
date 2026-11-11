package org.artifactory.storage.service;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.XrayDescriptor;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.Request;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.storage.db.fs.service.StatsPersistingServiceImpl;
import org.artifactory.storage.fs.service.RepoStatsService;
import org.artifactory.storage.service.constraints.NotSystemUserCriteria;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.webapp.servlet.HttpArtifactoryRequest;
import org.jfrog.access.common.ServiceType;
import org.jfrog.access.token.JwtAccessToken;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.artifactory.addon.xray.XrayAddon.ARTIFACTORY_XRAY_USER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
public class StatsServiceImplTest extends ArtifactoryHomeBoundTest {
    private static final String SKIP_UPDATE_STATS = "skipUpdateStats";

    @Mock
    private StatsPersistingServiceImpl statsPersistingService;
    @Mock
    private StatsDelegatingServiceImpl statsDelegatingService;
    @Mock
    private NotSystemUserCriteria notSystemUserCriteria;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private SecurityService securityService;
    @Mock
    private RepoStatsService repoStatsService;
    private AutoCloseable mocks;

    @BeforeClass
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test(dataProvider = "testSkipUpdateStatsOnDownload")
    public void testSkipUpdateStatsOnDownload(ConditionalMockBuilder builder) {

        StatsServiceImpl statsService = new StatsServiceImpl();
        statsService.setSecurityService(securityService);
        statsService.setStatsDelegatingService(statsDelegatingService);
        statsService.setStatsPersistingService(statsPersistingService);
        statsService.setConfigService(centralConfigService);

        mockIsDownloadStatsEnabled(builder.isDownloadStatsEnabled);

        mockIsXrayRequest(builder.isXrayUser, builder.isXrayTokenAuthentication, builder.isXrayConfigExist,
                builder.isXrayConfigEnabled);

        reset(notSystemUserCriteria);

        statsService.updateDownloadStatsIfNeeded(getRepoPathMock(),
                getRequestMock(builder.isSkippedByQueryParam, builder.isHeadRequest,
                        builder.isInternalArtifactoryRequest, builder.isSkipStatsUpdate), builder.isRealRepo);

        verify(notSystemUserCriteria, times(0)).meet(anyString());
    }

    @Test
    public void testRepoStatsCalledOnDownload() {
        homeStub.setProperty(ConstantValues.repoStatsEnabled.getPropertyName(),
                Boolean.toString(Boolean.TRUE));
        StatsServiceImpl statsService = new StatsServiceImpl();
        statsService.setSecurityService(securityService);
        statsService.setStatsDelegatingService(statsDelegatingService);
        statsService.setStatsPersistingService(statsPersistingService);
        statsService.setConfigService(centralConfigService);
        statsService.setRepoStatsService(repoStatsService);
        statsService.setNotSystemUserCriteria(notSystemUserCriteria);

        when(notSystemUserCriteria.meet(anyString())).thenReturn(true);

        mockIsXrayRequest(false, false, false,
                false);

        statsService.updateDownloadStatsIfNeeded(getRepoPathMock(),
                getRequestMock(false, false,
                        false, false), true);

        verify(repoStatsService, times(1)).update(any(), any(), any());
    }

    @DataProvider
    public Object[][] testSkipUpdateStatsOnDownload() {
        ConditionalMockBuilder skipByQueryParamTest = ConditionalMockBuilder.builder()
                .isSkippedByQueryParam(true)
                .build();

        ConditionalMockBuilder skipByHeadRequest = ConditionalMockBuilder.builder()
                .isHeadRequest(true)
                .build();

        ConditionalMockBuilder skipWhenRepoIsNotReal = ConditionalMockBuilder.builder()
                .isRealRepo(false)
                .build();

        ConditionalMockBuilder skipWhenDownloadStatsNotEnabled = ConditionalMockBuilder.builder()
                .isDownloadStatsEnabled(false)
                .build();

        ConditionalMockBuilder skipForXrayRequest = ConditionalMockBuilder.builder()
                .isXrayUser(true)
                .isXrayTokenAuthentication(false)
                .isXrayConfigExist(true)
                .isXrayConfigEnabled(true)
                .build();

        ConditionalMockBuilder skipIfNotArtifactoryRequestWithSkipProperty = ConditionalMockBuilder.builder()
                .isInternalArtifactoryRequest(true)
                .isSkipStatsUpdate(true)
                .build();

        return new Object[][] {
                {skipByQueryParamTest},
                {skipByHeadRequest},
                {skipWhenRepoIsNotReal},
                {skipWhenDownloadStatsNotEnabled},
                {skipForXrayRequest},
                {skipIfNotArtifactoryRequestWithSkipProperty}
        };
    }

    private RepoPath getRepoPathMock() {
        return Mockito.mock(RepoPath.class);
    }

    private Request getRequestMock(boolean skipUpdateByQueryParam, boolean isHeadRequest,
            boolean isInternalArtifactoryRequest, boolean isSkipStatsUpdate) {
        if (skipUpdateByQueryParam) {
            Map<String, String[]> parameters = new HashMap<>();
            parameters.put(SKIP_UPDATE_STATS, new String[] {"true"});
            Request request = Mockito.mock(Request.class);
            when(request.getParameters()).thenReturn(parameters);
            return request;
        }

        Request request =
                getInternalArtifactoryRequestMockAndSetSkipUpdate(isInternalArtifactoryRequest, isSkipStatsUpdate);
        if (isHeadRequest) {
            when(request.isHeadOnly()).thenReturn(true);
        }
        return request;
    }

    private void mockIsDownloadStatsEnabled(boolean statsEnabled) {
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        artifactoryHome.getArtifactoryProperties().setProperty(ConstantValues.downloadStatsEnabled.getPropertyName(),
                Boolean.toString(statsEnabled));
        ArtifactoryHome.bind(artifactoryHome);
    }

    private void mockIsXrayRequest(boolean isXrayUser, boolean isXrayTokenAuthentication, boolean isXrayConfigExist,
            boolean isXrayConfigEnabled) {
        mockXrayConfig(isXrayConfigExist, isXrayConfigEnabled);
        mockXrayUser(isXrayUser, isXrayTokenAuthentication);
    }

    private void mockXrayConfig(boolean isXrayConfigExist, boolean isXrayConfigEnabled) {
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        XrayDescriptor xrayDescriptor = Mockito.mock(XrayDescriptor.class);

        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        if (isXrayConfigExist) {
            when(configDescriptor.getXrayConfig()).thenReturn(xrayDescriptor);
            when(xrayDescriptor.isEnabled()).thenReturn(isXrayConfigEnabled);
        } else {
            when(configDescriptor.getXrayConfig()).thenReturn(null);
        }
    }

    private void mockXrayUser(boolean isXrayUser, boolean isXrayTokenAuthentication) {
        when(securityService.currentUsername()).thenReturn(isXrayUser ? ARTIFACTORY_XRAY_USER : "otheruser");
        if (isXrayTokenAuthentication) {
            AccessTokenAuthentication authentication = Mockito.mock(AccessTokenAuthentication.class);
            JwtAccessToken jwtAccessToken = Mockito.mock(JwtAccessToken.class);
            SecurityContext securityContext = Mockito.mock(SecurityContext.class);

            when(SecurityContextHolder.getContext()).thenReturn(securityContext);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            when(authentication.getAccessToken()).thenReturn(jwtAccessToken);
            when(jwtAccessToken.getSubject()).thenReturn(ServiceType.XRAY + "@");
        }
    }

    private Request getInternalArtifactoryRequestMockAndSetSkipUpdate(boolean isInternalArtifactoryRequest,
            boolean isSkipStatsUpdate) {
        InternalArtifactoryRequest internalArtifactoryRequest = Mockito.mock(InternalArtifactoryRequest.class);
        if (isInternalArtifactoryRequest) {
            when(internalArtifactoryRequest.isSkipStatsUpdate()).thenReturn(isSkipStatsUpdate);
            return internalArtifactoryRequest;
        } else {
            return Mockito.mock(HttpArtifactoryRequest.class);
        }
    }

    @Builder
    private static class ConditionalMockBuilder {
        @Builder.Default
        boolean isSkippedByQueryParam = false;
        @Builder.Default
        boolean isHeadRequest = false;
        @Builder.Default
        boolean isRealRepo = true;
        @Builder.Default
        boolean isDownloadStatsEnabled = true;
        @Builder.Default
        boolean isXrayUser = false;
        @Builder.Default
        boolean isXrayTokenAuthentication = false;
        @Builder.Default
        boolean isXrayConfigExist = false;
        @Builder.Default
        boolean isXrayConfigEnabled = false;
        @Builder.Default
        boolean isInternalArtifactoryRequest = false;
        @Builder.Default
        boolean isSkipStatsUpdate = false;
    }
}