package org.artifactory.repo.interceptor.config;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
abstract class PackageRepoEnforceLayoutInterceptorTest extends ArtifactoryHomeBoundJUnitTest {
    protected static final String REPO_KEY = "repo";

    @Mock
    CentralConfigService centralConfigService;
    @Mock
    ArtifactoryContext context;

    PackageRepoEnforceLayoutInterceptor packageRepoEnforceLayoutInterceptor;

    @BeforeEach
    void setUp() {
        packageRepoEnforceLayoutInterceptor = new PackageRepoEnforceLayoutInterceptor(centralConfigService);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.isReposImportInProgress()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @EnumSource(value = RepoDetailsType.class)
    void testOnBeforeSave_ConsiderLocalAndFederatedRepos(RepoDetailsType detailsType) {
        boolean isLocalOrFederated =
                detailsType == RepoDetailsType.LOCAL || detailsType == RepoDetailsType.FEDERATED;

        RepoConfig repoConfig = createRepoConfig(RepoType.Generic);
        RepoConfigChange repoConfigChange =
                spy(new RepoConfigChange(REPO_KEY, RepoConfigChange.ConfigAction.REPO_CREATE,
                        repoConfig, repoConfig));
        packageRepoEnforceLayoutInterceptor.onBeforeSave(Map.of(detailsType, List.of(repoConfigChange)));
        verify(repoConfigChange, times(isLocalOrFederated ? 2 : 0)).getConfigAction();
    }

    @ParameterizedTest
    @EnumSource(value = RepoConfigChange.ConfigAction.class)
    void testOnBeforeSave_ConsiderOnlyUpdateOrCreate(RepoConfigChange.ConfigAction configAction) {
        boolean isUpdateOrCreate = configAction == RepoConfigChange.ConfigAction.REPO_CREATE ||
                configAction == RepoConfigChange.ConfigAction.REPO_UPDATE;
        RepoConfig repoConfig = createRepoConfig(RepoType.Generic);
        RepoConfigChange repoConfigChange = spy(new RepoConfigChange(REPO_KEY, configAction, repoConfig, repoConfig));
        packageRepoEnforceLayoutInterceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL, List.of(repoConfigChange)));
        verify(repoConfig, times(isUpdateOrCreate ? 1 : 0)).getPackageType();
    }

    @Test
    void testOnBeforeSave_NullCheckForOldAndNewConfigWithUpdateAction() {
        RepoConfig repoConfig = createRepoConfig(RepoType.Generic);
        RepoConfigChange repoConfigChange =
                spy(new RepoConfigChange(REPO_KEY, RepoConfigChange.ConfigAction.REPO_UPDATE,
                        repoConfig, null));
        packageRepoEnforceLayoutInterceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL, List.of(repoConfigChange)));
        verify(repoConfig, never()).getPackageType();
        repoConfigChange = spy(new RepoConfigChange(REPO_KEY, RepoConfigChange.ConfigAction.REPO_UPDATE,
                null, repoConfig));
        packageRepoEnforceLayoutInterceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL, List.of(repoConfigChange)));
        verify(repoConfig, never()).getPackageType();
    }

    @Test
    void testOnBeforeSave_NullCheckForNewConfigWithCreateAction() {
        RepoConfig repoConfig = createRepoConfig(RepoType.Generic);
        RepoConfigChange repoConfigChange =
                spy(new RepoConfigChange(REPO_KEY, RepoConfigChange.ConfigAction.REPO_CREATE,
                        null, repoConfig));
        packageRepoEnforceLayoutInterceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL, List.of(repoConfigChange)));
        verify(repoConfig, never()).getPackageType();
    }

    @Test
    void testOnBeforeSave_NullCheckForOldAndNewConfig() {
        RepoConfig repoConfig = createRepoConfig(RepoType.Generic);
        RepoConfigChange repoConfigChange =
                spy(new RepoConfigChange(REPO_KEY, RepoConfigChange.ConfigAction.REPO_UPDATE,
                        repoConfig, null));
        packageRepoEnforceLayoutInterceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL, List.of(repoConfigChange)));
        verify(repoConfig, never()).getPackageType();
        repoConfigChange = spy(new RepoConfigChange(REPO_KEY, RepoConfigChange.ConfigAction.REPO_UPDATE,
                null, repoConfig));
        packageRepoEnforceLayoutInterceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL, List.of(repoConfigChange)));
        verify(repoConfig, never()).getPackageType();
    }

    @Test
    void testOnBeforeSave_EnforceLayoutFieldsAreNull() {
        RepoConfig oldRepoConfig = createRepoConfig(getRepoType());
        RepoConfig newRepoConfig = createRepoConfig(getRepoType());
        RepoConfigChange repoConfigChange =
                spy(new RepoConfigChange(REPO_KEY, RepoConfigChange.ConfigAction.REPO_UPDATE,
                        newRepoConfig, oldRepoConfig));
        Map<String, String> oldPackageTypeConfig = Map.of("ignore", "true");
        Map<String, String> newPackageTypeConfig = Map.of("ignore", "false");
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);
        when(oldRepoConfig.getPackageTypeConfig()).thenReturn(oldPackageTypeConfig);
        Assertions.assertDoesNotThrow(() -> packageRepoEnforceLayoutInterceptor.onBeforeSave(
                Map.of(RepoDetailsType.LOCAL, List.of(repoConfigChange))));
    }

    @Test
    void testOnBeforeSave_EnforceLayoutFieldsNotChange() {
        RepoConfig oldRepoConfig = createRepoConfig(getRepoType());
        RepoConfig newRepoConfig = createRepoConfig(getRepoType());
        RepoConfigChange repoConfigChange =
                spy(new RepoConfigChange(REPO_KEY, RepoConfigChange.ConfigAction.REPO_UPDATE,
                        newRepoConfig, oldRepoConfig));
        Map<String, String> oldPackageTypeConfig = getEnforceLayoutDefaultSettings();
        Map<String, String> newPackageTypeConfig = getEnforceLayoutDefaultSettings();
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);
        when(oldRepoConfig.getPackageTypeConfig()).thenReturn(oldPackageTypeConfig);
        Assertions.assertDoesNotThrow(() -> packageRepoEnforceLayoutInterceptor.onBeforeSave(
                Map.of(RepoDetailsType.LOCAL, List.of(repoConfigChange))));
    }

    @Test
    void testOnBeforeSave_EnforceLayoutFieldsChange() {
        RepoConfig oldRepoConfig = createRepoConfig(getRepoType());
        RepoConfig newRepoConfig = createRepoConfig(getRepoType());
        RepoConfigChange repoConfigChange =
                spy(new RepoConfigChange(REPO_KEY, RepoConfigChange.ConfigAction.REPO_UPDATE,
                        newRepoConfig, oldRepoConfig));
        Map<String, String> oldPackageTypeConfig = getEnforceLayoutDefaultSettings();
        Map<String, String> newPackageTypeConfig = createModifiedEnforceLayoutSettingsMap();
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);
        when(oldRepoConfig.getPackageTypeConfig()).thenReturn(oldPackageTypeConfig);
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                () -> packageRepoEnforceLayoutInterceptor.onBeforeSave(
                        Map.of(RepoDetailsType.LOCAL, List.of(repoConfigChange))));
        String expectedMsgPattern = "Cannot change enforce layout field '.*' for repository: repo";
        Assertions.assertTrue(exception.getMessage().matches(expectedMsgPattern));
    }

    @Test
    void testOnBeforeSave_OldConfigDoesNotHaveEnforceLayoutFields() {
        RepoConfig oldRepoConfig = createRepoConfig(getRepoType());
        RepoConfig newRepoConfig = createRepoConfig(getRepoType());
        RepoConfigChange repoConfigChange =
                spy(new RepoConfigChange(REPO_KEY, RepoConfigChange.ConfigAction.REPO_UPDATE,
                        newRepoConfig, oldRepoConfig));
        Map<String, String> oldPackageTypeConfig = Map.of(); // old config does not have 'enforce layout' fields
        Map<String, String> newPackageTypeConfig = getEnforceLayoutDefaultSettings();
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(newPackageTypeConfig);
        when(oldRepoConfig.getPackageTypeConfig()).thenReturn(oldPackageTypeConfig);
        Assertions.assertDoesNotThrow(() -> packageRepoEnforceLayoutInterceptor.onBeforeSave(
                Map.of(RepoDetailsType.LOCAL, List.of(repoConfigChange))));
    }

    @Test
    void testOnBeforeSave_EnforceLayoutFieldsSetByGlobalConfig() {
        enableEnforceLayoutSettings();
        RepoConfig newRepoConfig = createRepoConfig(getRepoType());
        RepoConfigChange repoConfigChange =
                spy(new RepoConfigChange(REPO_KEY, RepoConfigChange.ConfigAction.REPO_CREATE,
                        newRepoConfig, null));
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(getEnforceLayoutDefaultSettings());
        packageRepoEnforceLayoutInterceptor.onBeforeSave(
                Map.of(RepoDetailsType.LOCAL, List.of(repoConfigChange)));
        getEnforceLayoutDefaultSettings().forEach(
                (key, value) -> Assertions.assertNotEquals(value, newRepoConfig.getPackageTypeConfig().get(key)));
        createModifiedEnforceLayoutSettingsMap().forEach(
                (key, value) -> Assertions.assertEquals(value, newRepoConfig.getPackageTypeConfig().get(key)));
    }

    @Test
    void testOnBeforeSave_EnforceLayoutFieldsNotSet() {
        RepoConfig newRepoConfig = createRepoConfig(getRepoType());
        RepoConfigChange repoConfigChange =
                spy(new RepoConfigChange(REPO_KEY, RepoConfigChange.ConfigAction.REPO_CREATE,
                        newRepoConfig, null));
        when(newRepoConfig.getPackageTypeConfig()).thenReturn(createModifiedEnforceLayoutSettingsMap());
        packageRepoEnforceLayoutInterceptor.onBeforeSave(
                Map.of(RepoDetailsType.FEDERATED, List.of(repoConfigChange)));
        getEnforceLayoutDefaultSettings().forEach(
                (key, value) -> Assertions.assertNotEquals(value, newRepoConfig.getPackageTypeConfig().get(key)));
        createModifiedEnforceLayoutSettingsMap().forEach(
                (key, value) -> Assertions.assertEquals(value, newRepoConfig.getPackageTypeConfig().get(key)));
    }

    protected abstract RepoType getRepoType();

    protected abstract Map<String, String> getEnforceLayoutDefaultSettings();

    protected abstract void enableEnforceLayoutSettings();


    private RepoConfig createRepoConfig(RepoType repoType) {
        RepoConfig repoConfig = mock(RepoConfig.class);
        lenient().when(repoConfig.getPackageType()).thenReturn(repoType);
        return repoConfig;
    }

    private Map<String, String> createModifiedEnforceLayoutSettingsMap() {
        Map<String, String> enforceLayoutSettings = getEnforceLayoutDefaultSettings();
        Map<String, String> modifiedEnforceLayoutSettings = new HashMap<>();
        enforceLayoutSettings.forEach((key, value) -> {
            boolean currentValue = Boolean.parseBoolean(value);
            modifiedEnforceLayoutSettings.put(key, String.valueOf(!currentValue));
        });
        return modifiedEnforceLayoutSettings;
    }
}