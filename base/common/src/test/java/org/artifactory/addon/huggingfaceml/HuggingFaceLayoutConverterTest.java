
package org.artifactory.addon.huggingfaceml;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.SneakyThrows;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.result.AqlEagerResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.model.RepoConfig;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.artifactory.addon.huggingfaceml.HuggingFaceLayoutConverter.HF_ENTITY_IS_MIGRATED;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class HuggingFaceLayoutConverterTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private AqlService aqlService;
    @Mock
    private SecurityService securityService;
    private MeterRegistry meterRegistry;
    @Captor
    private ArgumentCaptor<RepoConfig> repoConfigCaptor;

    private AutoCloseable mocks;
    private HuggingFaceLayoutConverter converter;

    @SneakyThrows
    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();
        converter = spy(new HuggingFaceLayoutConverter(repositoryService, aqlService,
                securityService, meterRegistry, false));
        when(securityService.callAsSystem(any())).thenAnswer(invocation -> {
            Callable<String> callable = invocation.getArgument(0);
            return callable.call();
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @SneakyThrows
    @Test
    void convertThrowsException() {
        doThrow(new Exception("Simulated exception")).when(securityService).callAsSystem(any());

        final String message = converter.convert("validRepo");
        assertEquals("Failed to convert paths for repo validRepo with dryRun false", message);

        verify(repositoryService, never()).move(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), any(), anyBoolean());
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void convertPaths(boolean dryRun) {
        String repoKey = "validRepo";
        final RepoConfig validRepoConfig = createValidRepoConfig(repoKey);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(validRepoConfig);
        List<String> paths = List.of(
                "models/name/revision/2024-09-12T13:25:52.272Z/.jfrog_huggingface_model_info.json",
                "models/name/revision/2024-09-12T13:25:52.272Z/internal/file.txt",
                "models/name/revision/2024-09-12T13:25:52.272Z/internal/leveltwo/file.txt",
                "models/name/revision/bad_path",
                "models/name/revision/.latest_huggingface_model_info.json",
                "models/org/name/revision/2024-09-12T13:25:52.272Z/.jfrog_huggingface_model_info.json",
                "models/org/name/revision/2024-09-12T13:25:52.272Z/internal/file.txt",
                "models/org/name/revision/2024-09-12T13:25:52.272Z/internal/leveltwo/file.txt",
                "models/org/name/revision/bad_path",
                "models/org/name/revision/.latest_huggingface_model_info.json",
                "datasets/name/revision/2024-09-12T13:25:52.272Z/.jfrog_huggingface_model_info.json",
                "datasets/name/revision/2024-09-12T13:25:52.272Z/internal/file.txt",
                "datasets/name/revision/2024-09-12T13:25:52.272Z/internal/leveltwo/file.txt",
                "datasets/name/revision/bad_path",
                "datasets/name/revision/.latest_huggingface_dataset_info.json"
        );
        final AqlEagerResult aqlResultWithValidPaths = createAqlResultWithValidPaths(paths);
        when(aqlService.executeQueryEager(any(AqlApiItem.class))).thenReturn(aqlResultWithValidPaths);

        converter = spy(new HuggingFaceLayoutConverter(repositoryService, aqlService,
                securityService, meterRegistry, dryRun));
        converter.convert(repoKey);
        final Properties properties = new PropertiesImpl();
        properties.put(HF_ENTITY_IS_MIGRATED, "true");

        // two bad paths should be skipped
        int times = dryRun ? 0 : paths.size() - 3;
        int leadFilesCount = dryRun ? 0 :
                paths.stream().filter(path -> HuggingFacePathUtil.getFileType(path) == FileType.LEAD_FILE).toList()
                        .size();
        verify(repositoryService, times(leadFilesCount)).move(any(), any(), eq(false), eq(true), eq(true), eq(true),
                eq(true),
                eq(properties), anyBoolean());
        // Without properties
        verify(repositoryService, times(times - leadFilesCount)).move(any(), any(), eq(false), eq(true), eq(true),
                eq(true), eq(true), eq(null), anyBoolean());
        if (!dryRun) {
            verify(converter, times(1)).createBackupForRepoContent(repoKey);
            verify(converter, times(1)).reportConversionToGrafana(any(HuggingFaceLayoutConvertResult.class));
            verify(repositoryService, times(1)).updateRepoConfigWithoutChecks(repoConfigCaptor.capture());
            assertEquals("true", repoConfigCaptor.getValue().getPackageTypeConfig().get(IS_ML_REPO_LAYOUT));
            assertEquals(3, meterRegistry.getMeters().size());
        } else {
            verify(converter, never()).createBackupForRepoContent(anyString());
            verify(converter, never()).reportConversionToGrafana(any(HuggingFaceLayoutConvertResult.class));
            verify(repositoryService, never()).updateRepoConfigWithoutChecks(any());
        }
    }

    @SneakyThrows
    @Test
    void getHuggingFaceFilesByRepoFromDB_withInvalidRepo_doesNotConvertPaths() {
        String repoKey = "invalidRepo";
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(null);

        converter.convert(repoKey);

        verify(repositoryService, never()).moveFlat(any(), anyString(), anyString(), any(), any(), anyBoolean(),
                anyBoolean(), anyInt());
        verify(converter, never()).createBackupForRepoContent(anyString());
        verify(repositoryService, never()).updateRepoConfigWithoutChecks(any());
    }

    @Test
    void isValidRepoForLayoutConvert_withValidRepo_returnsNull() {
        String repoKey = "validRepo";
        final RepoConfig validRepoConfig = createValidRepoConfig(repoKey);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(validRepoConfig);

        assertNull(converter.validateRepoForLayoutConvert(repoKey));
    }

    @Test
    void isValidReturnsFalseForAnAbsentRepo() {
        String repoKey = "invalidRepo";
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(null);

        final String result = converter.validateRepoForLayoutConvert(repoKey);
        assertEquals("Repository invalidRepo descriptor not found", result);
    }

    @Test
    void isValidReturnsFalseForNonHFRepo() {
        String repoKey = "nonHFRepo";
        final RepoConfig nonHFRepoConfig = createValidRepoConfig(repoKey, RepoType.NuGet, false);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(nonHFRepoConfig);

        final String result = converter.validateRepoForLayoutConvert(repoKey);
        assertEquals("Repository nonHFRepo is not HuggingFaceML type", result);
    }

    @Test
    void isValidReturnsFalseForHFRepoWithMLRepoLayout() {
        String repoKey = "hfRepo";
        final RepoConfig huggingFaceRepoConfig = createValidRepoConfig(repoKey, RepoType.HuggingFaceML, true);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(huggingFaceRepoConfig);

        final String result = converter.validateRepoForLayoutConvert(repoKey);
        assertEquals("Repository hfRepo is already using new layout", result);
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(strings = {"repoWithChildren", "repoWithoutChildren"})
    void createBackupForRepoContentForLocal(String repoKeyForConversion) {
        RepoPath sourcePath = RepoPathFactory.create(repoKeyForConversion);
        RepoPath backupPath = RepoPathFactory.create(repoKeyForConversion, HuggingFaceLayoutConverter.BACKUP_DIR);

        when(repositoryService.hasChildren(sourcePath)).thenReturn("repoWithChildren".equals(repoKeyForConversion));

        converter.createBackupForRepoContent(repoKeyForConversion);

        if ("repoWithChildren".equals(repoKeyForConversion)) {
            verify(repositoryService, times(1)).copy(sourcePath, backupPath);
        } else {
            verify(repositoryService, never()).copy(any(), any());
            verify(repositoryService, never()).copyToCache(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(),
                    anyBoolean(), any(), anyBoolean());
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(strings = {"repoWithChildren-cache", "repoWithoutChildren"})
    void createBackupForRepoContentForRemoteCache(String repoKeyForConversion) {
        RepoPath sourcePath = RepoPathFactory.create(repoKeyForConversion);
        RepoPath backupPath = RepoPathFactory.create(repoKeyForConversion, HuggingFaceLayoutConverter.BACKUP_DIR);

        when(repositoryService.hasChildren(sourcePath)).thenReturn(
                "repoWithChildren-cache".equals(repoKeyForConversion));

        converter.createBackupForRepoContent(repoKeyForConversion);

        if ("repoWithChildren-cache".equals(repoKeyForConversion)) {
            verify(repositoryService, times(1)).copyToCache(eq(sourcePath), eq(backupPath), eq(false), eq(true),
                    eq(true), eq(false), eq(null), eq(false));
        } else {
            verify(repositoryService, never()).copy(any(), any());
            verify(repositoryService, never()).copyToCache(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(),
                    anyBoolean(), any(), anyBoolean());
        }
    }

    private RepoConfig createValidRepoConfig(String repoKey) {
        return createValidRepoConfig(repoKey, RepoType.HuggingFaceML, false);
    }

    private RepoConfig createValidRepoConfig(String repoKey, RepoType repoType, boolean isMlRepoLayout) {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(repoConfig.getPackageType()).thenReturn(repoType);
        Map<String, String> packageTypeConfig = new HashMap<>();
        packageTypeConfig.put(IS_ML_REPO_LAYOUT, String.valueOf(isMlRepoLayout));
        when(repoConfig.getPackageTypeConfig()).thenReturn(packageTypeConfig);
        return repoConfig;
    }

    private AqlEagerResult createAqlResultWithValidPaths(List<String> paths) {
        AqlEagerResult result = mock(AqlEagerResult.class);
        List<AqlItem> items = new ArrayList<>();
        for (String path : paths) {
            String name = path.substring(path.lastIndexOf('/') + 1);
            AqlItem item = mock(AqlItem.class);
            when(item.getRepo()).thenReturn("validRepo");
            when(item.getName()).thenReturn(name);
            when(item.getPath()).thenReturn(path.substring(0, path.lastIndexOf('/')));
            items.add(item);
        }
        when(result.getResults()).thenReturn(items);
        return result;
    }
}
