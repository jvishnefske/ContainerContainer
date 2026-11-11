package org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoLayoutBuilder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.ph.PackageSpecificationImpl;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.remote.ContentSynchronisation;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigRequest;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteCacheRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.BowerTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.DockerTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GenericTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GoTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HelmOciTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.OciTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualSelectedRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_DOCKER_REMOTE_RETRIEVAL_CACHE_PERIOD;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_GO_REMOTE_METADATA_MISSED_RETRIEVAL_CACHE_PERIOD;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_MISSED_RETRIEVAL_PERIOD;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_RETRIEVAL_CACHE_PERIOD;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author AndreiK.
 */
class RepoConfigValidatorTest extends ArtifactoryHomeBoundJUnitTest {

    private RepoConfigValidator validator;
    private AutoCloseable autoCloseable;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RepositoryConfigService repositoryConfigService;

    @BeforeEach
    void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        InternalArtifactoryContext context = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        validator = new RepoConfigValidator();
        validator.setRepositoryService(repositoryService);
        validator.setRepositoryConfigService(repositoryConfigService);
        PackageSpecificationImpl packageSpecification = new PackageSpecificationImpl(null);
        validator.setPackageSpecification(packageSpecification);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @MethodSource("provideDockerCachePeriodAndRepoType")
    void testRemoteRepoValidationOfRetrievalCachePeriodSecsOfCacheModel(Long cachePeriod, boolean isDockerModel,
            Long expected) {
        TypeSpecificConfigModel typeSpecificConfigModel;
        if (isDockerModel) {
            typeSpecificConfigModel = new DockerTypeSpecificConfigModel();
            ((DockerTypeSpecificConfigModel) typeSpecificConfigModel).setRetrievalCachePeriodSecs(cachePeriod);
        } else {
            typeSpecificConfigModel = new GenericTypeSpecificConfigModel();
        }
        RemoteAdvancedRepositoryConfigModel remoteAdvancedRepositoryConfigModel =
                getAdvancedRepoConfigModelAndSetRetrievalCachePeriod(cachePeriod);
        RemoteRepositoryConfigModel model = getRemoteRepositoryConfigModel(remoteAdvancedRepositoryConfigModel,
                typeSpecificConfigModel);
        RemoteCacheRepositoryConfigModel cache = model.getAdvanced().getCache();

        validator.setRetrievalCachePeriodSecsOfCacheModel(model, cache);

        assertEquals((long) cache.getRetrievalCachePeriodSecs(), (long) expected);
    }

    @ParameterizedTest
    @MethodSource("provideGoCachePeriodAndRepoType")
    void testRemoteRepoValidationOfGoRetrievalCachePeriodSecsOfCacheModel(Long cachePeriod, boolean isGoModel,
            Long expected) {
        TypeSpecificConfigModel typeSpecificConfigModel;
        if (isGoModel) {
            typeSpecificConfigModel = new GoTypeSpecificConfigModel();
            ((GoTypeSpecificConfigModel) typeSpecificConfigModel)
                    .setMissedRetrievalCachePeriodSecs(cachePeriod);
        } else {
            typeSpecificConfigModel = new GenericTypeSpecificConfigModel();
        }
        RemoteAdvancedRepositoryConfigModel remoteAdvancedRepositoryConfigModel =
                getAdvancedRepoConfigModelAndSetMissedRetrievalCachePeriod(cachePeriod);
        RemoteRepositoryConfigModel model = getRemoteRepositoryConfigModel(remoteAdvancedRepositoryConfigModel,
                typeSpecificConfigModel);
        RemoteCacheRepositoryConfigModel cache = model.getAdvanced().getCache();

        validator.setRetrievalCachePeriodSecsOfCacheModel(model, cache);

        assertEquals((long) cache.getMissedRetrievalCachePeriodSecs(), (long) expected);
    }

    @Test
    void testValidateSelectedReposInVirtualExist() throws RepoConfigException {
        String repoKey = "repoKey";
        List<VirtualSelectedRepository> virtualSelectedRepositories = new ArrayList<>();
        VirtualSelectedRepository repository = new VirtualSelectedRepository();
        repository.setRepoName(repoKey);
        virtualSelectedRepositories.add(repository);
        when(repositoryService.repoExists(repoKey)).thenReturn(true);
        validator.validateSelectedReposInVirtualExist(virtualSelectedRepositories);
        verify(repositoryService, times(1)).repoExists(repoKey);
    }

    @Test
    void testValidateMetadataTimeIsValidDefaultValues() throws RepoConfigException {
        RepoConfigValidator configValidator = new RepoConfigValidator();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        configValidator.validateMetadataTimeIsValid(cache);
    }

    @Test
    void testValidateMetadataTimeIsValidWithZeroCachePeriod() throws RepoConfigException {
        RepoConfigValidator configValidator = new RepoConfigValidator();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        cache.setRetrievalCachePeriodSecs(0L);
        configValidator.validateMetadataTimeIsValid(cache);
    }

    @Test
    void testValidateMetadataTimeIsValidTimeoutBiggerThenCache() {
        RepoConfigValidator configValidator = new RepoConfigValidator();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        cache.setRetrievalCachePeriodSecs(1L);
        RepoConfigException repoConfigException =
                assertThrows(RepoConfigException.class, () -> configValidator.validateMetadataTimeIsValid(cache));
        assertTrue(repoConfigException.getMessage()
                .contains("The Metadata retrieval timeout not allowed to be bigger then Metadata Retrieval"));
    }

    @Test
    void testValidateMetadataTimeIsValidWhenTimeoutIllegal() {
        RepoConfigValidator configValidator = new RepoConfigValidator();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        cache.setRetrievalCachePeriodSecs(1L);
        cache.setMetadataRetrievalTimeoutSecs(0L);
        RepoConfigException repoConfigException =
                assertThrows(RepoConfigException.class, () -> configValidator.validateMetadataTimeIsValid(cache));
        assertTrue(repoConfigException.getMessage()
                .contains("The Metadata retrieval timeout can't be zero or negative"));
    }

    @Test
    void testValidateRemoteValidFields() throws RepoConfigException {
        RemoteRepositoryConfigModel model = new RemoteRepositoryConfigModel();
        setGeneralRemoteConfig(model);
        RemoteAdvancedRepositoryConfigModel advanced = new RemoteAdvancedRepositoryConfigModel();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        cache.setMetadataRetrievalTimeoutSecs(10L);
        advanced.setCache(cache);
        model.setAdvanced(advanced);
        setBasicRemoteConfig(model);
        BowerTypeSpecificConfigModel mock = mock(BowerTypeSpecificConfigModel.class);
        when(mock.getRepoType()).thenReturn(RepoType.Bower);
        model.setTypeSpecific(mock);
        RepoConfigValidator configValidator = new RepoConfigValidator();
        prepareValidation(configValidator);
        configValidator.validateRemote(model);
    }

    @Test
    void testValidateRemoteNotValidMetadataTimeout() {
        RemoteRepositoryConfigModel model = new RemoteRepositoryConfigModel();
        setGeneralRemoteConfig(model);
        RemoteAdvancedRepositoryConfigModel advanced = new RemoteAdvancedRepositoryConfigModel();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        cache.setMetadataRetrievalTimeoutSecs(0L);
        advanced.setCache(cache);
        model.setAdvanced(advanced);
        setBasicRemoteConfig(model);
        BowerTypeSpecificConfigModel mock = mock(BowerTypeSpecificConfigModel.class);
        when(mock.getRepoType()).thenReturn(RepoType.Bower);
        model.setTypeSpecific(mock);
        RepoConfigValidator configValidator = new RepoConfigValidator();
        prepareValidation(configValidator);
        RepoConfigException repoConfigException =
                assertThrows(RepoConfigException.class, () -> configValidator.validateRemote(model));
        assertTrue(repoConfigException.getMessage()
                .contains("The Metadata retrieval timeout can't be zero or negative"));
    }

    @Test
    void testValidateVirtualOciSupportHelmOci() throws RepoConfigException {
        String repoKey = "repoKey";
        VirtualRepositoryConfigModel virtualRepositoryConfigModel = new VirtualRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey(repoKey);
        general.setProjectKey("default");
        VirtualSelectedRepository repository = new VirtualSelectedRepository();
        repository.setRepoName("helmoci");
        repository.setType(RepoType.HelmOCI.getDisplayName());
        List<VirtualSelectedRepository> virtualSelectedRepositories = List.of(repository);
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        basic.setSelectedRepositories(virtualSelectedRepositories);
        virtualRepositoryConfigModel.setGeneral(general);
        virtualRepositoryConfigModel.setAdvanced(new VirtualAdvancedRepositoryConfigModel());
        virtualRepositoryConfigModel.setBasic(basic);
        virtualRepositoryConfigModel.setTypeSpecific(new OciTypeSpecificConfigModel());
        when(repositoryService.repoExists("helmoci")).thenReturn(true);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.HelmOCI);
        when(repositoryService.repoConfigByKey("helmoci")).thenReturn(repoConfig);
        validator.validateVirtual(virtualRepositoryConfigModel);
    }

    @Test
    void testValidateVirtualHelmOciNotSupportHelmOci() {
        String repoKey = "repoKey";
        VirtualRepositoryConfigModel virtualRepositoryConfigModel = new VirtualRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey(repoKey);
        general.setProjectKey("default");
        VirtualSelectedRepository repository = new VirtualSelectedRepository();
        repository.setRepoName("oci");
        repository.setType(RepoType.OCI.getDisplayName());
        List<VirtualSelectedRepository> virtualSelectedRepositories = List.of(repository);
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        basic.setSelectedRepositories(virtualSelectedRepositories);
        virtualRepositoryConfigModel.setGeneral(general);
        virtualRepositoryConfigModel.setAdvanced(new VirtualAdvancedRepositoryConfigModel());
        virtualRepositoryConfigModel.setBasic(basic);
        virtualRepositoryConfigModel.setTypeSpecific(new HelmOciTypeSpecificConfigModel());
        when(repositoryService.repoExists("oci")).thenReturn(true);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.OCI);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repositoryService.repoConfigByKey("oci")).thenReturn(repoConfig);
        assertThrows(RepoConfigException.class, () -> validator.validateVirtual(virtualRepositoryConfigModel));
    }

    @ParameterizedTest
    @MethodSource("environmentAgnosticRepoTypes")
    void validateEnvironmentsForRepoType_shouldFail_whenAgnosticTypeWithEnvironments(RepoType repoType) {
        RepositoryConfigModel<?, ?, ?> model = buildModelWithRepoType(repoType, Set.of("PROD"));
        RepoConfigException exception = assertThrows(RepoConfigException.class,
                () -> validator.validateEnvironmentsForRepoType(model));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("environment-agnostic"));
    }

    @ParameterizedTest
    @MethodSource("environmentAgnosticRepoTypes")
    void validateEnvironmentsForRepoType_shouldPass_whenAgnosticTypeWithoutEnvironments(RepoType repoType)
            throws RepoConfigException {
        RepositoryConfigModel<?, ?, ?> model = buildModelWithRepoType(repoType, Set.of());
        validator.validateEnvironmentsForRepoType(model);
    }

    @ParameterizedTest
    @MethodSource("environmentAgnosticRepoTypes")
    void validateEnvironmentsForRepoType_shouldPass_whenEnvironmentsFieldIsNull(RepoType repoType) {
        // An absent "environments" JSON field leaves GeneralRepositoryConfigModel#environments null.
        // The validator feeds model.getGeneral().getEnvironments() straight into new HashSet<>(...), which NPEs if the
        // getter ever stops being null-safe. Building the model with a null environments field pins that contract -
        // this test fails the moment getEnvironments() can return null (directly and via the validator's HashSet call).
        RepositoryConfigModel<?, ?, ?> model = buildModelWithRepoType(repoType, null);
        assertNotNull(model.getGeneral().getEnvironments(), "getEnvironments() must never return null");
        assertDoesNotThrow(() -> validator.validateEnvironmentsForRepoType(model));
    }

    @Test
    void validateEnvironmentsForRepoType_shouldPass_whenNonAgnosticTypeWithEnvironments() throws RepoConfigException {
        RepositoryConfigModel<?, ?, ?> model = buildModelWithRepoType(RepoType.Maven, Set.of("PROD"));
        validator.validateEnvironmentsForRepoType(model);
    }

    @ParameterizedTest
    @MethodSource("environmentAgnosticRepoTypes")
    void validateEnvironmentsForRepoType_shouldPass_whenResendingExistingEnvironments(RepoType repoType)
            throws RepoConfigException {
        RepositoryConfigModel<?, ?, ?> model = buildModelWithRepoType(repoType, Set.of("DEV"));
        // A read -> modify (non-env field) -> update round trip re-sends the currently-assigned env, which is allowed.
        validator.validateEnvironmentsForRepoType(model, Set.of("DEV"));
    }

    @ParameterizedTest
    @MethodSource("environmentAgnosticRepoTypes")
    void validateEnvironmentsForRepoType_shouldPass_whenNoEnvironmentsSentForRepoWithExisting(RepoType repoType)
            throws RepoConfigException {
        RepositoryConfigModel<?, ?, ?> model = buildModelWithRepoType(repoType, Set.of());
        // Omitting environments on update is not an env change; the existing env is kept/defaulted downstream.
        validator.validateEnvironmentsForRepoType(model, Set.of("DEV"));
    }

    @ParameterizedTest
    @MethodSource("environmentAgnosticRepoTypes")
    void validateEnvironmentsForRepoType_shouldFail_whenChangingExistingEnvironments(RepoType repoType) {
        RepositoryConfigModel<?, ?, ?> model = buildModelWithRepoType(repoType, Set.of("PROD"));
        RepoConfigException exception = assertThrows(RepoConfigException.class,
                () -> validator.validateEnvironmentsForRepoType(model, Set.of("DEV")));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("environment-agnostic"));
    }

    @ParameterizedTest
    @MethodSource("environmentAgnosticRepoTypes")
    void validateEnvironmentsForRepoType_shouldFail_whenAssigningEnvironmentToRepoWithoutAny(RepoType repoType) {
        RepositoryConfigModel<?, ?, ?> model = buildModelWithRepoType(repoType, Set.of("DEV"));
        assertThrows(RepoConfigException.class,
                () -> validator.validateEnvironmentsForRepoType(model, Set.of()));
    }

    @Test
    void validateEnvironmentsForRepoType_shouldPass_whenGeneralIsNull() throws RepoConfigException {
        RepositoryConfigModel<?, ?, ?> model = mock(RepositoryConfigModel.class);
        when(model.getGeneral()).thenReturn(null);
        when(model.getTypeSpecific()).thenReturn(mock(TypeSpecificConfigModel.class));
        validator.validateEnvironmentsForRepoType(model, Set.of("PROD"));
    }

    @Test
    void validateEnvironmentsForRepoType_shouldPass_whenTypeSpecificIsNull() throws RepoConfigException {
        RepositoryConfigModel<?, ?, ?> model = mock(RepositoryConfigModel.class);
        when(model.getGeneral()).thenReturn(new GeneralRepositoryConfigModel());
        when(model.getTypeSpecific()).thenReturn(null);
        validator.validateEnvironmentsForRepoType(model, Set.of("PROD"));
    }

    @ParameterizedTest
    @MethodSource("environmentAgnosticRepoTypes")
    void validateEnvironmentsForRepoType_shouldPass_whenNullCurrentEnvironmentsAndNoneRequested(RepoType repoType)
            throws RepoConfigException {
        RepositoryConfigModel<?, ?, ?> model = buildModelWithRepoType(repoType, Set.of());
        validator.validateEnvironmentsForRepoType(model, null);
    }

    private static Stream<Arguments> environmentAgnosticRepoTypes() {
        return Stream.of(
                Arguments.of(RepoType.BuildInfo),
                Arguments.of(RepoType.ReleaseBundles)
        );
    }

    private static RepositoryConfigModel<?, ?, ?> buildModelWithRepoType(RepoType repoType, Set<String> environments) {
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setEnvironments(environments);
        TypeSpecificConfigModel typeSpecific = mock(TypeSpecificConfigModel.class);
        when(typeSpecific.getRepoType()).thenReturn(repoType);
        RepositoryConfigModel<?, ?, ?> model = mock(RepositoryConfigModel.class);
        when(model.getGeneral()).thenReturn(general);
        when(model.getTypeSpecific()).thenReturn(typeSpecific);
        return model;
    }

    @Test
    void testValidateRepoName() throws RepoConfigException {
        when(repositoryConfigService.existsByKeyIgnoreCase("cool-name")).thenReturn(false);
        validator.validateRepoNameForCreate("cool-name", "local");
        validator.validateRepoNameForUpdate("cool-name");
    }

    @ParameterizedTest
    @MethodSource("invalidRepoNames")
    void testValidateRepoNameException(String repoKey) {
        assertThrows(RepoConfigException.class, () -> validator.validateRepoNameForCreate(repoKey, "local"));
        assertThrows(RepoConfigException.class, () -> validator.validateRepoNameForUpdate(repoKey));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidProjectContext")
    void testValidateProjectContext(RepoConfigRequest request) {
        assertThrows(RepoConfigException.class, () -> validator.validateProjectContext(request));
    }

    private static Stream<Arguments> invalidRepoNames() {
        return Stream.of(
                Arguments.of("jfrog-logs"),
                Arguments.of("jfrog-usage-logs"),
                Arguments.of("jfrog-billing-logs")
        );
    }

    private void prepareValidation(RepoConfigValidator configValidator) {
        CentralConfigService configService = mock(CentralConfigService.class);
        configValidator.setCentralConfig(configService);
        CentralConfigDescriptorImpl centralConfigDescriptor = new CentralConfigDescriptorImpl();
        RepoLayout simpleRepoLayout = new RepoLayoutBuilder().name("simple-default")
                .artifactPathPattern("mm").distinctiveDescriptorPathPattern(false).build();
        centralConfigDescriptor.setRepoLayouts(List.of(simpleRepoLayout));
        when(configService.getDescriptor()).thenReturn(centralConfigDescriptor);
        AddonsManager addonsManager = mock(AddonsManager.class);
        configValidator.setAddonsManager(addonsManager);
        when(addonsManager.isEntitled(any())).thenReturn(true);
    }

    private void setGeneralRemoteConfig(RemoteRepositoryConfigModel model) {
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey("repo");
        model.setGeneral(general);
    }

    private void setBasicRemoteConfig(RemoteRepositoryConfigModel model) {
        RemoteBasicRepositoryConfigModel basic = mock(RemoteBasicRepositoryConfigModel.class);
        when(basic.getLayout()).thenReturn("simple-default");
        when(basic.getUrl()).thenReturn("http://localhost");
        model.setBasic(basic);
    }

    private static Stream<Arguments> provideDockerCachePeriodAndRepoType() {
        return Stream.of(
                Arguments.of(null, true, DEFAULT_DOCKER_REMOTE_RETRIEVAL_CACHE_PERIOD),
                Arguments.of(null, false, DEFAULT_RETRIEVAL_CACHE_PERIOD),
                Arguments.of(36000L, true, 36000L),
                Arguments.of(36000L, false, 36000L)
        );
    }

    private static Stream<Arguments> provideGoCachePeriodAndRepoType() {
        return Stream.of(
                Arguments.of(null, true, DEFAULT_GO_REMOTE_METADATA_MISSED_RETRIEVAL_CACHE_PERIOD),
                Arguments.of(null, false, DEFAULT_MISSED_RETRIEVAL_PERIOD),
                Arguments.of(36000L, true, 36000L),
                Arguments.of(36000L, false, 36000L)
        );
    }

    private static Stream<Arguments> provideInvalidProjectContext() {
        return Stream.of(
                Arguments.of(RepoConfigRequest.builder()
                                .projectContext(true)
                                .projectKeys(Set.of("project1", "project2"))
                                .build()),
                Arguments.of(RepoConfigRequest.builder()
                        .projectContext(true)
                        .projectKeys(Set.of())
                        .build())
        );
    }

    private RemoteAdvancedRepositoryConfigModel getAdvancedRepoConfigModelAndSetRetrievalCachePeriod(Long cachePeriod) {
        RemoteAdvancedRepositoryConfigModel remoteAdvancedRepositoryConfigModel =
                new RemoteAdvancedRepositoryConfigModel();
        RemoteCacheRepositoryConfigModel remoteCacheRepositoryConfigModel = new RemoteCacheRepositoryConfigModel();
        remoteAdvancedRepositoryConfigModel.setCache(remoteCacheRepositoryConfigModel);
        remoteCacheRepositoryConfigModel.setRetrievalCachePeriodSecs(cachePeriod);
        return remoteAdvancedRepositoryConfigModel;
    }

    private RemoteAdvancedRepositoryConfigModel getAdvancedRepoConfigModelAndSetMissedRetrievalCachePeriod(
            Long cachePeriod) {
        RemoteAdvancedRepositoryConfigModel remoteAdvancedRepositoryConfigModel =
                new RemoteAdvancedRepositoryConfigModel();
        RemoteCacheRepositoryConfigModel remoteCacheRepositoryConfigModel = new RemoteCacheRepositoryConfigModel();
        remoteAdvancedRepositoryConfigModel.setCache(remoteCacheRepositoryConfigModel);
        remoteCacheRepositoryConfigModel.setMissedRetrievalCachePeriodSecs(cachePeriod);
        return remoteAdvancedRepositoryConfigModel;
    }

    private RemoteRepositoryConfigModel getRemoteRepositoryConfigModel(
            RemoteAdvancedRepositoryConfigModel remoteAdvancedRepositoryConfigModel,
            TypeSpecificConfigModel typeSpecificConfigModel) {
        RemoteRepositoryConfigModel remoteRepositoryConfigModel = new RemoteRepositoryConfigModel();
        remoteRepositoryConfigModel.setBasic(new RemoteBasicRepositoryConfigModel());
        remoteRepositoryConfigModel.setGeneral(new GeneralRepositoryConfigModel());
        remoteRepositoryConfigModel.setAdvanced(remoteAdvancedRepositoryConfigModel);
        remoteRepositoryConfigModel.setTypeSpecific(typeSpecificConfigModel);
        return remoteRepositoryConfigModel;
    }

    @Test
    void validateRemote_shouldPass_whenMinimumCachePeriodFeatureFlagDisabled() throws RepoConfigException {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "false");
        RemoteRepositoryConfigModel model = new RemoteRepositoryConfigModel();
        setGeneralRemoteConfig(model);
        RemoteAdvancedRepositoryConfigModel advanced = new RemoteAdvancedRepositoryConfigModel();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        cache.setRetrievalCachePeriodSecs(0L);
        advanced.setCache(cache);
        model.setAdvanced(advanced);
        setBasicRemoteConfig(model);
        BowerTypeSpecificConfigModel mock = mock(BowerTypeSpecificConfigModel.class);
        when(mock.getRepoType()).thenReturn(RepoType.Bower);
        model.setTypeSpecific(mock);
        RepoConfigValidator configValidator = new RepoConfigValidator();
        prepareValidation(configValidator);

        configValidator.validateRemote(model);
    }

    @Test
    void validateRemote_shouldPass_whenCachePeriodMeetsMinimum() throws RepoConfigException {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        RemoteRepositoryConfigModel model = new RemoteRepositoryConfigModel();
        setGeneralRemoteConfig(model);
        RemoteAdvancedRepositoryConfigModel advanced = new RemoteAdvancedRepositoryConfigModel();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        cache.setRetrievalCachePeriodSecs(30L);
        cache.setMetadataRetrievalTimeoutSecs(30L);
        advanced.setCache(cache);
        model.setAdvanced(advanced);
        setBasicRemoteConfig(model);
        BowerTypeSpecificConfigModel mock = mock(BowerTypeSpecificConfigModel.class);
        when(mock.getRepoType()).thenReturn(RepoType.Bower);
        model.setTypeSpecific(mock);
        RepoConfigValidator configValidator = new RepoConfigValidator();
        prepareValidation(configValidator);

        configValidator.validateRemote(model);
    }


    @Test
    void validateRemote_shouldPass_whenSmartRepository() throws RepoConfigException {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        RemoteRepositoryConfigModel model = new RemoteRepositoryConfigModel();
        setGeneralRemoteConfig(model);
        RemoteAdvancedRepositoryConfigModel advanced = new RemoteAdvancedRepositoryConfigModel();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        cache.setRetrievalCachePeriodSecs(0L);
        advanced.setCache(cache);
        model.setAdvanced(advanced);
        RemoteBasicRepositoryConfigModel basic = mock(RemoteBasicRepositoryConfigModel.class);
        when(basic.getLayout()).thenReturn("simple-default");
        when(basic.getUrl()).thenReturn("https://acme.jfrog.io/artifactory/maven-remote");
        ContentSynchronisation contentSynchronisation = new ContentSynchronisation();
        contentSynchronisation.enable();
        when(basic.getContentSynchronisation()).thenReturn(contentSynchronisation);
        model.setBasic(basic);
        BowerTypeSpecificConfigModel mock = mock(BowerTypeSpecificConfigModel.class);
        when(mock.getRepoType()).thenReturn(RepoType.Bower);
        model.setTypeSpecific(mock);
        RepoConfigValidator configValidator = new RepoConfigValidator();
        prepareValidation(configValidator);

        configValidator.validateRemote(model);
    }

    @Test
    void validateRemote_shouldFail_whenJFrogStyleUrlButContentSynchronisationDisabled() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        RemoteRepositoryConfigModel model = new RemoteRepositoryConfigModel();
        setGeneralRemoteConfig(model);
        RemoteAdvancedRepositoryConfigModel advanced = new RemoteAdvancedRepositoryConfigModel();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        cache.setRetrievalCachePeriodSecs(0L);
        advanced.setCache(cache);
        model.setAdvanced(advanced);
        RemoteBasicRepositoryConfigModel basic = mock(RemoteBasicRepositoryConfigModel.class);
        when(basic.getLayout()).thenReturn("simple-default");
        when(basic.getUrl()).thenReturn("https://acme.jfrog.io/artifactory/maven-remote");
        when(basic.getContentSynchronisation()).thenReturn(new ContentSynchronisation());
        model.setBasic(basic);
        BowerTypeSpecificConfigModel mock = mock(BowerTypeSpecificConfigModel.class);
        when(mock.getRepoType()).thenReturn(RepoType.Bower);
        model.setTypeSpecific(mock);
        RepoConfigValidator configValidator = new RepoConfigValidator();
        prepareValidation(configValidator);

        assertThrows(RepoConfigException.class, () -> configValidator.validateRemote(model));
    }

    @Test
    void validateRemote_shouldPass_whenUrlIsWhitelisted() throws RepoConfigException {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        getBound().setProperty(ConstantValues.remoteRepoMinMetadataCachePeriodWhitelistUrls,
                "https://internal.company.com");
        RemoteRepositoryConfigModel model = new RemoteRepositoryConfigModel();
        setGeneralRemoteConfig(model);
        RemoteAdvancedRepositoryConfigModel advanced = new RemoteAdvancedRepositoryConfigModel();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        cache.setRetrievalCachePeriodSecs(0L);
        advanced.setCache(cache);
        model.setAdvanced(advanced);
        RemoteBasicRepositoryConfigModel basic = mock(RemoteBasicRepositoryConfigModel.class);
        when(basic.getLayout()).thenReturn("simple-default");
        when(basic.getUrl()).thenReturn("https://internal.company.com/repo");
        model.setBasic(basic);
        BowerTypeSpecificConfigModel mock = mock(BowerTypeSpecificConfigModel.class);
        when(mock.getRepoType()).thenReturn(RepoType.Bower);
        model.setTypeSpecific(mock);
        RepoConfigValidator configValidator = new RepoConfigValidator();
        prepareValidation(configValidator);

        configValidator.validateRemote(model);
    }

    @Test
    void validateRemote_shouldFail_whenCachePeriodBelowMinimum() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        RemoteRepositoryConfigModel model = new RemoteRepositoryConfigModel();
        setGeneralRemoteConfig(model);
        RemoteAdvancedRepositoryConfigModel advanced = new RemoteAdvancedRepositoryConfigModel();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        cache.setRetrievalCachePeriodSecs(0L);
        advanced.setCache(cache);
        model.setAdvanced(advanced);
        setBasicRemoteConfig(model);
        BowerTypeSpecificConfigModel mock = mock(BowerTypeSpecificConfigModel.class);
        when(mock.getRepoType()).thenReturn(RepoType.Bower);
        model.setTypeSpecific(mock);
        RepoConfigValidator configValidator = new RepoConfigValidator();
        prepareValidation(configValidator);

        RepoConfigException exception = assertThrows(RepoConfigException.class,
                () -> configValidator.validateRemote(model));
        assertTrue(exception.getMessage().contains("must be at least 30 seconds"));
        assertTrue(exception.getMessage().contains("Current value: 0 seconds"));
    }

    @Test
    void validateRemote_shouldFail_whenVariousCachePeriodsBelowMinimum() {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        RemoteRepositoryConfigModel model = new RemoteRepositoryConfigModel();
        setGeneralRemoteConfig(model);
        RemoteAdvancedRepositoryConfigModel advanced = new RemoteAdvancedRepositoryConfigModel();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        cache.setRetrievalCachePeriodSecs(10L);
        advanced.setCache(cache);
        model.setAdvanced(advanced);
        setBasicRemoteConfig(model);
        BowerTypeSpecificConfigModel mock = mock(BowerTypeSpecificConfigModel.class);
        when(mock.getRepoType()).thenReturn(RepoType.Bower);
        model.setTypeSpecific(mock);
        RepoConfigValidator configValidator = new RepoConfigValidator();
        prepareValidation(configValidator);

        assertThrows(RepoConfigException.class, () -> configValidator.validateRemote(model));
    }

    @Test
    void validateRemote_shouldPass_whenGenericRemoteAndCacheBelowMinimum() throws RepoConfigException {
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        RemoteRepositoryConfigModel model = new RemoteRepositoryConfigModel();
        setGeneralRemoteConfig(model);
        RemoteAdvancedRepositoryConfigModel advanced = new RemoteAdvancedRepositoryConfigModel();
        RemoteCacheRepositoryConfigModel cache = new RemoteCacheRepositoryConfigModel();
        cache.setRetrievalCachePeriodSecs(0L);
        advanced.setCache(cache);
        model.setAdvanced(advanced);
        setBasicRemoteConfig(model);
        model.setTypeSpecific(new GenericTypeSpecificConfigModel());
        RepoConfigValidator configValidator = new RepoConfigValidator();
        prepareValidation(configValidator);

        configValidator.validateRemote(model);
    }
}
