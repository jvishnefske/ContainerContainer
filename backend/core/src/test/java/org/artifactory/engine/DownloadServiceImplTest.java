package org.artifactory.engine;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.analytics.AnalyticsAddon;
import org.artifactory.addon.leadartifactdetector.LeadArtifactDetectorAddon;
import org.artifactory.addon.onboarding.OnBoardingAddon;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.download.DownloadCtx;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.request.ControlledCacheRedirectService;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.request.InternalRequestFactory;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.plugin.AltResponseHelper;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.db.DbCacheRepo;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.DownloadPerfCounters;
import org.artifactory.request.DownloadRequestContext;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.RedirectInfo;
import org.artifactory.request.RemoteRequestException;
import org.artifactory.request.RepoRequestContext;
import org.artifactory.request.RepoRequests;
import org.artifactory.request.Request;
import org.artifactory.request.RequestResponseHelper;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.request.RequestWrapper;
import org.artifactory.resource.BlockedRepoResource;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.MutableRepoResourceInfo;
import org.artifactory.resource.RemoteRepoResource;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.rest.resource.ArtifactoryResourceInfo;
import org.artifactory.service.SmartRepoService;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.storage.fs.service.StatsService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.traffic.TrafficEntry;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.entry.LegacyTrafficEntry;
import org.artifactory.unifiedevent.UnifiedEventServiceImpl;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.ResponseContextData;
import org.artifactory.util.AolUtils;
import org.jfrog.storage.binstore.ifc.ManualRedirectType;
import org.jfrog.storage.binstore.ifc.RedirectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.apache.http.HttpStatus.SC_BAD_GATEWAY;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.artifactory.api.request.ArtifactoryHeaders.X_JFROG_CONTROLLED_CACHE_RESPONSE;
import static org.artifactory.common.ConstantValues.systemFolderContractEnabled;
import static org.artifactory.engine.ControlledCacheConstants.JFROG_CONTROLLED_CACHE_RESPONSE_REDIRECT_VALUE;
import static org.artifactory.engine.ControlledCacheConstants.JFROG_CONTROLLED_CACHE_RESPONSE_STREAM_VALUE;
import static org.artifactory.engine.DownloadServiceTestUtils.mockIsRedirectFromCachingProxy;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.jfrog.storage.binstore.ifc.ManualRedirectType.CF;
import static org.jfrog.storage.binstore.ifc.ManualRedirectType.DEFAULT;
import static org.jfrog.storage.binstore.ifc.ManualRedirectType.NONE;
import static org.jfrog.storage.binstore.ifc.ManualRedirectType.PDN;
import static org.jfrog.storage.binstore.ifc.ManualRedirectType.S3;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.MANUAL_REDIRECT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Shay Bagants
 */
public class DownloadServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private DownloadServiceImpl downloadService;

    @Mock
    private InternalRequestContext requestContext;
    @Mock
    private ArtifactoryRequest request;
    @Mock
    private RepoResource resource;
    @Mock
    private SecurityService securityService;
    @Mock
    private DownloadRedirectHelper downloadRedirectHelper;
    @Mock
    private ArtifactoryBinaryService binaryService;
    @Mock
    private CentralConfigService centralConfig;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private AolService aolService;
    @Mock
    private WorkersAddon workersAddon;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private PluginsAddon pluginAddon;
    @Mock
    private OnBoardingAddon onBoardingAddon;
    @Mock
    private LeadArtifactDetectorAddon leadArtifactDetectorAddon;
    @Mock
    private SmartRepoService smartRepoService;
    @Mock
    private TrafficService trafficService;
    @Mock
    private AnalyticsAddon analyticsAddon;
    @Mock
    private ControlledCacheRedirectService controlledCacheRedirectService;
    @Mock
    private StatsService statsService;
    @Mock
    private AltResponseHelper altResponseHelper;
    @Mock
    private UnifiedEventServiceImpl unifiedEventService;
    private AutoCloseable mocks;

    private static Stream<Arguments> getCdnConfigurationsData() {
        return Stream.of(
                //isCdn, isEplus, isEdge, isEnterprise, isAol, isAnonymous, manualSentType, expectedType
                //AOL
                Arguments.of(true, true, false, false, true, false, CF, RedirectType.CLOUD_FRONT),
                Arguments.of(true, true, false, false, true, false, DEFAULT, RedirectType.CLOUD_FRONT),
                Arguments.of(true, false, true, false, true, false, CF, RedirectType.CLOUD_FRONT),
                Arguments.of(true, true, false, false, true, true, CF, RedirectType.CLOUD_FRONT),
                Arguments.of(true, false, true, false, true, true, CF, RedirectType.CLOUD_FRONT),
                Arguments.of(true, false, false, true, true, true, CF, RedirectType.CLOUD_FRONT),
                Arguments.of(true, false, false, true, true, false, S3, RedirectType.S3),
                Arguments.of(true, false, false, true, true, false, DEFAULT, RedirectType.S3),
                Arguments.of(true, false, false, false, true, true, S3, RedirectType.S3),
                Arguments.of(true, false, false, false, true, false, S3, RedirectType.S3),
                Arguments.of(false, true, false, false, true, false, S3, RedirectType.S3),
                Arguments.of(false, false, true, false, true, false, S3, RedirectType.S3),
                Arguments.of(false, false, false, false, true, false, S3, RedirectType.S3),
                //Manual DIRECT request - will ignore the manual request when it's AOL
                Arguments.of(true, true, false, false, true, false, NONE, RedirectType.CLOUD_FRONT),
                Arguments.of(false, false, true, false, true, false, NONE, RedirectType.S3),
                //OnPrem - isCdn cannot be true onPrem (as of 29/03/20)
                Arguments.of(false, false, false, false, false, false, NONE, RedirectType.DIRECT),
                // On-prem E+
                Arguments.of(false, true, false, false, false, false, NONE, RedirectType.DIRECT),
                Arguments.of(false, true, false, false, false, false, DEFAULT, RedirectType.CLOUD_FRONT),
                Arguments.of(false, true, false, false, false, true, DEFAULT, RedirectType.CLOUD_FRONT),
                Arguments.of(false, true, false, false, false, false, S3, RedirectType.S3),
                Arguments.of(false, true, false, false, false, false, CF, RedirectType.CLOUD_FRONT),
                // On-prem Edge
                Arguments.of(false, false, true, false, false, false, NONE, RedirectType.DIRECT),
                Arguments.of(false, false, true, false, false, false, DEFAULT, RedirectType.CLOUD_FRONT),
                Arguments.of(false, false, true, false, false, true, DEFAULT, RedirectType.CLOUD_FRONT),
                Arguments.of(false, false, true, false, false, false, S3, RedirectType.S3),
                Arguments.of(false, false, true, false, false, false, CF, RedirectType.CLOUD_FRONT),
                // On-prem Enterprise. Redirect is not allowed
                Arguments.of(false, false, false, true, false, false, NONE, RedirectType.DIRECT),
                Arguments.of(false, false, false, true, false, true, NONE, RedirectType.DIRECT),
                Arguments.of(false, false, false, true, false, false, DEFAULT, RedirectType.DIRECT),
                Arguments.of(false, false, false, true, false, true, DEFAULT, RedirectType.DIRECT),
                Arguments.of(false, false, false, true, false, false, S3, RedirectType.DIRECT),
                Arguments.of(false, false, false, true, false, false, CF, RedirectType.DIRECT)
        );
    }

    private static Stream<Arguments> getCdnConfigurationsWithCdnListData() {
        return Stream.of(
                //isCdn, isEplus, isEdge, isEnterprise, isAol, isAnonymous, manualSentType, expectedType
                //if list of allowed cdn repos configured isCdn is ignored
                // No manual redirect
                Arguments.of(false, true, false, false, true, true, DEFAULT, RedirectType.CLOUD_FRONT),
                Arguments.of(false, true, false, false, true, false, DEFAULT, RedirectType.CLOUD_FRONT),
                Arguments.of(false, false, true, false, true, true, DEFAULT, RedirectType.CLOUD_FRONT),
                Arguments.of(false, false, true, false, true, false, DEFAULT, RedirectType.CLOUD_FRONT),
                Arguments.of(false, false, false, true, true, true, DEFAULT, RedirectType.CLOUD_FRONT),
                Arguments.of(false, false, false, true, true, false, DEFAULT, RedirectType.CLOUD_FRONT),
                // Manual redirect to S3
                Arguments.of(false, true, false, false, true, true, S3, RedirectType.S3),
                Arguments.of(false, true, false, false, true, false, S3, RedirectType.S3),
                Arguments.of(false, false, true, false, true, true, S3, RedirectType.S3),
                Arguments.of(false, false, true, false, true, false, S3, RedirectType.S3),
                Arguments.of(false, false, false, true, true, true, S3, RedirectType.S3),
                Arguments.of(false, false, false, true, true, false, S3, RedirectType.S3),
                //checking that for non-AOL list of allowed CDN repos are not applied
                Arguments.of(false, true, false, false, false, false, S3, RedirectType.S3)
        );
    }

    private static Stream<Arguments> headerByPriorityDataProvider() {
        return Stream.of(
                // threadHeader, threadHeaderDocker, contextHeader, contextHeaderDocker, expected
                Arguments.of(S3.getType(), null, null, null, S3),
                Arguments.of(NONE.getType(), null, null, null, NONE),
                Arguments.of(null, null, null, null, null),
                Arguments.of(S3.getType(), PDN.getType(), null, null,
                        S3),
                Arguments.of(S3.getType(), null, PDN.getType(), null,
                        S3),
                Arguments.of(null, PDN.getType(), null, null,
                        PDN),
                Arguments.of(null, null, null, PDN.getType(),
                        PDN),
                Arguments.of(null, null, S3.getType(), PDN.getType(),
                        S3)
        );
    }


    @BeforeEach
    public void setup() {
        downloadService = spy(new DownloadServiceImpl());
        mocks = MockitoAnnotations.openMocks(this);
        when(requestContext.getRequest()).thenReturn(request);
        reset(downloadService);
        downloadService.securityService = securityService;
        downloadService.downloadRedirectHelper = downloadRedirectHelper;
        downloadService.addonsManager = addonsManager;
        downloadService.binaryService = binaryService;
        downloadService.centralConfig = centralConfig;
        downloadService.repositoryService = repositoryService;
        downloadService.projectsService = projectsService;
        downloadService.smartRepoService = smartRepoService;
        downloadService.unifiedEventService = unifiedEventService;
        downloadService.requestResponseHelper = new RequestResponseHelper(trafficService, securityService,
                CountingInputStream::new);
        downloadService.binaryElementFactory =
                new BinaryElementFactory(securityService, projectsService, centralConfig);
        downloadService.controlledCacheRedirectService = controlledCacheRedirectService;
        downloadService.statsService = statsService;
        downloadService.altResponseHelper = altResponseHelper;
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        ArtifactoryContextThreadBinder.bind(context);
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginAddon);
    }

    @AfterEach
    public void unbind() throws Exception {
        mocks.close();
        unbindArtifactoryHome();
        RequestThreadLocal.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @MethodSource("headerByPriorityDataProvider")
    void testGetHeaderByPriority(String threadHeader, String threadHeaderDocker, String contextHeader,
            String contextHeaderDocker, ManualRedirectType expected) {
        bindRequest(threadHeader, threadHeaderDocker);
        Enumeration<String> enumeration = mock(Enumeration.class);
        when(enumeration.nextElement()).thenReturn(contextHeader);
        when(enumeration.hasMoreElements()).thenReturn(contextHeader != null);
        when(request.getHeaderValues(MANUAL_REDIRECT_TYPE.getHeaderName())).thenReturn(enumeration);
        Enumeration<String> enumerationDocker = mock(Enumeration.class);
        when(enumerationDocker.nextElement()).thenReturn(contextHeaderDocker);
        when(enumerationDocker.hasMoreElements()).thenReturn(contextHeaderDocker != null);
        when(request.getHeaderValues("X-Meta-JFrog-Download-Redirect-To")).thenReturn(enumerationDocker);
        Optional<String> actualHeader = DownloadServiceImpl.getHeaderByPriority(requestContext, MANUAL_REDIRECT_TYPE);
        Optional<String> expectedHeader = Optional.ofNullable(expected).map(ManualRedirectType::getType);
        assertEquals(expectedHeader, actualHeader);
    }

    private void bindRequest(String header) {
        RequestWrapper requestWrapper = mock(RequestWrapper.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(requestWrapper.getRequest()).thenReturn(request);
        when(request.getHeader(MANUAL_REDIRECT_TYPE.getHeaderName())).thenReturn(header);
        RequestThreadLocal.bind(requestWrapper);
    }


    @ParameterizedTest
    @MethodSource("threadLocalDataProvider")
    void testGetManualRedirectType(String threadHeader, ManualRedirectType expected, boolean isAol) {
        bindRequest(threadHeader);
        Enumeration enumeration = mock(Enumeration.class);
        when(enumeration.hasMoreElements()).thenReturn(false);
        when(request.getHeaderValues(MANUAL_REDIRECT_TYPE.getHeaderName())).thenReturn(enumeration);
        doReturn(isAol).when(downloadService).canUseCdnWithLicense(any(Repo.class));
        ManualRedirectType type = downloadService.getManualRedirectType(requestContext, mock(Repo.class));
        assertEquals(expected, type);
    }

    private static Stream<Arguments> threadLocalDataProvider() {
        return Stream.of(
                Arguments.of(S3.getType(), S3, false),
                Arguments.of(CF.getType(), CF, true),
                Arguments.of(CF.getType(), DEFAULT, false),
                Arguments.of(NONE.getType(), NONE, false),
                Arguments.of(null, DEFAULT, false)
        );
    }


    @ParameterizedTest
    @MethodSource("getCdnConfigurationsData")
    void testGetRedirectTypeForRepo_differentCdnConfigurations(boolean isCdn, boolean isEplus, boolean isEdge,
            boolean isEnterprise, boolean isAol, boolean isAnonymous, ManualRedirectType typeSent,
            RedirectType expectedType) {
        setupCdn(false);
        testDifferentCdnConfigurations(isCdn, isEplus, isEdge, isEnterprise, isAol, isAnonymous, typeSent,
                expectedType);
    }

    @ParameterizedTest
    @MethodSource("getCdnConfigurationsWithCdnListData")
    void testGetRedirectTypeForRepo_differentCdnConfigurationsWithCdnList(boolean isCdn, boolean isEplus,
            boolean isEdge, boolean isEnterprise, boolean isAol, boolean isAnonymous, ManualRedirectType typeSent,
            RedirectType expectedType) {
        setupCdn(true);
        testDifferentCdnConfigurations(isCdn, isEplus, isEdge, isEnterprise, isAol, isAnonymous, typeSent,
                expectedType);
    }

    private void testDifferentCdnConfigurations(boolean isCdn, boolean isEplus, boolean isEdge,
            boolean isEnterprise, boolean isAol, boolean isAnonymous, ManualRedirectType typeSent,
            RedirectType expectedType) {
        Repo repo = mock(Repo.class);
        LocalRepoDescriptor repoDescriptor = mock(LocalRepoDescriptor.class);
        RepoResourceInfo repoResourceInfo = mock(RepoResourceInfo.class);

        when(repoResourceInfo.getSha1()).thenReturn("cf23df2207d99a74fbe169e3eba035e633b65d94");
        when(resource.getInfo()).thenReturn(repoResourceInfo);
        when(repoDescriptor.isReal()).thenReturn(true);
        when(repo.getDescriptor()).thenReturn(repoDescriptor);

        // using params
        when(repoDescriptor.isCdnRedirect()).thenReturn(isCdn);
        when(addonsManager.isEntitled(EntitlementType.CDN)).thenReturn(isEplus || isEdge);
        when(addonsManager.isEntitled(EntitlementType.ANONYMOUS_CDN)).thenReturn(isEplus || isEnterprise || isEdge);
        when(addonsManager.isEntitled(EntitlementType.BINARY_PROVIDER_DOWNLOAD_REDIRECT)).thenReturn(isEdge || isEplus);
        when(aolService.isAol()).thenReturn(isAol);
        when(securityService.isAnonymous()).thenReturn(isAnonymous);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Go);
        when(repo.getRepoConfig()).thenReturn(repoConfig);
        RedirectType redirectType = downloadService.getRedirectTypeForRepo(requestContext, repo, resource, typeSent);

        assertEquals(expectedType, redirectType);
    }

    @ParameterizedTest
    @MethodSource("ignoreListOfReposAllowedSendRedirectUrlScenarios")
    void testSaasRedirectPerRepo_isAol(String ignoreListOfRepos, int isDownloadRedirectMethodTimesCalled) {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.ignoreListOfReposAllowedSendRedirectUrl,
                ignoreListOfRepos);
        DownloadRedirectHelper redirectHelper = new DownloadRedirectHelper(mock(CentralConfigService.class));
        DownloadRedirectHelper spyHelper = Mockito.spy(redirectHelper);
        try (MockedStatic<AolUtils> mockedAol = Mockito.mockStatic(AolUtils.class)) {
            mockedAol.when(AolUtils::isAol).thenReturn(true);
            LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Maven);
            LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
            localRepoDescriptor.setType(RepoType.Maven);
            DbLocalRepo<LocalRepoDescriptor, LocalRepoConfig> localRepo = new DbLocalRepo<>(
                    localRepoDescriptor, localRepoConfig, repositoryService, null,
                    mock(CentralConfigService.class));
            spyHelper.isRedirectEnable(localRepo, localRepoDescriptor);
            verify(spyHelper, times(isDownloadRedirectMethodTimesCalled)).isRedirectEnableForThisRepoType(
                    any(Repo.class));
        }
    }

    public static Stream<Arguments> ignoreListOfReposAllowedSendRedirectUrlScenarios() {
        return Stream.of(
                Arguments.of("false", 1), // expects calling isRedirectEnableForThisRepoType
                Arguments.of("true", 0)
        );
    }

    @ParameterizedTest
    @MethodSource("shouldRedirectProvider")
    void testShouldRedirect(boolean headRequest, boolean expectedResult) {
        LocalRepoDescriptor repoDescriptor = mock(LocalRepoDescriptor.class);
        when(repoDescriptor.isReal()).thenReturn(true);
        when(downloadRedirectHelper.isRedirectEnable(any(), any())).thenReturn(true);
        when(downloadRedirectHelper.isArtifactGreaterEqualsRedirectThreshold(any())).thenReturn(true);
        when(binaryService.canCloudProviderGenerateRedirection()).thenReturn(true);
        when(request.isHeadOnly()).thenReturn(headRequest);
        RepoResourceInfo repoResourceInfo = mock(RepoResourceInfo.class);
        when(repoResourceInfo.getSha1()).thenReturn("cf23df2207d99a74fbe169e3eba035e633b65d94");
        when(resource.getInfo()).thenReturn(repoResourceInfo);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Go);
        Repo responseRepo = mock(Repo.class);
        when(responseRepo.getRepoConfig()).thenReturn(repoConfig);
        boolean shouldRedirect = downloadService.shouldRedirect(requestContext, responseRepo, resource,
                repoDescriptor, true);
        assertEquals(shouldRedirect, expectedResult);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testShouldRedirectWithVirtualRepo(boolean virtualDownloadRedirectEnabled) {
        try {
            setStringSystemProperty(ConstantValues.virtualDownloadRedirectEnabled,
                    String.valueOf(virtualDownloadRedirectEnabled));
            VirtualRepoDescriptor repoDescriptor = mock(VirtualRepoDescriptor.class);
            when(repoDescriptor.isReal()).thenReturn(false);
            when(downloadRedirectHelper.isRedirectEnable(any(), any())).thenReturn(true);
            when(downloadRedirectHelper.isArtifactGreaterEqualsRedirectThreshold(any())).thenReturn(true);
            when(binaryService.canCloudProviderGenerateRedirection()).thenReturn(true);
            RepoResourceInfo repoResourceInfo = mock(RepoResourceInfo.class);
            when(repoResourceInfo.getSha1()).thenReturn("cf23df2207d99a74fbe169e3eba035e633b65d94");
            when(resource.getInfo()).thenReturn(repoResourceInfo);
            RepoConfig repoConfig = mock(RepoConfig.class);
            when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
            when(repoConfig.getPackageType()).thenReturn(RepoType.Go);
            Repo responseRepo = mock(Repo.class);
            when(responseRepo.getRepoConfig()).thenReturn(repoConfig);
            boolean shouldRedirect = downloadService.shouldRedirect(requestContext, responseRepo, resource,
                    repoDescriptor, true);
            assertEquals(shouldRedirect, virtualDownloadRedirectEnabled);
        } finally {
            setStringSystemProperty(ConstantValues.virtualDownloadRedirectEnabled, "false");
        }
    }

    @Test
    void testOnContextLoadedNotifyRepoRedirectAllowance() {
        // setup
        LocalRepoConfig gen1 = LocalRepoConfig.create("gen1", RepoType.Generic);
        FederatedRepoConfig gen2 = FederatedRepoConfig.create("gen2", RepoType.Generic);
        when(repositoryService.getLocalRepoConfigsIncludingInfoRepos()).thenReturn(List.of(gen1));
        when(repositoryService.getFederatedRepoConfigs()).thenReturn(List.of(gen2));

        // test
        downloadService.onLicenseLoaded();
        // verify non have redirect enabled by default
        verify(downloadService, times(0)).logWarnRedirectConfigured();

        // setup
        gen1.getRepoTypeConfig().setDownloadRedirect(true);
        // test
        downloadService.onLicenseLoaded();
        // verify non have redirect enabled by default
        verify(downloadService, times(1)).logWarnRedirectConfigured();
        clearInvocations(downloadService);

        // setup
        gen2.getRepoTypeConfig().setDownloadRedirect(true);
        // test
        downloadService.onLicenseLoaded();
        // verify non have redirect enabled by default
        verify(downloadService, times(1)).logWarnRedirectConfigured();
        clearInvocations(downloadService);
    }

    @Test
    void testGetDownloadRequestContext() {
        String debPackage = "cuda-toolkit-config-common_12.1.55-1_all.deb";
        String relativePath = "./" + debPackage;
        RepoPath testRepo = RepoPathFactory.create("testRepo", relativePath);
        InternalArtifactoryRequest artifactoryRequest =
                InternalRequestFactory.createInternalRequestDisableRedirect(testRepo);
        DownloadCtx downloadCtx = mock(DownloadCtx.class);
        when(downloadCtx.isExpired()).thenReturn(false);
        DownloadRequestContext downloadRequestContext =
                downloadService.getDownloadRequestContext(artifactoryRequest, downloadCtx);
        assertNotNull(downloadRequestContext);
        RepoPath repoPath = downloadRequestContext.alterPath(null, testRepo);
        assertEquals(debPackage, repoPath.getPath());
    }

    public static Stream<Arguments> shouldRedirectProvider() {
        return Stream.of(
                Arguments.of(true, false),
                Arguments.of(false, true)
        );
    }

    private void bindRequest(String header, String dockerHeader) {
        RequestWrapper requestWrapper = mock(RequestWrapper.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(requestWrapper.getRequest()).thenReturn(request);
        when(request.getHeader(MANUAL_REDIRECT_TYPE.getHeaderName())).thenReturn(header);
        when(request.getHeader("X-Meta-JFrog-Download-Redirect-To")).thenReturn(dockerHeader);
        RequestThreadLocal.bind(requestWrapper);
    }

    private void setupCdn(boolean withCdnList) {
        if (withCdnList) {
            when(downloadRedirectHelper.isCdnForcedForThisRepoType(any())).thenReturn(true);
        }
        when(downloadRedirectHelper.isRedirectEnable(any(), any())).thenReturn(true);
        when(downloadRedirectHelper.isArtifactGreaterEqualsRedirectThreshold(any())).thenReturn(true);
        when(binaryService.canCloudProviderGenerateRedirection()).thenReturn(true);
        when(aolService.isAol()).thenReturn(true);
    }

    @Test
    void testXrayOriginHeader() throws IOException {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        ArtifactoryResponse response = spy(ArtifactoryResponse.class);
        when(request.getPath()).thenReturn("path");
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginAddon);
        doNothing().when(pluginAddon).execPluginActions(any(), any(), any());
        DownloadRequestContext downloadRequestContext = mock(DownloadRequestContext.class);
        doReturn(downloadRequestContext).when(downloadService).getDownloadRequestContext(any(), any());
        BlockedRepoResource blockedResource = mock(BlockedRepoResource.class);
        when(blockedResource.getBlockedType()).thenReturn(BlockedRepoResource.BlockedType.XRAY);
        doReturn(blockedResource).when(downloadService).getRepoResource(any(), any(), any());
        doNothing().when(downloadService).respond(any(), any(), any(), anyBoolean());

        downloadService.processDownload(request, response);
        verify(response).setHeader(ArtifactoryHeaders.XRAY_ORIGIN, "true");
    }

    @Test
    void testConsumptionSuccessfulDownloadHead() throws IOException {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getPath()).thenReturn("path");
        when(request.getRepoKey()).thenReturn("repo-key");
        when(request.getOriginalRepoPath()).thenReturn(RepoPathFactory.create("test"));


        when(request.isHeadOnly()).thenReturn(Boolean.TRUE);

        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        when(response.isSuccessful()).thenReturn(Boolean.TRUE);

        when(resource.getRepoPath()).thenReturn(RepoPathFactory.create("test"));
        when(resource.getInfo()).thenReturn(mock(MutableRepoResourceInfo.class));
        when(resource.isFound()).thenReturn(Boolean.TRUE);

        Repo repo = mock(Repo.class);
        when(repositoryService.repositoryByKey(any())).thenReturn(repo);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Go);
        when(repo.getRepoConfig()).thenReturn(repoConfig);

        when(addonsManager.addonByType(LeadArtifactDetectorAddon.class)).thenReturn(leadArtifactDetectorAddon);
        when(leadArtifactDetectorAddon.isPackageConsumptionLead(any(), any())).thenReturn(Boolean.TRUE);

        when(addonsManager.addonByType(AnalyticsAddon.class)).thenReturn(analyticsAddon);

        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginAddon);
        doNothing().when(pluginAddon).execPluginActions(any(), any(), any());


        DownloadRequestContext downloadRequestContext = mock(DownloadRequestContext.class);
        doReturn(downloadRequestContext).when(downloadService).getDownloadRequestContext(any(), any());
        doReturn(resource).when(downloadService).getRepoResource(any(), any(), any());
        doNothing().when(downloadService).respond(any(), any(), any(), anyBoolean());

        downloadService.processDownload(request, response);

        verify(unifiedEventService).sendArtifactEvent(
                SupportedUnifiedEvents.BEFORE_DOWNLOAD_REQUEST, request, null);
    }

    @Test
    void testConsumptionSuccessfulDownload() throws IOException {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getPath()).thenReturn("path");
        when(request.getRepoKey()).thenReturn("repo-key");

        doNothing().when(smartRepoService).onFileDownload(any());

        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        when(response.isSuccessful()).thenReturn(Boolean.TRUE);

        when(resource.getRepoPath()).thenReturn(RepoPathFactory.create("test"));
        when(resource.getInfo()).thenReturn(mock(MutableRepoResourceInfo.class));
        when(resource.isFound()).thenReturn(Boolean.TRUE);

        Repo repo = mock(Repo.class);
        when(repositoryService.repositoryByKey(any())).thenReturn(repo);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Go);
        when(repo.getRepoConfig()).thenReturn(repoConfig);

        when(addonsManager.addonByType(LeadArtifactDetectorAddon.class)).thenReturn(leadArtifactDetectorAddon);
        when(leadArtifactDetectorAddon.isPackageConsumptionLead(any(), any())).thenReturn(Boolean.TRUE);

        when(addonsManager.addonByType(AnalyticsAddon.class)).thenReturn(analyticsAddon);

        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginAddon);
        doNothing().when(pluginAddon).execPluginActions(any(), any(), any());


        DownloadRequestContext downloadRequestContext = mock(DownloadRequestContext.class);
        doReturn(downloadRequestContext).when(downloadService).getDownloadRequestContext(any(), any());
        doReturn(resource).when(downloadService).getRepoResource(any(), any(), any());
        doNothing().when(downloadService).respond(any(), any(), any(), anyBoolean());

        downloadService.processDownload(request, response);
    }

    @Test
    void testConsumptionSuccessfulRedirectDownload() throws IOException {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getPath()).thenReturn("path");
        when(request.getRepoKey()).thenReturn("repo-key");

        doNothing().when(smartRepoService).onFileDownload(any());

        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_TEMPORARY_REDIRECT);

        when(resource.getRepoPath()).thenReturn(RepoPathFactory.create("test"));
        when(resource.getInfo()).thenReturn(mock(MutableRepoResourceInfo.class));
        when(resource.isFound()).thenReturn(Boolean.TRUE);

        Repo repo = mock(Repo.class);
        when(repositoryService.repositoryByKey(any())).thenReturn(repo);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Go);
        when(repo.getRepoConfig()).thenReturn(repoConfig);

        when(addonsManager.addonByType(LeadArtifactDetectorAddon.class)).thenReturn(leadArtifactDetectorAddon);
        when(leadArtifactDetectorAddon.isPackageConsumptionLead(any(), any())).thenReturn(Boolean.TRUE);

        when(addonsManager.addonByType(AnalyticsAddon.class)).thenReturn(analyticsAddon);

        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginAddon);
        doNothing().when(pluginAddon).execPluginActions(any(), any(), any());


        DownloadRequestContext downloadRequestContext = mock(DownloadRequestContext.class);
        doReturn(downloadRequestContext).when(downloadService).getDownloadRequestContext(any(), any());
        doReturn(resource).when(downloadService).getRepoResource(any(), any(), any());
        doNothing().when(downloadService).respond(any(), any(), any(), anyBoolean());

        downloadService.processDownload(request, response);
    }

    @Test
    void testOnBoardingNotify() throws IOException {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getPath()).thenReturn("path");
        when(request.getRepoKey()).thenReturn("repo-key");

        doNothing().when(smartRepoService).onFileDownload(any());

        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        when(response.isSuccessful()).thenReturn(Boolean.TRUE);

        when(resource.getRepoPath()).thenReturn(RepoPathFactory.create("pom.xml"));
        when(resource.getPath()).thenReturn("file.pom");
        when(resource.getInfo()).thenReturn(mock(MutableRepoResourceInfo.class));
        when(resource.isFound()).thenReturn(Boolean.TRUE);

        Repo repo = mock(Repo.class);
        when(repositoryService.repositoryByKey(any())).thenReturn(repo);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
        when(repo.getRepoConfig()).thenReturn(repoConfig);
        mockIsRedirectFromCachingProxy(request, false);


        when(addonsManager.addonByType(OnBoardingAddon.class)).thenReturn(onBoardingAddon);
        doNothing().when(onBoardingAddon).userUploadedPackage(any(), any(), any(), any());


        when(addonsManager.addonByType(LeadArtifactDetectorAddon.class)).thenReturn(leadArtifactDetectorAddon);
        when(leadArtifactDetectorAddon.isPackageConsumptionLead(any(), any())).thenReturn(Boolean.TRUE);

        when(addonsManager.addonByType(AnalyticsAddon.class)).thenReturn(analyticsAddon);

        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginAddon);
        doNothing().when(pluginAddon).execPluginActions(any(), any(), any());


        DownloadRequestContext downloadRequestContext = mock(DownloadRequestContext.class);
        doReturn(downloadRequestContext).when(downloadService).getDownloadRequestContext(any(), any());
        doReturn(resource).when(downloadService).getRepoResource(any(), any(), any());
        doNothing().when(downloadService).respond(any(), any(), any(), anyBoolean());

        downloadService.processDownload(request, response);
    }

    @Test
    void testCurationOriginHeader() throws IOException {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        ArtifactoryResponse response = spy(ArtifactoryResponse.class);
        when(request.getPath()).thenReturn("path");
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginAddon);
        doNothing().when(pluginAddon).execPluginActions(any(), any(), any());
        DownloadRequestContext downloadRequestContext = mock(DownloadRequestContext.class);
        doReturn(downloadRequestContext).when(downloadService).getDownloadRequestContext(any(), any());
        BlockedRepoResource blockedResource = mock(BlockedRepoResource.class);
        when(blockedResource.getBlockedType()).thenReturn(BlockedRepoResource.BlockedType.CURATION);
        doReturn(blockedResource).when(downloadService).getRepoResource(any(), any(), any());
        doNothing().when(downloadService).respond(any(), any(), any(), anyBoolean());

        downloadService.processDownload(request, response);
        verify(response).setHeader(ArtifactoryHeaders.CURATION_ORIGIN, "true");
    }

    @Test
    void testRedirectedFromProxyCacheSkipDownloadInterceptors() throws IOException {
        // given
        mockIsRedirectFromCachingProxy(request, true);
        ArtifactoryResponse response = mockPositivePath();

        // when
        downloadService.processDownload(request, response);

        // then
        verify(downloadService, times(0)).runWorker(SupportedUnifiedEvents.AFTER_DOWNLOAD,
                request, response, null, null);
        verify(pluginAddon, times(0)).execPluginActions(any(), any(), any());
        verify(smartRepoService, times(0)).onFileDownload(any());
        verify(analyticsAddon, times(0)).trackSuccessfulPackageDownload(any());
        verify(statsService, times(0)).updateDownloadStatsIfNeeded(any(RepoPath.class), any(Request.class),
                anyBoolean());
        verify(trafficService, times(0)).handleTrafficEntry(any(LegacyTrafficEntry.class));
        verify(trafficService, times(0)).handleTrafficEntry(any(TrafficEntry.class));
        verifyNoInteractions(unifiedEventService);
    }

    private ArtifactoryResponse mockPositivePath() throws IOException {
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginAddon);
        when(aolService.isAol()).thenReturn(false);
        when(downloadRedirectHelper.isRedirectEnable(any(), any())).thenReturn(true);
        when(request.getPath()).thenReturn("path");
        doReturn(false).when(request).isHeadOnly();

        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        when(repoDescriptor.isReal()).thenReturn(true);
        Repo<RepoDescriptor, RepoConfig> repo = mock(Repo.class);
        when(repo.getDescriptor()).thenReturn(repoDescriptor);
        when(repo.getRepoConfig()).thenReturn(mock(RepoConfig.class));
        when(repositoryService.repositoryByKey(any())).thenReturn(repo);

        when(leadArtifactDetectorAddon.isPackageConsumptionLead(any(), any())).thenReturn(Boolean.TRUE);
        when(addonsManager.addonByType(AnalyticsAddon.class)).thenReturn(analyticsAddon);

        DownloadRequestContext downloadRequestContext = mock(DownloadRequestContext.class);
        when(downloadRequestContext.getRequest()).thenReturn(request);
        doReturn(downloadRequestContext).when(downloadService).getDownloadRequestContext(any(), any());

        RepoResource repoResource = mock(RepoResource.class);
        doReturn(true).when(repoResource).isFound();
        when(repoResource.getRepoPath()).thenReturn(RepoPathFactory.create("repoKey", "path"));
        when(repoResource.getResponseRepoPath()).thenReturn(
                RepoPathFactory.create("responseRepoKey", "responseRepoPath"));
        when(repoResource.getInfo()).thenReturn(mock(MutableRepoResourceInfo.class));
        doReturn(repoResource).when(downloadService).getRepoResource(any(), any(), any());

        when(altResponseHelper.getAlternateHandle(any(), any(), any())).thenReturn(mock(ResourceStreamHandle.class));

        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        when(response.isSuccessful()).thenReturn(Boolean.TRUE);
        return response;
    }

    @ParameterizedTest
    @ValueSource(ints = {SC_FORBIDDEN, SC_BAD_REQUEST, SC_INTERNAL_SERVER_ERROR, SC_SERVICE_UNAVAILABLE})
    void testHandleRemoteRepositoryFailure(int responseStatusCode) throws IOException, RepoRejectException {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        ArtifactoryResponse response = spy(ArtifactoryResponse.class);
        RepoPath repoPath = RepoPathFactory.create("repo-test", "/file.jar");
        when(request.getPath()).thenReturn(repoPath.getPath());
        when(request.getRepoKey()).thenReturn(repoPath.getRepoKey());
        when(request.shouldSkipPackageUsageTrack()).thenReturn(true);
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginAddon);
        doNothing().when(pluginAddon).execPluginActions(any(), any(), any());
        when(addonsManager.addonByType(WorkersAddon.class)).thenReturn(workersAddon);
        doNothing().when(workersAddon).run(any(), any(), any());
        HttpRepo httpRepo = mock(HttpRepo.class);
        when(repositoryService.repositoryByKey(repoPath.getRepoKey())).thenReturn(httpRepo);
        when(httpRepo.getRepoConfig()).thenReturn(mock(RemoteRepoConfig.class));
        doThrow(new RemoteRequestException("error from remote repository", responseStatusCode, ""))
                .when(repositoryService).getResourceStreamHandle(any(), any(), any());
        DownloadRequestContext downloadRequestContext = mock(DownloadRequestContext.class);
        when(downloadRequestContext.getRequest()).thenReturn(request);
        doReturn(downloadRequestContext).when(downloadService).getDownloadRequestContext(any(), any());
        RemoteRepoResource remoteRepoResource = mock(RemoteRepoResource.class);
        when(remoteRepoResource.getRepoPath()).thenReturn(repoPath);
        when(remoteRepoResource.getResponseRepoPath()).thenReturn(repoPath);
        when(remoteRepoResource.isFound()).thenReturn(true);
        doReturn(remoteRepoResource).when(downloadService).getRepoResource(any(), any(), any());
        downloadService.init();
        DispatcherResponse dispatcherResponse = DispatcherResponse.builder()
                .shouldProceed(true)
                .build();
        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null))
                .thenReturn(dispatcherResponse);

        downloadService.processDownload(request, response);

        verify(response).setHeader(ArtifactoryHeaders.THIRD_PARTY_ORIGIN, "true");
        int expectedStatusCode = responseStatusCode >= 500 ? SC_BAD_GATEWAY : responseStatusCode;
        verify(response).sendError(eq(expectedStatusCode), anyString(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("testAppendHeadersTests")
    void testAppendHeaders(DownloadCtx downloadCtx, ArtifactoryResponse response, int headerCount) {
        downloadService.appendHeaders(downloadCtx, response);
        doNothing().when(response).setHeader(anyString(), anyString());
        verify(response, times(headerCount)).setHeader(anyString(), anyString());
    }

    private static Stream<Arguments> testAppendHeadersTests() {
        DownloadCtx emptyHeadersDownloadCtx = new DownloadCtx();
        emptyHeadersDownloadCtx.setHeaders(Map.of());
        DownloadCtx downloadCtx = new DownloadCtx();
        downloadCtx.setHeaders(Map.of("foo", "bar"));
        return Stream.of(
                Arguments.of(new DownloadCtx(), mock(ArtifactoryResponse.class), 0),
                Arguments.of(emptyHeadersDownloadCtx, mock(ArtifactoryResponse.class), 0),
                Arguments.of(downloadCtx, mock(ArtifactoryResponse.class), 1)
        );
    }

    @ParameterizedTest
    @ValueSource(chars = {'?', '*', '\\', '|', '"'})
    void testDownloadWithInvalidCharsInRemoteArtifactName(char invalidChar) throws IOException {
        String filePath = "com/external/file%c.jar".formatted(invalidChar);
        RepoPath repoPath = RepoPathFactory.create("remote-repo", filePath);

        RemoteRepoResource remoteRepoResource = mock(RemoteRepoResource.class);
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);

        when(requestContext.getRequest()).thenReturn(request);
        when(remoteRepoResource.isFound()).thenReturn(true);
        when(remoteRepoResource.isBlocked()).thenReturn(false);
        when(request.isHeadOnly()).thenReturn(false);
        when(request.isChecksum()).thenReturn(false);
        when(remoteRepoResource.getRepoPath()).thenReturn(repoPath);
        when(repositoryService.existsByType(REMOTE, "remote-repo")).thenReturn(true);
        when(request.getRepoPath()).thenReturn(repoPath);

        downloadService.respond(requestContext, response, remoteRepoResource, true);

        verify(response).sendError(HttpStatus.SC_BAD_REQUEST,
                "Invalid path exception occurred while sending request response: Invalid path." +
                        " '%c' is not a valid name character: %s".formatted(invalidChar, filePath));
    }

    /**
     * Regression: paths that contain BOTH an invalid path character (so the
     * {@code respondFoundResource} catch block fires with {@link java.nio.file.InvalidPathException}) AND
     * literal {@code %} sequences (e.g. URL-decoded {@code %2F}, {@code %s}) used to surface as HTTP 500
     * instead of the intended 400.
     * <p>
     * Why: the catch block builds {@code message = "...: " + e.getMessage()} where {@code e.getMessage()}
     * embeds the original path (per {@link java.nio.file.InvalidPathException#getMessage()}). Before the
     * fix the message was passed as the {@code format} argument to {@code RepoRequests.logToContext}, which
     * delegated to {@link String#format(String, Object[])}; that threw
     * {@link java.util.UnknownFormatConversionException} / {@link java.util.MissingFormatArgumentException}
     * before {@code response.sendError(400, ...)} could run.
     * <p>
     * We seed the {@link RepoRequests} thread-local with a real (mock) context here because without it
     * {@code logToContext} short-circuits and the bug cannot be reproduced.
     */
    @ParameterizedTest
    @MethodSource("invalidPathsWithLiteralPercentDataProvider")
    void testDownloadWithInvalidCharsAndLiteralPercentSequences(String filePath) throws IOException {
        RepoRequestContext mockContext = mock(RepoRequestContext.class);
        setRepoRequestsThreadLocal(mockContext);
        try {
            RepoPath repoPath = RepoPathFactory.create("remote-repo", filePath);

            RemoteRepoResource remoteRepoResource = mock(RemoteRepoResource.class);
            ArtifactoryResponse response = mock(ArtifactoryResponse.class);

            when(requestContext.getRequest()).thenReturn(request);
            when(remoteRepoResource.isFound()).thenReturn(true);
            when(remoteRepoResource.isBlocked()).thenReturn(false);
            when(request.isHeadOnly()).thenReturn(false);
            when(request.isChecksum()).thenReturn(false);
            when(remoteRepoResource.getRepoPath()).thenReturn(repoPath);
            when(repositoryService.existsByType(REMOTE, "remote-repo")).thenReturn(true);
            when(request.getRepoPath()).thenReturn(repoPath);

            // must not throw an IllegalFormatException - i.e. must reach the sendError(400, ...) below
            downloadService.respond(requestContext, response, remoteRepoResource, true);

            verify(response).sendError(eq(HttpStatus.SC_BAD_REQUEST),
                    contains("Invalid path exception occurred while sending request response"));
        } finally {
            clearRepoRequestsThreadLocal();
        }
    }

    private static Stream<Arguments> invalidPathsWithLiteralPercentDataProvider() {
        return Stream.of(
                // exact prod scenario from Coralogix: URL-decoded path with %2F sequences (%2F -> conversion 'F'
                // does not exist -> UnknownFormatConversionException without the fix)
                Arguments.of("com/external/login?return_to=%2F%2Fexample.com"),
                // valid-looking conversion specifier %s with no args -> MissingFormatArgumentException without fix
                Arguments.of("com/external/login?download=%s"),
                // malformed percent escape combined with another invalid char -> UnknownFormatConversionException
                Arguments.of("com/external/path*name=%ZZ"),
                // multiple % sequences with backslash invalid char
                Arguments.of("com\\file%2F%252F%s.jar"),
                // double-encoded %25 unfolds to %25 in the path itself, also problematic
                Arguments.of("com/external/file?q=%25"),
                // literal %n inside the path - %n is a platform-specific newline conversion in String.format
                Arguments.of("com/external/file?q=%n")
        );
    }

    @SuppressWarnings("unchecked")
    private static void setRepoRequestsThreadLocal(RepoRequestContext context) {
        try {
            Field field = RepoRequests.class.getDeclaredField("context");
            field.setAccessible(true);
            ThreadLocal<RepoRequestContext> threadLocal = (ThreadLocal<RepoRequestContext>) field.get(null);
            threadLocal.set(context);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not seed RepoRequests thread-local for test", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearRepoRequestsThreadLocal() {
        try {
            Field field = RepoRequests.class.getDeclaredField("context");
            field.setAccessible(true);
            ThreadLocal<RepoRequestContext> threadLocal = (ThreadLocal<RepoRequestContext>) field.get(null);
            threadLocal.remove();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not clear RepoRequests thread-local after test", e);
        }
    }

    @Test
    void testDownloadWithRelativePathInRemoteArtifactName() throws IOException {
        String fileRelativePath = "./com/external/file.jar";
        RepoPath repoPath = RepoPathFactory.create("remote-repo", fileRelativePath);

        RemoteRepoResource remoteRepoResource = mock(RemoteRepoResource.class);
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);

        when(requestContext.getRequest()).thenReturn(request);
        when(remoteRepoResource.isFound()).thenReturn(false);
        when(remoteRepoResource.isBlocked()).thenReturn(false);
        when(request.isHeadOnly()).thenReturn(false);
        when(request.isChecksum()).thenReturn(false);
        when(remoteRepoResource.getRepoPath()).thenReturn(RepoPathFactory.create("remote-repo", ""));
        when(repositoryService.existsByType(REMOTE, "remote-repo")).thenReturn(true);
        when(request.getRepoPath()).thenReturn(repoPath);
        DispatcherResponse dispatcherResponse = DispatcherResponse.builder()
                .shouldProceed(true)
                .data(null)
                .build();
        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null))
                .thenReturn(dispatcherResponse);

        downloadService.respond(requestContext, response, remoteRepoResource, true);

        Logger log = LoggerFactory.getLogger(DownloadServiceImpl.class);

        verify(response).sendError(HttpStatus.SC_NOT_FOUND, "Resource not found; Path: 'remote-repo:'", log,
                new ArtifactoryResourceInfo(false, false, false));
    }

    @Test
    void testUnfoundRepoResourceErrorPresentsPath() throws IOException {
        RepoPath repoPath = RepoPathFactory.create("remote-repo", "/path/to/file.jar");

        UnfoundRepoResource unfoundRepoResource = new UnfoundRepoResource(repoPath, "Not Found",
                HttpStatus.SC_NOT_FOUND);
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        when(requestContext.getRequest()).thenReturn(request);
        when(request.isHeadOnly()).thenReturn(false);
        when(request.isChecksum()).thenReturn(true);
        when(repositoryService.existsByType(REMOTE, "remote-repo")).thenReturn(true);
        when(request.getRepoPath()).thenReturn(repoPath);
        doReturn(false).when(downloadService).shouldHideUnauthorisedResponse(requestContext);
        DispatcherResponse dispatcherResponse = DispatcherResponse.builder()
                .shouldProceed(true)
                .data(null)
                .build();

        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.AFTER_DOWNLOAD_ERROR, request, null))
                .thenReturn(dispatcherResponse);
        downloadService.respond(requestContext, response, unfoundRepoResource, true);

        Logger log = LoggerFactory.getLogger(DownloadServiceImpl.class);
        verify(response).sendError(HttpStatus.SC_NOT_FOUND,
                "Not Found; Path: 'remote-repo:path/to/file.jar'", log,
                new ArtifactoryResourceInfo(false, false, false));
    }

    @Test
    void respondFoundResource_shouldUpdateDownloadPerfCounters() throws IOException {
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        when(requestContext.getRequest()).thenReturn(request);

        // Bind HTTP request to thread local storage (download service will keep perf counters on it)
        RequestWrapper requestWrapper = mock(RequestWrapper.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(requestWrapper.getRequest()).thenReturn(httpRequest);
        RequestThreadLocal.bind(requestWrapper);

        RepoPath repoPath = RepoPathFactory.create("repo", "path/file.tzt");
        FileInfoImpl fileIndo = new FileInfoImpl(repoPath);
        fileIndo.setSize(1025);
        FileResource resource = new FileResource(fileIndo);
        // A shortcut: mimic ready-to-use response to avoid mocking more methods
        when(altResponseHelper.alternateResponse(requestContext, response, repoPath, 0))
                .thenReturn(mock());
        // Act
        downloadService.respond(requestContext, response, resource, false);

        // Assert
        ArgumentCaptor<DownloadPerfCounters> captor = ArgumentCaptor.forClass(DownloadPerfCounters.class);
        verify(httpRequest).setAttribute(eq(DownloadPerfCounters.class.getName()), captor.capture());
        DownloadPerfCounters counters = captor.getValue();
        assertEquals(1025L, counters.getArtifactSizeBytes());

        RequestThreadLocal.unbind();
    }


    @Test
    void curationCallForCachedRepo() {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getPath()).thenReturn("path");
        when(request.getRepoKey()).thenReturn("repo-key");

        LocalCacheRepoDescriptor repoDescriptor = mock(LocalCacheRepoDescriptor.class);
        when(repoDescriptor.getType()).thenReturn(RepoType.Maven);

        FileResource repoResource = mock(FileResource.class);
        when(repoResource.getRepoKey()).thenReturn("repo-key-cache");

        RemoteRepo remoteRepo = mock(RemoteRepo.class);

        DbCacheRepo repository = mock(DbCacheRepo.class);
        when(repository.getDescriptor()).thenReturn(repoDescriptor);
        when(repository.getInfo(any())).thenReturn(repoResource);
        when((repository.getRemoteRepo())).thenReturn(remoteRepo);

        when(repositoryService.remoteRepositoryByKey("repo-key")).thenReturn(remoteRepo);

        DownloadRequestContext requestContext = new DownloadRequestContext(mock(ArtifactoryRequest.class));

        downloadService.getRepoResource(request, repository, requestContext);

        verify(remoteRepo).checkByCuration(any(), any());
    }

    @Test
    void shouldBlockSystemFolderDownload_remoteFlowJfrogNotGoverned_doesNotBlock() {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoKey()).thenReturn("my-remote");
        when(request.getRepoPath()).thenReturn(RepoPathFactory.create("my-remote", ".jfrog/index.json"));
        when(repositoryService.isRemoteOrCacheFlow("my-remote")).thenReturn(true);

        assertFalse(downloadService.shouldBlockSystemFolderDownload(request));
    }

    @Test
    void shouldBlockSystemFolderDownload_remoteFlowDownloadAllowedFolder_isAllowed() {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoKey()).thenReturn("my-remote");
        when(request.getRepoPath()).thenReturn(RepoPathFactory.create("my-remote", ".evidence/sha256/sig.json"));
        when(repositoryService.isRemoteOrCacheFlow("my-remote")).thenReturn(true);

        assertFalse(downloadService.shouldBlockSystemFolderDownload(request));
    }

    @Test
    void shouldBlockSystemFolderDownload_localFlow_keepsMasterBehaviorAndDoesNotBlock() {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoKey()).thenReturn("my-local");
        when(repositoryService.isRemoteOrCacheFlow("my-local")).thenReturn(false);

        assertFalse(downloadService.shouldBlockSystemFolderDownload(request));
    }

    @Test
    void shouldBlockSystemFolderDownload_internalRequest_isExempt() {
        InternalArtifactoryRequest request = mock(InternalArtifactoryRequest.class);

        assertFalse(downloadService.shouldBlockSystemFolderDownload(request));
    }

    @Test
    void shouldBlockSystemFolderDownload_contractDisabled_neverBlocksAndSkipsContract() {
        homeStub.setProperty(systemFolderContractEnabled, "false");
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);

        assertFalse(downloadService.shouldBlockSystemFolderDownload(request));
        verifyNoInteractions(repositoryService);
    }

    @Test
    void shouldBlockSystemFolderDownload_remoteFlowNonSystemPath_doesNotBlock() {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoKey()).thenReturn("my-remote");
        when(request.getRepoPath()).thenReturn(RepoPathFactory.create("my-remote", "com/foo/bar.jar"));
        when(repositoryService.isRemoteOrCacheFlow("my-remote")).thenReturn(true);

        assertFalse(downloadService.shouldBlockSystemFolderDownload(request));
    }

    @Test
    void isRemoteOrCacheFlow_isTrueForRemoteAndCacheKeysOnly() {
        InternalRepositoryService service = mock(InternalRepositoryService.class);
        when(service.isRemoteOrCacheFlow(anyString())).thenCallRealMethod();

        LocalRepo<?, ?> cacheRepo = mock(LocalRepo.class);
        when(cacheRepo.isCache()).thenReturn(true);
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.isCache()).thenReturn(false);
        when(service.localFederatedOrCachedRepositoryByKey("remote-or-cache")).thenReturn(cacheRepo);
        when(service.localFederatedOrCachedRepositoryByKey("local")).thenReturn(localRepo);
        when(service.localFederatedOrCachedRepositoryByKey("virtual")).thenReturn(null);

        assertTrue(service.isRemoteOrCacheFlow("remote-or-cache"));
        assertFalse(service.isRemoteOrCacheFlow("local"));
        assertFalse(service.isRemoteOrCacheFlow("virtual"));
    }

    @Test
    void noCurationCallForNotCachedRepo() {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);

        LocalCacheRepoDescriptor repoDescriptor = mock(LocalCacheRepoDescriptor.class);
        when(repoDescriptor.getType()).thenReturn(RepoType.Maven);

        RemoteRepo remoteRepo = mock(RemoteRepo.class);

        DbLocalRepo repository = mock(DbLocalRepo.class);
        when(repository.getDescriptor()).thenReturn(repoDescriptor);

        DownloadRequestContext requestContext = new DownloadRequestContext(mock(ArtifactoryRequest.class));

        downloadService.getRepoResource(request, repository, requestContext);

        verify(remoteRepo, times(0)).checkByCuration(any(), any());
    }

    @Test
    void testReportRedirectInfo() {
        RequestWrapper requestWrapper = mock(RequestWrapper.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(requestWrapper.getRequest()).thenReturn(httpRequest);
        try {
            RequestThreadLocal.bind(requestWrapper);
            downloadService.reportRedirectInfo(null);
            verifyNoInteractions(requestWrapper);

            downloadService.reportRedirectInfo(RedirectType.DIRECT);
            verifyNoInteractions(requestWrapper);

            downloadService.reportRedirectInfo(RedirectType.S3);
            verify(httpRequest).getAttribute(RedirectInfo.class.getName());
            RedirectInfo expectedRedirectInfo = new RedirectInfo();
            expectedRedirectInfo.setRedirectTarget(RedirectType.S3.toString().toLowerCase());
            verify(httpRequest).setAttribute(eq(RedirectInfo.class.getName()), eq(expectedRedirectInfo));

            downloadService.reportControlledCacheRedirect();
            expectedRedirectInfo.setRedirectTarget("cc");
            verify(httpRequest, times(2)).getAttribute(RedirectInfo.class.getName());
            verify(httpRequest).setAttribute(eq(RedirectInfo.class.getName()), eq(expectedRedirectInfo));
        } finally {
            RequestThreadLocal.unbind();
        }
    }

    @Test
    void sendStreamOrRedirect_whenControlledCacheShouldRedirect_takesPrecedenceOverSignedUrl() throws IOException {
        // given: controlled cache wants to redirect AND a signed URL is available (cloud-backed)
        when(controlledCacheRedirectService.shouldRedirect(any(), any(), any())).thenReturn(true);
        URI ccRedirect = URI.create("/artifactory/repoKey/path?key=sha1");
        when(controlledCacheRedirectService.createRedirectUri(any())).thenReturn(ccRedirect);
        downloadService.trafficService = trafficService;

        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path");
        when(resource.getInfo()).thenReturn(mock(RepoResourceInfo.class));
        when(resource.getSize()).thenReturn(100L);
        Repo<RepoDescriptor, RepoConfig> responseRepo = mock(Repo.class);

        String signedUrl = "https://signed.example.com/foo";
        downloadService.sendStreamOrRedirect(requestContext, response, resource, repoPath,
                mock(ResourceStreamHandle.class), signedUrl, request, responseRepo, RedirectType.S3, true);

        // controlled cache path was taken; cloud-backed → strategy is "redirect" because the
        // subsequent download request will return another 302 to the cloud signed URL.
        verify(response).setHeader(X_JFROG_CONTROLLED_CACHE_RESPONSE, JFROG_CONTROLLED_CACHE_RESPONSE_REDIRECT_VALUE);
        verify(response).setRedirect(ccRedirect.toString());
        // signed URL path was NOT taken
        verify(response, times(0)).setRedirect(signedUrl);
    }

    @Test
    void sendStreamOrRedirect_whenControlledCacheShouldRedirectAndDirectBackend_setsStreamStrategy()
            throws IOException {
        // given: controlled cache wants to redirect, no signed URL (filesystem-backed)
        when(controlledCacheRedirectService.shouldRedirect(any(), any(), any())).thenReturn(true);
        URI ccRedirect = URI.create("/artifactory/repoKey/path?key=sha1");
        when(controlledCacheRedirectService.createRedirectUri(any())).thenReturn(ccRedirect);
        downloadService.trafficService = trafficService;

        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path");
        when(resource.getInfo()).thenReturn(mock(RepoResourceInfo.class));
        when(resource.getSize()).thenReturn(100L);
        Repo<RepoDescriptor, RepoConfig> responseRepo = mock(Repo.class);

        downloadService.sendStreamOrRedirect(requestContext, response, resource, repoPath,
                mock(ResourceStreamHandle.class), null, request, responseRepo, RedirectType.DIRECT, true);

        // controlled cache path was taken; DIRECT (filesystem) → strategy is "stream" because
        // the subsequent download request will stream the body back from Artifactory.
        verify(response).setHeader(X_JFROG_CONTROLLED_CACHE_RESPONSE, JFROG_CONTROLLED_CACHE_RESPONSE_STREAM_VALUE);
        verify(response).setRedirect(ccRedirect.toString());
    }

    @ParameterizedTest
    @EnumSource(value = RedirectType.class, names = {"S3", "CLOUD_FRONT", "GCS", "ABS"})
    void sendStreamOrRedirect_whenControlledCacheShouldRedirectAndCloudBackend_setsRedirectStrategy(
            RedirectType redirectType) throws IOException {
        when(controlledCacheRedirectService.shouldRedirect(any(), any(), any())).thenReturn(true);
        URI ccRedirect = URI.create("/artifactory/repoKey/path?key=sha1");
        when(controlledCacheRedirectService.createRedirectUri(any())).thenReturn(ccRedirect);
        downloadService.trafficService = trafficService;

        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path");
        when(resource.getInfo()).thenReturn(mock(RepoResourceInfo.class));
        when(resource.getSize()).thenReturn(100L);
        Repo<RepoDescriptor, RepoConfig> responseRepo = mock(Repo.class);

        downloadService.sendStreamOrRedirect(requestContext, response, resource, repoPath,
                mock(ResourceStreamHandle.class), "https://signed.example.com/foo", request, responseRepo,
                redirectType, true);

        verify(response).setHeader(X_JFROG_CONTROLLED_CACHE_RESPONSE, JFROG_CONTROLLED_CACHE_RESPONSE_REDIRECT_VALUE);
    }

    @Test
    void sendStreamOrRedirect_whenControlledCacheShouldNotRedirectAndSignedUrlPresent_redirectsToSignedUrl()
            throws IOException {
        when(controlledCacheRedirectService.shouldRedirect(any(), any(), any())).thenReturn(false);
        downloadService.trafficService = trafficService;

        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path");
        when(resource.getInfo()).thenReturn(mock(RepoResourceInfo.class));
        when(resource.getSize()).thenReturn(100L);
        Repo<RepoDescriptor, RepoConfig> responseRepo = mock(Repo.class);
        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        when(responseRepo.getDescriptor()).thenReturn(repoDescriptor);
        when(repoDescriptor.isReal()).thenReturn(true);

        String signedUrl = "https://signed.example.com/foo";
        downloadService.sendStreamOrRedirect(requestContext, response, resource, repoPath,
                null, signedUrl, request, responseRepo, RedirectType.S3, true);

        verify(response).setRedirect(signedUrl);
        verify(response, times(0)).setHeader(eq(X_JFROG_CONTROLLED_CACHE_RESPONSE), anyString());
    }

    @Test
    void testIsNotModifiedWithNoConditionalHeaders() {
        when(request.hasIfModifiedSince()).thenReturn(false);
        when(request.hasIfNoneMatch()).thenReturn(false);

        final boolean result = downloadService.isNotModified(request, resource);
        assertFalse(result);
    }

    @Test
    void testIsNotModifiedWhenIfModifiedSinceReturnsFalse() {
        final long resourceLastModified = 1000L;

        when(request.hasIfModifiedSince()).thenReturn(true);
        when(request.hasIfNoneMatch()).thenReturn(false);
        when(request.isNewerThan(resourceLastModified)).thenReturn(true);
        when(resource.getLastModified()).thenReturn(resourceLastModified);

        final boolean result = downloadService.isNotModified(request, resource);
        assertTrue(result);
    }

    @Test
    void testIsNotModifiedWhenIfModifiedSinceReturnsTrue() {
        final long resourceLastModified = 1000L;

        when(request.hasIfModifiedSince()).thenReturn(true);
        when(request.hasIfNoneMatch()).thenReturn(false);
        when(request.isNewerThan(resourceLastModified)).thenReturn(false);
        when(resource.getLastModified()).thenReturn(resourceLastModified);

        final boolean result = downloadService.isNotModified(request, resource);
        assertFalse(result);
    }

    @Test
    void testIsNotModifiedWhenIfNoneMatchReturnsTrue() {
        when(request.hasIfModifiedSince()).thenReturn(false);
        when(request.hasIfNoneMatch()).thenReturn(true);
        when(downloadService.isNonMatch(request, resource)).thenReturn(false);

        final boolean result = downloadService.isNotModified(request, resource);
        assertTrue(result);
    }

    @Test
    void testIsNotModifiedWhenIfNoneMatchReturnsFalse() {
        when(request.hasIfModifiedSince()).thenReturn(false);
        when(request.hasIfNoneMatch()).thenReturn(true);
        when(downloadService.isNonMatch(request, resource)).thenReturn(true);

        final boolean result = downloadService.isNotModified(request, resource);
        assertFalse(result);
    }

    @Test
    void sendUnifiedEvent_shouldSetHeaders_whenResponseHasHeaders() {
        DownloadCtx downloadCtx = new DownloadCtx();
        Map<String, String> expectedHeaders = Map.of("X-Custom-Header", "value");
        RepoPath modifiedRepoPath = RepoPathFactory.create("modified-repo", "modified/path");

        DispatcherResponse response = DispatcherResponse.builder()
                .shouldProceed(true)
                .data(ResponseContextData.builder()
                        .headers(expectedHeaders)
                        .modifiedRepoPath(modifiedRepoPath)
                        .isExpired(true)
                        .build())
                .build();
        when(unifiedEventService.sendArtifactEvent(eq(SupportedUnifiedEvents.BEFORE_DOWNLOAD), any(),
                any())).thenReturn(response);

        downloadService.sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, request,
                RepoPathFactory.create("repo", "path"), downloadCtx);

        assertEquals(expectedHeaders, downloadCtx.getHeaders());
        assertEquals(modifiedRepoPath, downloadCtx.getModifiedRepoPath());
        assertTrue(downloadCtx.isExpired());
    }

    @Test
    void sendUnifiedEvent_shouldNotSetHeaders_whenResponseDataIsNull() {
        DownloadCtx downloadCtx = new DownloadCtx();
        DispatcherResponse response = DispatcherResponse.builder()
                .shouldProceed(true)
                .data(null)
                .build();
        when(unifiedEventService.sendArtifactEvent(eq(SupportedUnifiedEvents.BEFORE_DOWNLOAD), any(),
                any())).thenReturn(response);
        downloadService.sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, request,
                RepoPathFactory.create("repo", "path"), downloadCtx);
        assertTrue(downloadCtx.getHeaders() == null || downloadCtx.getHeaders().isEmpty());
    }

    @Test
    void sendUnifiedEvent_shouldThrowWorkerStopException_whenUnifiedEventExceptionWithConflict() {
        DownloadCtx downloadCtx = new DownloadCtx();
        when(unifiedEventService.sendArtifactEvent(eq(SupportedUnifiedEvents.BEFORE_DOWNLOAD), any(), any()))
                .thenThrow(new UnifiedEventException("Conflict error"));

        WorkerStopException exception = assertThrows(WorkerStopException.class,
                () -> downloadService.sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, request,
                        RepoPathFactory.create("repo", "path"), downloadCtx));
        assertTrue(exception.getMessage().contains("Conflict error"));
    }

    @Test
    void sendUnifiedEvent_shouldRethrowUnifiedEventException_whenNonConflictStatusCode() {
        DownloadCtx downloadCtx = new DownloadCtx();
        UnifiedEventException originalException =
                new UnifiedEventException(HttpStatus.SC_BAD_REQUEST, false, "Bad request error");
        when(unifiedEventService.sendArtifactEvent(eq(SupportedUnifiedEvents.BEFORE_DOWNLOAD), any(), any())).thenThrow(
                originalException);

        UnifiedEventException exception = assertThrows(UnifiedEventException.class,
                () -> downloadService.sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, request,
                        RepoPathFactory.create("repo", "path"), downloadCtx));
        assertEquals(originalException, exception);
    }

}

