package org.artifactory.repo.interceptor.repocud;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.crypto.CryptoService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoMetadata;
import org.artifactory.repo.interceptor.RepoEventTriggerSource;
import org.artifactory.repo.interceptor.RepoEventTriggerSourceResolver;
import org.artifactory.sensitive.CustomHttpHeadersSensitiveHandler;
import org.artifactory.sensitive.fields.CryptoServiceImpl;
import org.artifactory.sensitive.fields.DynamicSensitiveFieldHandler;
import org.artifactory.sensitive.fields.SensitiveStringFieldHandler;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.unifiedevent.model.repo.RepoConfigEventData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.artifactory.unifiedevent.constants.SupportedUnifiedEvents.AFTER_REPO_CREATE;
import static org.artifactory.unifiedevent.constants.SupportedUnifiedEvents.AFTER_REPO_DELETE;
import static org.artifactory.unifiedevent.constants.SupportedUnifiedEvents.AFTER_REPO_UPDATE;
import static org.artifactory.unifiedevent.constants.SupportedUnifiedEvents.BEFORE_REPO_CREATE;
import static org.artifactory.unifiedevent.constants.SupportedUnifiedEvents.BEFORE_REPO_DELETE;
import static org.artifactory.unifiedevent.constants.SupportedUnifiedEvents.BEFORE_REPO_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepoConfigEventDataBuilderTest extends ArtifactoryHomeBoundJUnitTest {

    private static final RepoMetadata EMPTY_METADATA = RepoMetadata.empty();

    @Mock
    private SecurityService securityService;
    @Mock
    private RepoEventTriggerSourceResolver triggerSourceResolver;
    @Mock
    private CryptoService cryptoService;
    private AutoCloseable mocks;
    private RepoConfigEventDataBuilder underTest;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        getOrCreateArtifactoryHomeStub();
        SecurityContextHolder.clearContext();
        when(securityService.currentUsername()).thenReturn("admin");
        when(securityService.getAuthenticatedRealm()).thenReturn("internal");
        underTest = new RepoConfigEventDataBuilder(securityService, triggerSourceResolver, cryptoService);
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void build_shouldIncludeMaskedConfigAndMetadata_whenRepoCreated() {
        LocalRepoConfig repoConfig = LocalRepoConfig.create("my-repo", RepoType.Generic);
        when(cryptoService.mask(anyList())).thenReturn(List.of(repoConfig));
        when(triggerSourceResolver.resolve()).thenReturn(RepoEventTriggerSource.SYSTEM_INTERNAL.name());
        RepoConfigChange change = RepoConfigChange.createOf(repoConfig);

        RepoConfigEventData data = underTest.build(change, AFTER_REPO_CREATE, null, EMPTY_METADATA);

        verify(cryptoService).mask(anyList());
        assertEquals("admin", data.context().actor());
        assertEquals("my-repo", data.metadata().repositoryKey());
        assertEquals("local", data.metadata().repositoryType());
        assertEquals("generic", data.metadata().packageType());
        assertEquals(RepoEventTriggerSource.SYSTEM_INTERNAL.name(), data.triggerSource());
        assertNotNull(data.config());
        assertSame(repoConfig, data.config());
    }

    @Test
    void build_shouldNotIncludeConfig_whenRepoUpdated() {
        LocalRepoConfig oldConfig = LocalRepoConfig.create("my-repo", RepoType.Generic);
        LocalRepoConfig newConfig = LocalRepoConfig.create("my-repo", RepoType.Generic);
        when(triggerSourceResolver.resolve()).thenReturn(RepoEventTriggerSource.JFROG_UI.name());
        RepoConfigChange change = RepoConfigChange.updateOf(oldConfig, newConfig);

        RepoConfigEventData data = underTest.build(change, AFTER_REPO_UPDATE, null, EMPTY_METADATA);

        assertEquals("my-repo", data.metadata().repositoryKey());
        assertNull(data.config());
    }

    @Test
    void build_shouldPopulateFieldLevelChangeAttributes_whenRepoUpdated() {
        LocalRepoConfig oldConfig = LocalRepoConfig.create("my-repo", RepoType.Generic);
        oldConfig.getBaseConfig().setDescription("old description");
        LocalRepoConfig newConfig = LocalRepoConfig.create("my-repo", RepoType.Generic);
        newConfig.getBaseConfig().setDescription("new description");
        when(triggerSourceResolver.resolve()).thenReturn(RepoEventTriggerSource.JFROG_UI.name());
        RepoConfigChange change = RepoConfigChange.updateOf(oldConfig, newConfig);

        RepoConfigEventData data = underTest.build(change, AFTER_REPO_UPDATE, null, EMPTY_METADATA);

        assertNull(data.config());
        assertNotNull(data.changeAttributes());
        assertTrue(data.changeAttributes().stream().anyMatch(
                attr -> "old description".equals(attr.oldValue()) && "new description".equals(attr.newValue())));
    }

    @Test
    void build_shouldIncludeMaskedConfig_whenBeforeRepoCreate() {
        LocalRepoConfig repoConfig = LocalRepoConfig.create("my-repo", RepoType.Generic);
        when(cryptoService.mask(anyList())).thenReturn(List.of(repoConfig));
        when(triggerSourceResolver.resolve()).thenReturn(RepoEventTriggerSource.SYSTEM_INTERNAL.name());
        RepoConfigChange change = RepoConfigChange.createOf(repoConfig);

        RepoConfigEventData data = underTest.build(change, BEFORE_REPO_CREATE, null, EMPTY_METADATA);

        assertNotNull(data.config());
        assertSame(repoConfig, data.config());
    }

    @Test
    void build_shouldInclude207StatusCode_whenPartialDeleteOccurred() {
        RepoConfig repoConfig = org.mockito.Mockito.mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn("libs-release");
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(triggerSourceResolver.resolve()).thenReturn(RepoEventTriggerSource.JFROG_CLI.name());
        RepoConfigChange change = RepoConfigChange.deleteOf(repoConfig);

        RepoConfigEventData data = underTest.build(change, AFTER_REPO_DELETE, 207, EMPTY_METADATA);

        assertEquals(207, data.statusCode());
        assertEquals("libs-release", data.metadata().repositoryKey());
    }

    @Test
    void build_shouldRedactSensitiveFields_whenRealCryptoServiceMasksLocalConfig() {
        LocalRepoConfig localConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);

        bindContextForDynamicSensitiveHandlers();
        SensitiveStringFieldHandler stringHandler = new SensitiveStringFieldHandler();
        stringHandler.init();
        CryptoServiceImpl realCrypto = new CryptoServiceImpl(
                List.of(stringHandler, new DynamicSensitiveFieldHandler()));
        RepoConfigEventDataBuilder realBuilder =
                new RepoConfigEventDataBuilder(securityService, triggerSourceResolver, realCrypto);
        when(triggerSourceResolver.resolve()).thenReturn(RepoEventTriggerSource.SYSTEM_INTERNAL.name());
        RepoConfigChange change = RepoConfigChange.createOf(localConfig);

        RepoConfigEventData data = realBuilder.build(change, AFTER_REPO_CREATE, null, EMPTY_METADATA);

        assertNotNull(data.config());
    }

    @Test
    void build_shouldOmitConfig_whenCryptoServiceMaskReturnsEmpty() {
        LocalRepoConfig repoConfig = LocalRepoConfig.create("my-repo", RepoType.Generic);
        when(cryptoService.mask(anyList())).thenReturn(List.of());
        when(triggerSourceResolver.resolve()).thenReturn(RepoEventTriggerSource.SYSTEM_INTERNAL.name());
        RepoConfigChange change = RepoConfigChange.createOf(repoConfig);

        RepoConfigEventData data = underTest.build(change, BEFORE_REPO_CREATE, null, EMPTY_METADATA);

        verify(cryptoService).mask(anyList());
        assertNull(data.config());
    }

    @Test
    void build_shouldLeaveRepositoryTypeNull_whenChangeHasNoRepoType() {
        RepoConfig repoConfig = org.mockito.Mockito.mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn("my-repo");
        when(repoConfig.getRepoType()).thenReturn(null);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(triggerSourceResolver.resolve()).thenReturn(RepoEventTriggerSource.REST_API.name());
        RepoConfigChange change = RepoConfigChange.createOf(repoConfig);

        RepoConfigEventData data = underTest.build(change, AFTER_REPO_CREATE, null, EMPTY_METADATA);

        assertNull(data.metadata().repositoryType());
        assertEquals("generic", data.metadata().packageType());
    }

    @Test
    void build_shouldLeavePackageTypeNull_whenChangeHasNoPackageType() {
        RepoConfig repoConfig = org.mockito.Mockito.mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn("my-repo");
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(null);
        when(triggerSourceResolver.resolve()).thenReturn(RepoEventTriggerSource.REST_API.name());
        RepoConfigChange change = RepoConfigChange.createOf(repoConfig);

        RepoConfigEventData data = underTest.build(change, AFTER_REPO_CREATE, null, EMPTY_METADATA);

        assertEquals("local", data.metadata().repositoryType());
        assertNull(data.metadata().packageType());
    }

    @Test
    void build_shouldUseOldRepoConfig_whenDeleteChangeHasOnlyOldConfig() {
        RepoConfig repoConfig = org.mockito.Mockito.mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn("libs-release");
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(triggerSourceResolver.resolve()).thenReturn(RepoEventTriggerSource.JFROG_CLI.name());
        RepoConfigChange change = RepoConfigChange.deleteOf(repoConfig);

        RepoConfigEventData data = underTest.build(change, BEFORE_REPO_DELETE, null, EMPTY_METADATA);

        assertEquals("libs-release", data.metadata().repositoryKey());
        assertEquals("local", data.metadata().repositoryType());
    }

    @Test
    void build_shouldPopulateChangeAttributes_whenBeforeRepoUpdate() {
        LocalRepoConfig oldConfig = LocalRepoConfig.create("my-repo", RepoType.Generic);
        oldConfig.getBaseConfig().setDescription("old");
        LocalRepoConfig newConfig = LocalRepoConfig.create("my-repo", RepoType.Generic);
        newConfig.getBaseConfig().setDescription("new");
        when(triggerSourceResolver.resolve()).thenReturn(RepoEventTriggerSource.JFROG_UI.name());
        RepoConfigChange change = RepoConfigChange.updateOf(oldConfig, newConfig);

        RepoConfigEventData data = underTest.build(change, BEFORE_REPO_UPDATE, null, EMPTY_METADATA);

        assertNotNull(data.changeAttributes());
        assertFalse(data.changeAttributes().isEmpty());
    }

    @Test
    void build_shouldRedactSensitiveFields_whenRealCryptoServiceMasksRemoteConfig() throws Exception {
        String plainPassword = "super-secret-p@ss!";
        RemoteRepoConfig remoteConfig = RemoteRepoConfig.create("remote-repo", RepoType.Maven);
        remoteConfig.getRepoTypeConfig().setPassword(plainPassword);

        bindContextForDynamicSensitiveHandlers();
        SensitiveStringFieldHandler stringHandler = new SensitiveStringFieldHandler();
        stringHandler.init();
        CryptoServiceImpl realCrypto = new CryptoServiceImpl(
                List.of(stringHandler, new DynamicSensitiveFieldHandler()));
        RepoConfigEventDataBuilder realBuilder =
                new RepoConfigEventDataBuilder(securityService, triggerSourceResolver, realCrypto);
        when(triggerSourceResolver.resolve()).thenReturn(RepoEventTriggerSource.SYSTEM_INTERNAL.name());
        RepoConfigChange change = RepoConfigChange.createOf(remoteConfig);

        RepoConfigEventData data = realBuilder.build(change, AFTER_REPO_CREATE, null, EMPTY_METADATA);

        assertNotNull(data.config());
        String json = new ObjectMapper().writeValueAsString(data.config());
        assertFalse(json.contains(plainPassword));
        assertTrue(json.contains("***"));
    }

    @SuppressWarnings("unchecked")
    private static void bindContextForDynamicSensitiveHandlers() {
        ArtifactoryContext context = org.mockito.Mockito.mock(ArtifactoryContext.class);
        when(context.beanForType(org.mockito.ArgumentMatchers.any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type == CustomHttpHeadersSensitiveHandler.class) {
                CustomHttpHeadersSensitiveHandler handler = new CustomHttpHeadersSensitiveHandler();
                handler.init();
                return handler;
            }
            throw new IllegalArgumentException("Unexpected handler type: " + type.getName());
        });
        ArtifactoryContextThreadBinder.bind(context);
    }
}
