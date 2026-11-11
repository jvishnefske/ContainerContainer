package org.artifactory.engine;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.leadartifactdetector.LeadArtifactDetectorAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.security.SecurityService;
import org.artifactory.application.service.CommitContextPropertiesProvider;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.SaveResourceContext;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.security.AnonymousAuthenticationToken;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import static org.artifactory.common.ConstantValues.applicationServiceFeatureEnabled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Igor Usenko
 */
class UploadServiceApplicationIdTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private SecurityService securityServiceMock;
    @Mock
    private InternalRepositoryService repoServiceMock;
    @Mock
    private CommitContextPropertiesProvider commitContextPropertiesProviderMock;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    private static final String TEST_DATA = """           
            IS_LEAD,    SCOPE,                              APP_ID_PROPERTY_ADDED
            false,      application:appKey/bindings/**:x,   false
            true,       application:appKey/bindings/**:x,   true
            false,      applied-permissions/user,           false
            true,       applied-permissions/user,           false
            """;

    @CsvSource(useHeadersInDisplayName = true, textBlock = TEST_DATA)
    @ParameterizedTest
    void whenFeatureFlagIsTrue(boolean isLead, String scope, boolean appIdPropertyAdded) throws RepoRejectException,
            IOException {
        homeStub.setProperty(applicationServiceFeatureEnabled, "true");

        setupTokenAuthenticationWithScope(scope);
        String actualAppIdProperty = uploadArtifactForApplicationIdProperty(isLead, setupLocalRepo());

        assertEquals(appIdPropertyAdded, StringUtils.isNotBlank(actualAppIdProperty));
    }

    @CsvSource(useHeadersInDisplayName = true, textBlock = TEST_DATA)
    @ParameterizedTest
    void whenFeatureFlagIsFalse(boolean isLead, String scope) throws RepoRejectException, IOException {
        homeStub.setProperty(applicationServiceFeatureEnabled, "false");

        setupTokenAuthenticationWithScope(scope);
        String actualAppIdProperty = uploadArtifactForApplicationIdProperty(isLead, setupLocalRepo());

        assertTrue(StringUtils.isBlank(actualAppIdProperty));
    }

    @CsvSource(useHeadersInDisplayName = true, textBlock = TEST_DATA)
    @ParameterizedTest
    void whenDifferentAuthenticationTypeIsUsed(boolean isLead, String scope) throws RepoRejectException, IOException {
        homeStub.setProperty(applicationServiceFeatureEnabled, "true");

        setupAnonymousAuthentication();
        String actualAppIdProperty = uploadArtifactForApplicationIdProperty(isLead, setupLocalRepo());

        assertTrue(StringUtils.isBlank(actualAppIdProperty));
    }

    @CsvSource(useHeadersInDisplayName = true, textBlock = TEST_DATA)
    @ParameterizedTest
    void whenRepoConfigIsNull(boolean isLead, String scope) throws RepoRejectException, IOException {
        homeStub.setProperty(applicationServiceFeatureEnabled, "true");

        setupAnonymousAuthentication();
        String actualAppIdProperty = uploadArtifactForApplicationIdProperty(isLead, mock(LocalRepo.class));

        assertTrue(StringUtils.isBlank(actualAppIdProperty));
    }

    private String uploadArtifactForApplicationIdProperty(boolean isLead, LocalRepo localRepo) throws IOException,
            RepoRejectException {
        RepoResource resource = createRepoResource();
        UploadServiceImpl uploadService = setupUploadService(setupLeadArtifactDetectorAddon(isLead), resource);

        uploadService.uploadItemWithContent(createArtifactoryRequest(resource), createArtifactoryResponse(),
                localRepo, resource, null);

        ArgumentCaptor<SaveResourceContext> contextCaptor = ArgumentCaptor.forClass(SaveResourceContext.class);
        verify(repoServiceMock, times(1)).saveResource(any(), contextCaptor.capture());
        SaveResourceContext context = contextCaptor.getValue();
        Properties properties = context.getProperties();

        return properties == null ? null : properties.getFirst("application.id");
    }

    private static RepoResource createRepoResource() {
        RepoResource res = mock(RepoResource.class);
        when(res.isFound()).thenReturn(true);
        return res;
    }

    private ArtifactoryRequest createArtifactoryRequest(RepoResource resource) {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        RepoPath repoPath = new RepoPathImpl("repoKey", "path");
        FileInfo repoResourceInfo = new FileInfoImpl(repoPath);
        when(repoServiceMock.getFileInfo(any())).thenReturn(repoResourceInfo);
        when(resource.getRepoPath()).thenReturn(repoPath);
        when(resource.getInfo()).thenReturn(repoResourceInfo);
        return request;
    }

    private static ArtifactoryResponse createArtifactoryResponse() throws IOException {
        ArtifactoryResponse response = mock(ArtifactoryResponse.class);
        Writer writer = mock(Writer.class);
        when(response.getWriter()).thenReturn(writer);
        return response;
    }

    private static LocalRepo setupLocalRepo() {
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Npm);
        LocalRepo localRepo = mock(LocalRepo.class);
        when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        return localRepo;
    }

    private UploadServiceImpl setupUploadService(AddonsManager addonsManager, RepoResource res)
            throws IOException, RepoRejectException {
        UploadServiceImpl uploadService = new UploadServiceImpl();
        uploadService.setSecurityService(securityServiceMock);
        uploadService.setRepoService(repoServiceMock);
        uploadService.setCommitContextPropertiesProvider(commitContextPropertiesProviderMock);
        uploadService.setAddonsManager(addonsManager);
        when(repoServiceMock.saveResource(any(), any())).thenReturn(res);
        return uploadService;
    }

    private static AddonsManager setupLeadArtifactDetectorAddon(boolean isLead) {
        AddonsManager addonsManager = mock(AddonsManager.class);
        LeadArtifactDetectorAddon leadArtifactDetectorAddon = mock(LeadArtifactDetectorAddon.class);
        when(leadArtifactDetectorAddon.isLeadArtifact(any(), any())).thenReturn(isLead);
        when(addonsManager.addonByType(eq(LeadArtifactDetectorAddon.class))).thenReturn(leadArtifactDetectorAddon);
        return addonsManager;
    }

    private static void setupTokenAuthenticationWithScope(String scope) {
        AccessTokenAuthentication accessTokenAuthentication = mock(AccessTokenAuthentication.class);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(jwtAccessToken.getScope()).thenReturn(List.of(scope));
        when(accessTokenAuthentication.getAccessToken()).thenReturn(jwtAccessToken);
        SecurityContextHolder.getContext().setAuthentication(accessTokenAuthentication);
    }

    private static void setupAnonymousAuthentication() {
        AnonymousAuthenticationToken accessTokenAuthentication = mock(AnonymousAuthenticationToken.class);
        SecurityContextHolder.getContext().setAuthentication(accessTokenAuthentication);
    }
}
