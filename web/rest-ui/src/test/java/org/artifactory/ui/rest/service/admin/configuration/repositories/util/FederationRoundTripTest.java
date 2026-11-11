package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.apache.commons.lang3.builder.DiffResult;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.smartrepo.EdgeSmartRepoAddon;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.metrics.cuj.RepoCreationMetricsWrapper;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.repo.ProjectRepoPermissionsHelper;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.validator.virtual.VirtualRepoPackageValidator;
import org.artifactory.repo.virtual.VirtualRepositoryConfigResolver;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.ServiceExecutor;
import org.artifactory.service.ConfigValidationService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepoAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.MirrorMemberConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GenericTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NpmTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.resource.admin.configuration.repositories.RepoConfigResource;
import org.artifactory.ui.rest.service.admin.configuration.ConfigServiceFactory;
import org.artifactory.ui.rest.service.admin.configuration.repositories.CreateRepositoryConfigService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.GetRepositoryConfigService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.UpdateRepositoryConfigService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.replication.ReplicationConfigService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.ReplicationConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigModelBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RemoteRepoPackageValidator;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.ReplicationConfigValidator;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.artifactory.api.repo.UpstreamRepoKind.SMART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Round-trip tests that exercise the real create/update/get service stack.
 * <p>
 * Only infrastructure with hard external dependencies (DB, addons, security, metrics) is mocked.
 * Storage is backed by an in-memory map so {@code upsertRepoConfig} → {@code getRepoConfigByKey}
 * works exactly as in production without a running server.
 * <p>
 * Covers: local, remote, virtual, and federated-as-local repo types; fedAttrOptIn on/off;
 * with/without federation members; create, update, and mixed-flag flows.
 */
class FederationRoundTripTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String MIRROR_URL_1 = "https://remote1.example.com/artifactory/my-repo";
    private static final String MIRROR_URL_2 = "https://remote2.example.com/artifactory/my-repo";
    private static final String MIRROR_URL_3 = "https://remote3.example.com/artifactory/my-repo";
    private static final String MIRROR_URL_4 = "https://remote4.example.com/artifactory/my-repo";

    // ── In-memory repo store — replaces all per-test getRepoConfigByKey stubs ──────────
    // upsertRepoConfig writes here; every read method answers from here.
    private Map<String, RepoConfig> repoStore;

    // ── External infrastructure — must mock ──────────────────────────────────────────
    @Mock
    private FederatedRepoService federatedRepoService;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private HttpServletRequest servletRequest;
    @Mock
    private ConfigServiceFactory configServiceFactory;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private SecurityService securityService;
    @Mock
    private ReplicationConfigDescriptorBuilder replicationBuilder;
    @Mock
    private ReplicationConfigService replicationConfigService;
    @Mock
    private ReplicationConfigValidator replicationValidator;
    @Mock
    private ConfigValidationService configValidationService;
    @Mock
    private RemoteRepoPackageValidator remoteRepoPackageValidator;
    @Mock
    private VirtualRepoPackageValidator virtualRepoPackageValidator;
    @Mock
    private VirtualRepositoryConfigResolver resolver;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private ProjectRepoPermissionsHelper projectRepoPermissionsHelper;
    @Mock
    private RepoCreationMetricsWrapper repoCreationMetricsWrapper;

    // ── Real implementations — core business logic under test ────────────────────────
    @Spy
    private ServiceExecutor serviceExecutor = new ServiceExecutor();
    @Spy
    private RepoConfigDescriptorBuilder repoBuilder = new RepoConfigDescriptorBuilder();
    @Spy
    private CreateRepoConfigHelper createHelper = new CreateRepoConfigHelper();
    @Spy
    private RepoConfigModelBuilder modelBuilder = new RepoConfigModelBuilder();

    // Validation: keep as mock — real validator calls into config service / addon checks
    @Mock
    private RepoConfigValidator repoValidator;

    @InjectMocks
    private UpdateRepoConfigHelper updateHelper;
    @InjectMocks
    private GetRepositoryConfigService getService;
    @Spy
    @InjectMocks
    private RepoConfigResource repoConfigResource;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        repoStore = new HashMap<>();
        mocks = MockitoAnnotations.openMocks(this);
        homeStub.setProperty(ConstantValues.repositoriesFederationsAttributeEnabled, "true");
        ArtifactoryContextThreadBinder.bind(artifactoryContext);

        when(servletRequest.getContextPath()).thenReturn("/artifactory");
        when(servletRequest.getRequestURI()).thenReturn("/artifactory/api/repositories/test");

        MutableCentralConfigDescriptor configDescriptor = new CentralConfigDescriptorImpl();
        when(centralConfigService.getMutableDescriptor()).thenReturn(configDescriptor);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(artifactoryContext.beanForType(FederatedRepoService.class)).thenReturn(federatedRepoService);

        DiffResult<?> diffResult = mock(DiffResult.class);
        when(diffResult.getNumberOfDiffs()).thenReturn(1);
        when(centralConfigService.findDiff(any(), any())).thenReturn(diffResult);

        // ── In-memory store wiring ────────────────────────────────────────────────────
        // existsByRepoKey: false on first create, true after upsert
        when(repositoryConfigService.existsByRepoKey(anyString()))
                .thenAnswer(inv -> repoStore.containsKey(inv.getArgument(0)));

        // upsertRepoConfig is the single write point — all reads answer from the same map
        doAnswer(inv -> {
            RepoConfig stored = inv.getArgument(0);
            repoStore.put(stored.getKey(), stored);
            return null;
        }).when(repositoryConfigService).upsertRepoConfig(any());

        // All read methods delegate to the map
        when(repositoryConfigService.getRepoConfigByKey(anyString()))
                .thenAnswer(inv -> repoStore.get(inv.getArgument(0)));
        when(repositoryConfigService.getFederatedRepoConfigByKey(anyString()))
                .thenAnswer(inv -> repoStore.get(inv.getArgument(0)));
        when(repositoryService.getRepoConfigByKey(anyString()))
                .thenAnswer(inv -> repoStore.get(inv.getArgument(0)));

        // List queries (used only by the list path; single-repo tests don't hit these)
        when(repositoryService.getLocalRepoConfigsExcludingInfoRepos())
                .thenAnswer(inv -> repoStore.values().stream()
                        .filter(c -> c.getRepoType() == RepoDetailsType.LOCAL).toList());
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos())
                .thenAnswer(inv -> repoStore.values().stream()
                        .filter(c -> c.getRepoType() == RepoDetailsType.LOCAL
                                || c.getRepoType() == RepoDetailsType.FEDERATED).toList());
        when(repositoryService.getLocalAndFederatedRepoConfigsIncludingInfoRepos())
                .thenAnswer(inv -> repoStore.values().stream()
                        .filter(c -> c.getRepoType() == RepoDetailsType.LOCAL
                                || c.getRepoType() == RepoDetailsType.FEDERATED).toList());
        when(repositoryService.getRemoteRepoConfigs())
                .thenAnswer(inv -> repoStore.values().stream()
                        .filter(c -> c.getRepoType() == RepoDetailsType.REMOTE).toList());
        when(repositoryService.getVirtualRepoConfigs())
                .thenAnswer(inv -> repoStore.values().stream()
                        .filter(c -> c.getRepoType() == RepoDetailsType.VIRTUAL).toList());

        // ── Update path ───────────────────────────────────────────────────────────────
        when(projectRepoPermissionsHelper.projAdminNotAuthorizedToUpdateRepo(any(), any())).thenReturn(false);
        when(remoteRepoPackageValidator.getRemoteRepoUpdateValidationEnabledPackages()).thenReturn(List.of());

        // ── Project / security stubs ──────────────────────────────────────────────────
        when(projectsService.canDoToProjectRepoResource(any(), anyString(), any(), any())).thenReturn(true);
        when(projectsService.canReadFullRepoConfig(anyString(), any())).thenReturn(true);
        when(projectsService.getProjectKeyOfRepository(anyString())).thenReturn("default");
        ProjectResourceEntity projectEntity = mock(ProjectResourceEntity.class);
        when(projectEntity.getEnvironments()).thenReturn(Set.of());
        when(projectsService.getProjectRepositoryEntity(anyString(), anyString())).thenReturn(projectEntity);
        when(securityService.isProjectAdmin(any(org.artifactory.repo.RepoPath.class))).thenReturn(true);
        doNothing().when(projectRepoPermissionsHelper).validateAnyProjectAdmin();

        // ── Remote-specific stubs ─────────────────────────────────────────────────────
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        EdgeSmartRepoAddon edgeSmartRepoAddon = mock(EdgeSmartRepoAddon.class);
        when(edgeSmartRepoAddon.getUpstreamRepoKind(any())).thenReturn(SMART);
        when(addonsManager.addonByType(EdgeSmartRepoAddon.class)).thenReturn(edgeSmartRepoAddon);
        doNothing().when(remoteRepoPackageValidator).validate(
                any(RemoteRepoConfig.class), any(MutableCentralConfigDescriptor.class),
                any(RemoteRepositoryConfigModel.class));

        // ── Virtual / get-side stubs ──────────────────────────────────────────────────
        ArtifactWebstartAddon webstartAddon = mock(ArtifactWebstartAddon.class);
        when(webstartAddon.getSslCertNames()).thenReturn(List.of());
        when(addonsManager.addonByType(ArtifactWebstartAddon.class)).thenReturn(webstartAddon);
        when(resolver.getOrderedRepos(anyString())).thenReturn(List.of());
        when(repositoryConfigService.getRepoConfigsByKeysInSpecificOrder(any())).thenReturn(new LinkedHashSet<>());
        when(projectsService.getIneligibleRepos(anyString(), anyString())).thenReturn(Set.of());

        // ── Wire real collaborators ───────────────────────────────────────────────────
        repoBuilder.setCentralConfig(centralConfigService);
        repoBuilder.setRepositoryConfigService(repositoryConfigService);

        createHelper.setAddonsManager(addonsManager);
        createHelper.setReplicationBuilder(replicationBuilder);
        createHelper.setReplicationConfigService(replicationConfigService);
        createHelper.setReplicationValidator(replicationValidator);
        createHelper.setRepoValidator(repoValidator);
        createHelper.setConfigValidationService(configValidationService);
        createHelper.setProjectsService(projectsService);
        createHelper.setRepositoryConfigService(repositoryConfigService);
        createHelper.setRemoteRepoPackageValidator(remoteRepoPackageValidator);
        createHelper.setVirtualRepoPackageValidator(virtualRepoPackageValidator);
        createHelper.setRepoBuilder(repoBuilder);

        modelBuilder.setCentralConfig(centralConfigService);
        modelBuilder.setAddonsManager(addonsManager);
        modelBuilder.setProjectsService(projectsService);
        modelBuilder.setRepositoryConfigService(repositoryConfigService);
        modelBuilder.setResolver(resolver);

        CreateRepositoryConfigService createService = new CreateRepositoryConfigService(
                centralConfigService, repoValidator, createHelper,
                projectsService, repositoryService, repositoryConfigService);
        UpdateRepositoryConfigService updateService = new UpdateRepositoryConfigService(
                updateHelper, projectsService, repoValidator);

        when(configServiceFactory.createRepositoryConfig()).thenReturn(createService);
        when(configServiceFactory.getRepositoryConfig()).thenReturn(getService);
        when(configServiceFactory.updateRepositoryConfig()).thenReturn(updateService);

        doAnswer(inv -> ((Callable<?>) inv.getArgument(2)).call())
                .when(repoCreationMetricsWrapper).createRepoAndCollectMetrics(any(), any(), any(), any());
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    // ── Create round-trip (POST → GET) ────────────────────────────────────────────────
    // Expected: federation in the GET response iff opt-in=true AND members were submitted.

    @ParameterizedTest(name = "{0} repo, fedAttrOptIn={1}, withMembers={2}")
    @CsvSource({
            "local,   true,  true",
            "local,   true,  false",
            "local,   false, true",
            "local,   false, false",
            "remote,  true,  true",
            "remote,  true,  false",
            "remote,  false, true",
            "remote,  false, false",
            "virtual, true,  true",
            "virtual, true,  false",
            "virtual, false, true",
            "virtual, false, false",
    })
    void testCreateRoundTrip(String repoType, boolean fedAttrOptIn, boolean withMembers) {
        String repoKey = "fed-" + repoType;
        stubUriInfo(Boolean.toString(fedAttrOptIn), repoType, repoKey);
        callCreate(buildModel(repoType, repoKey, withMembers));

        RepositoryConfigModel<?, ?, ?> result = callGet(repoKey);
        assertNotNull(result, "GET returned null for " + repoType);
        if (fedAttrOptIn && withMembers) {
            assertFederationPresence(result.getFederation(), true);
        } else {
            assertNull(result.getFederation());
        }
    }

    // ── Update round-trip (POST → PUT → GET) ──────────────────────────────────────────
    // Create always with members; update with or without; verify GET reflects the update.

    @ParameterizedTest(name = "{0} repo, fedAttrOptIn={1}, withMembers={2}")
    @CsvSource({
            "local,   true,  true",
            "local,   true,  false",
            "local,   false, true",
            "local,   false, false",
            "remote,  true,  true",
            "remote,  true,  false",
            "remote,  false, true",
            "remote,  false, false",
            "virtual, true,  true",
            "virtual, true,  false",
            "virtual, false, true",
            "virtual, false, false",
    })
    void testUpdateRoundTrip(String repoType, boolean fedAttrOptIn, boolean withMembers) {
        String repoKey = "fed-" + repoType;
        stubUriInfo(Boolean.toString(fedAttrOptIn), repoType, repoKey);
        callCreate(buildModel(repoType, repoKey, true));
        callUpdate(buildUpdatedModel(repoType, repoKey, withMembers));

        RepositoryConfigModel<?, ?, ?> result = callGet(repoKey);
        assertNotNull(result, "GET returned null for " + repoType);
        if (fedAttrOptIn && withMembers) {
            assertUpdatedFederationPresence(result.getFederation(), true);
        } else {
            assertNull(result.getFederation());
        }
    }

    // ── Create with opt-in, GET without opt-in hides federation ───────────────────────

    @ParameterizedTest(name = "{0} repo: create opt-in, get no opt-in")
    @CsvSource({"local", "remote", "virtual"})
    void testCreateWithOptIn_getWithoutOptIn(String repoType) {
        String repoKey = "fed-" + repoType;

        stubUriInfo("true", repoType, repoKey);
        callCreate(buildModel(repoType, repoKey, true));
        assertFederationPresence(callGet(repoKey).getFederation(), true);

        stubUriInfo("false", repoType, repoKey);
        assertNull(callGet(repoKey).getFederation());
    }

    // ── Update without opt-in drops previously-stored federation ──────────────────────

    /**
     * Creates a repo WITH opt-in (so members are persisted), then updates WITHOUT opt-in.
     * Verifies that the update wipes the stored members rather than silently preserving them.
     */
    @Test
    void testUpdateWithoutOptIn_dropsExistingFederationMembers() {
        stubUriInfo("true", "local", "fed-local");
        callCreate(buildLocalModel("fed-local", true));
        assertFederationPresence(callGet("fed-local").getFederation(), true);

        stubUriInfo("false", "local", "fed-local");
        callUpdate(buildUpdatedLocalModel("fed-local", false));

        stubUriInfo("true", "local", "fed-local");
        assertNull(callGet("fed-local").getFederation());
    }

    // ── Federated repo served as local via fedAttrOptIn ───────────────────────────────
    // Uses real create (POST repoType=federated) so members are stored in baseConfig via
    // RepoConfigDescriptorBuilder.populateFederationValues. GET with repoType=local +
    // fedAttrOptIn=true reads them back via createFederationConfigFromBaseConfig.

    @Test
    void testFederatedAsLocal_withOptIn_showsFederation() {
        stubUriInfo("false", "federated", "fed-as-local");
        callCreate(buildFederatedModel("fed-as-local", true));

        stubUriInfo("true", "local", "fed-as-local");
        RepositoryConfigModel<?, ?, ?> result = callGet("fed-as-local");
        assertNotNull(result, "GET returned null for federated-as-local");
        assertFederationPresence(result.getFederation(), true);
    }

    @Test
    void testFederatedAsLocal_noMembers_federationNull() {
        stubUriInfo("false", "federated", "fed-as-local-empty");
        callCreate(buildFederatedModel("fed-as-local-empty", false));

        stubUriInfo("true", "local", "fed-as-local-empty");
        RepositoryConfigModel<?, ?, ?> result = callGet("fed-as-local-empty");
        assertNotNull(result, "GET returned null for federated-as-local-empty");
        assertNull(result.getFederation());
    }

    /**
     * Fetching a federated repo as local WITHOUT opt-in returns 404 — the type mismatch guard
     * only makes an exception when fedAttrOptIn=true.
     */
    @Test
    void testFederatedAsLocal_withoutOptIn_returns404() {
        stubUriInfo("false", "federated", "fed-as-local-noopt");
        callCreate(buildFederatedModel("fed-as-local-noopt", true));

        stubUriInfo("false", "local", "fed-as-local-noopt");
        assertNull(callGet("fed-as-local-noopt"),
                "Expected 404 (null model) when fetching federated repo as local without opt-in");
    }

    @Test
    void testFederatedAsRemote_withOptIn_returns404() {
        stubUriInfo("false", "federated", "fed-as-remote");
        callCreate(buildFederatedModel("fed-as-remote", true));

        stubUriInfo("true", "remote", "fed-as-remote");
        assertNull(callGet("fed-as-remote"));
    }

    /**
     * Fetching a federated repo via repoType=federated (normal path) always exposes federation
     * because populateFederatedDescriptorValuesToModel unconditionally calls setFederation.
     * Also verifies the license gate fires on create (fedAttrOptIn=false → else-if branch).
     */
    @Test
    void testFederatedRepo_normalGet_alwaysShowsFederation() {
        stubUriInfo("false", "federated", "fed-normal");
        callCreate(buildFederatedModel("fed-normal", true));
        verify(federatedRepoService).validateFederationLicenseInstalled();

        stubUriInfo("false", "federated", "fed-normal");
        RepositoryConfigModel<?, ?, ?> result = callGet("fed-normal");
        assertNotNull(result, "GET returned null for federated repo");
        assertFederationPresence(result.getFederation(), true);
    }

    /**
     * Regression test for license-gate bypass: creating a FederatedRepositoryConfigModel with
     * fedAttrOptIn=true and no mirrors should still call validateFederationLicenseInstalled().
     * Currently FAILS — the fedAttrOptIn branch skips the license check when mirrors are empty,
     * while the non-opt-in path (else-if) would enforce it unconditionally.
     */
    @Test
    void testFederatedRepo_withFedAttrOptIn_noMirrors_licenseStillRequired() {
        stubUriInfo("true", "federated", "fed-no-mirrors");
        callCreate(buildFederatedModel("fed-no-mirrors", false));
        verify(federatedRepoService).validateFederationLicenseInstalled();
    }

    /**
     * Verifies the license gate fires when creating a non-federated repo with fedAttrOptIn=true
     * and actual federation mirrors — the opt-in path should require the license when mirrors
     * are present.
     */
    @Test
    void testLocalRepo_withFedAttrOptIn_andMirrors_requiresLicense() {
        stubUriInfo("true", "local", "fed-local-with-mirrors");
        callCreate(buildLocalModel("fed-local-with-mirrors", true));
        verify(federatedRepoService).validateFederationLicenseInstalled();
    }

    /**
     * Verifies the license gate does NOT fire when creating a non-federated repo with
     * fedAttrOptIn=true but no federation mirrors.
     */
    @Test
    void testLocalRepo_withFedAttrOptIn_noMirrors_noLicenseRequired() {
        stubUriInfo("true", "local", "fed-local-no-mirrors");
        callCreate(buildLocalModel("fed-local-no-mirrors", false));
        verify(federatedRepoService, never()).validateFederationLicenseInstalled();
    }

    // ── List path ─────────────────────────────────────────────────────────────────────

    @Test
    void testListLocal_withFedAttrOptIn_includesFederatedRepos() {
        stubUriInfo("true", "local", "my-local");
        callCreate(buildLocalModel("my-local", false));
        stubUriInfo("false", "federated", "my-fed");
        callCreate(buildFederatedModel("my-fed", true));

        assertEquals(2, callGetList("true", "local").size(),
                "fedAttrOptIn=true should include both local and federated repos");
    }

    @Test
    void testListLocal_withoutFedAttrOptIn_excludesFederatedRepos() {
        stubUriInfo("true", "local", "my-local");
        callCreate(buildLocalModel("my-local", false));
        stubUriInfo("false", "federated", "my-fed");
        callCreate(buildFederatedModel("my-fed", true));

        assertEquals(1, callGetList("false", "local").size(),
                "fedAttrOptIn=false should only return local repos");
    }

    @Test
    void testFedAttrOptIn_withFederatedRepoType_returns400() {
        MultivaluedHashMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.add("isFedAttrSupported", "true");
        MultivaluedHashMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.add("repoType", "federated");
        pathParams.add("repoKey", "some-repo");
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(uriInfo.getPathParameters()).thenReturn(pathParams);

        ArtifactoryRestResponse<RepositoryConfigModel<?, ?, ?>> response = new ArtifactoryRestResponse<>();
        repoConfigResource.setArtifactoryResponse(response);
        repoConfigResource.getRepositoryConfigByType("federated", "some-repo");

        assertEquals(400, response.getResponseCode());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────

    private java.util.Collection<RepositoryConfigModel<?, ?, ?>> callGetList(String fedAttrValue, String repoType) {
        MultivaluedHashMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.add("isFedAttrSupported", fedAttrValue);
        MultivaluedHashMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.add("repoType", repoType);
        // no repoKey → execute() routes to respondWithRepoList
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        ArtifactoryRestResponse<RepositoryConfigModel<?, ?, ?>> response = new ArtifactoryRestResponse<>();
        repoConfigResource.setArtifactoryResponse(response);
        repoConfigResource.getRepositoryConfigByType(repoType, "list-dummy");
        return response.getiModelList();
    }

    private void stubUriInfo(String fedAttrValue, String repoType, String repoKey) {
        MultivaluedHashMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.add("isFedAttrSupported", fedAttrValue);
        MultivaluedHashMap<String, String> pathParams = new MultivaluedHashMap<>();
        pathParams.add("repoType", repoType);
        pathParams.add("repoKey", repoKey);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
    }

    private void callCreate(RepositoryConfigModel<?, ?, ?> model) {
        repoConfigResource.setArtifactoryResponse(new ArtifactoryRestResponse<>());
        repoConfigResource.createRepository(model);
    }

    private void callUpdate(RepositoryConfigModel<?, ?, ?> model) {
        repoConfigResource.setArtifactoryResponse(new ArtifactoryRestResponse<>());
        repoConfigResource.updateRepositoryConfig(model);
    }

    private RepositoryConfigModel<?, ?, ?> callGet(String repoKey) {
        ArtifactoryRestResponse<RepositoryConfigModel<?, ?, ?>> response = new ArtifactoryRestResponse<>();
        repoConfigResource.setArtifactoryResponse(response);
        String repoType = uriInfo.getPathParameters().getFirst("repoType");
        repoConfigResource.getRepositoryConfigByType(repoType, repoKey);
        return response.getIModel();
    }

    private RepositoryConfigModel<?, ?, ?> buildModel(String repoType, String repoKey, boolean withMembers) {
        return switch (repoType) {
            case "local" -> buildLocalModel(repoKey, withMembers);
            case "remote" -> buildRemoteModel(repoKey, withMembers);
            case "virtual" -> buildVirtualModel(repoKey, withMembers);
            case "federated" -> buildFederatedModel(repoKey, withMembers);
            default -> throw new IllegalArgumentException("Unsupported repo type: " + repoType);
        };
    }

    private RepositoryConfigModel<?, ?, ?> buildUpdatedModel(String repoType, String repoKey, boolean withMembers) {
        return switch (repoType) {
            case "local" -> buildUpdatedLocalModel(repoKey, withMembers);
            case "remote" -> buildUpdatedRemoteModel(repoKey, withMembers);
            case "virtual" -> buildUpdatedVirtualModel(repoKey, withMembers);
            default -> throw new IllegalArgumentException("Unsupported repo type: " + repoType);
        };
    }

    private void assertFederationPresence(FederationConfigModel federation, boolean present) {
        if (present) {
            assertNotNull(federation);
            assertEquals(2, federation.getMirrors().size());
            assertTrue(federation.getMirrors().stream().anyMatch(m -> MIRROR_URL_1.equals(m.getUrl())));
            assertTrue(federation.getMirrors().stream().anyMatch(m -> MIRROR_URL_2.equals(m.getUrl())));
        } else {
            assertNull(federation);
        }
    }

    private void assertUpdatedFederationPresence(FederationConfigModel federation, boolean present) {
        if (present) {
            assertNotNull(federation);
            assertEquals(2, federation.getMirrors().size());
            assertTrue(federation.getMirrors().stream().anyMatch(m -> MIRROR_URL_3.equals(m.getUrl())));
            assertTrue(federation.getMirrors().stream().anyMatch(m -> MIRROR_URL_4.equals(m.getUrl())));
        } else {
            assertNull(federation);
        }
    }

    private FederationConfigModel buildFederation() {
        MirrorMemberConfigModel m1 = new MirrorMemberConfigModel();
        m1.setUrl(MIRROR_URL_1);
        m1.setEnabled(true);
        MirrorMemberConfigModel m2 = new MirrorMemberConfigModel();
        m2.setUrl(MIRROR_URL_2);
        m2.setEnabled(true);
        FederationConfigModel fed = new FederationConfigModel();
        fed.setMirrors(new ArrayList<>(List.of(m1, m2)));
        return fed;
    }

    private FederationConfigModel buildUpdatedFederation() {
        MirrorMemberConfigModel m3 = new MirrorMemberConfigModel();
        m3.setUrl(MIRROR_URL_3);
        m3.setEnabled(true);
        MirrorMemberConfigModel m4 = new MirrorMemberConfigModel();
        m4.setUrl(MIRROR_URL_4);
        m4.setEnabled(true);
        FederationConfigModel fed = new FederationConfigModel();
        fed.setMirrors(new ArrayList<>(List.of(m3, m4)));
        return fed;
    }

    private LocalRepositoryConfigModel buildLocalModel(String repoKey, boolean withMembers) {
        LocalRepositoryConfigModel model = new LocalRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey(repoKey);
        model.setGeneral(general);
        model.setBasic(new LocalBasicRepositoryConfigModel());
        model.setAdvanced(new LocalAdvancedRepositoryConfigModel());
        model.setTypeSpecific(new GenericTypeSpecificConfigModel());
        if (withMembers) {
            model.setFederation(buildFederation());
        }
        return model;
    }

    private RemoteRepositoryConfigModel buildRemoteModel(String repoKey, boolean withMembers) {
        RemoteRepositoryConfigModel model = new RemoteRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey(repoKey);
        model.setGeneral(general);
        model.setBasic(new RemoteBasicRepositoryConfigModel());
        model.setAdvanced(new RemoteAdvancedRepositoryConfigModel());
        model.setTypeSpecific(new NpmTypeSpecificConfigModel());
        if (withMembers) {
            model.setFederation(buildFederation());
        }
        return model;
    }

    private VirtualRepositoryConfigModel buildVirtualModel(String repoKey, boolean withMembers) {
        VirtualRepositoryConfigModel model = new VirtualRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey(repoKey);
        model.setGeneral(general);
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        basic.setSelectedRepositories(List.of());
        model.setBasic(basic);
        model.setAdvanced(new VirtualAdvancedRepositoryConfigModel());
        model.setTypeSpecific(new NpmTypeSpecificConfigModel());
        if (withMembers) {
            model.setFederation(buildFederation());
        }
        return model;
    }

    private FederatedRepositoryConfigModel buildFederatedModel(String repoKey, boolean withMembers) {
        FederatedRepositoryConfigModel model = new FederatedRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey(repoKey);
        model.setGeneral(general);
        model.setBasic(new LocalBasicRepositoryConfigModel());
        model.setAdvanced(new FederatedRepoAdvancedRepositoryConfigModel());
        model.setTypeSpecific(new GenericTypeSpecificConfigModel());
        if (withMembers) {
            model.setFederation(buildFederation());
        }
        return model;
    }

    private LocalRepositoryConfigModel buildUpdatedLocalModel(String repoKey, boolean withMembers) {
        LocalRepositoryConfigModel model = buildLocalModel(repoKey, false);
        if (withMembers) {
            model.setFederation(buildUpdatedFederation());
        }
        return model;
    }

    private RemoteRepositoryConfigModel buildUpdatedRemoteModel(String repoKey, boolean withMembers) {
        RemoteRepositoryConfigModel model = buildRemoteModel(repoKey, false);
        if (withMembers) {
            model.setFederation(buildUpdatedFederation());
        }
        return model;
    }

    private VirtualRepositoryConfigModel buildUpdatedVirtualModel(String repoKey, boolean withMembers) {
        VirtualRepositoryConfigModel model = buildVirtualModel(repoKey, false);
        if (withMembers) {
            model.setFederation(buildUpdatedFederation());
        }
        return model;
    }
}
