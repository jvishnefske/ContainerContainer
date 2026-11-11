package org.artifactory.util;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.RepoConfigCopyFactory;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.remote.CustomHttpHeader;
import org.artifactory.sensitive.CustomHttpHeadersSensitiveHandler;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.config.diff.DiffUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RepoConfigDiffUtil} (the repo-config binding over the generic {@code SensitiveDiffUtil}): static
 * {@code @Sensitive} masking, non-sensitive pass-through, and per-side {@code @DynamicSensitive} masking (custom HTTP
 * headers, including sensitivity that toggles between sides).
 */
class RepoConfigDiffUtilTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String D = DiffUtils.DELIMITER;
    private static final String PASSWORD_PATH = "repoTypeConfig" + D + "password";
    private static final String URL_PATH = "repoTypeConfig" + D + "url";
    private static final String HEADER_VALUE_PATH =
            "repoTypeConfig" + D + "customHttpHeaders" + D + "authorization" + D + "value";
    private static final String PACKAGE_TYPE_CONFIG_PREFIX = "packageTypeConfig";

    @BeforeEach
    void setUp() {
        bindArtifactoryContextWithHeaderHandler();
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void masksSensitiveStringFieldOnBothSides() {
        RemoteRepoConfig oldConfig = remote();
        oldConfig.getRepoTypeConfig().setPassword("oldSecret");
        RemoteRepoConfig newConfig = copyOf(oldConfig);
        newConfig.getRepoTypeConfig().setPassword("newSecret");

        DiffEntry password = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig), PASSWORD_PATH);
        assertEquals("***", password.getOldValue());
        assertEquals("***", password.getNewValue());
    }

    @Test
    void leavesNonSensitiveFieldsInClearText() {
        RemoteRepoConfig oldConfig = remote();
        oldConfig.getRepoTypeConfig().setUrl("http://old");
        RemoteRepoConfig newConfig = copyOf(oldConfig);
        newConfig.getRepoTypeConfig().setUrl("http://new");

        DiffEntry url = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig), URL_PATH);
        assertEquals("http://old", url.getOldValue());
        assertEquals("http://new", url.getNewValue());
    }

    @Test
    void masksSensitiveHeaderValueOnBothSides() {
        RemoteRepoConfig oldConfig = remote();
        oldConfig.getRepoTypeConfig().setCustomHttpHeaders(headers("Authorization", "oldToken", true));
        RemoteRepoConfig newConfig = copyOf(oldConfig);
        newConfig.getRepoTypeConfig().setCustomHttpHeaders(headers("Authorization", "newToken", true));

        DiffEntry value = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig), HEADER_VALUE_PATH);
        assertEquals("***", value.getOldValue());
        assertEquals("***", value.getNewValue());
    }

    @Test
    void masksOnlyTheSensitiveSideWhenHeaderSensitivityToggles() {
        RemoteRepoConfig oldConfig = remote();
        oldConfig.getRepoTypeConfig().setCustomHttpHeaders(headers("Authorization", "oldToken", true));
        RemoteRepoConfig newConfig = copyOf(oldConfig);
        newConfig.getRepoTypeConfig().setCustomHttpHeaders(headers("Authorization", "newToken", false));

        DiffEntry value = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig), HEADER_VALUE_PATH);
        assertEquals("***", value.getOldValue());
        assertEquals("newToken", value.getNewValue());
    }

    @Test
    void fullyMasksNewlyAddedHeaderWithSensitiveDescendant() {
        RemoteRepoConfig oldConfig = remote();
        oldConfig.getRepoTypeConfig().setCustomHttpHeaders(headers("X-Common", "common", false));
        RemoteRepoConfig newConfig = copyOf(oldConfig);
        newConfig.getRepoTypeConfig().setCustomHttpHeaders(new ArrayList<>(List.of(
                new CustomHttpHeader("X-Common", "common", false),
                new CustomHttpHeader("Authorization", "secret", true))));

        DiffEntry added = entryEndingWith(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig),
                "customHttpHeaders" + D + "authorization" + D + DiffUtils.NEW_MARKER);
        assertNull(added.getOldValue());
        assertEquals("***", added.getNewValue());
    }

    @Test
    void fullyMasksDeletedHeaderWithSensitiveDescendant() {
        RemoteRepoConfig oldConfig = remote();
        oldConfig.getRepoTypeConfig().setCustomHttpHeaders(new ArrayList<>(List.of(
                new CustomHttpHeader("X-Common", "common", false),
                new CustomHttpHeader("Authorization", "secret", true))));
        RemoteRepoConfig newConfig = copyOf(oldConfig);
        newConfig.getRepoTypeConfig().setCustomHttpHeaders(headers("X-Common", "common", false));

        DiffEntry deleted = entryEndingWith(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig),
                "customHttpHeaders" + D + "authorization" + D + DiffUtils.DELETED_MARKER);
        assertEquals("***", deleted.getOldValue());
        assertNull(deleted.getNewValue());
    }

    @Test
    void leavesNonSensitiveNewlyAddedHeaderInClearText() {
        RemoteRepoConfig oldConfig = remote();
        oldConfig.getRepoTypeConfig().setCustomHttpHeaders(headers("X-Common", "common", false));
        RemoteRepoConfig newConfig = copyOf(oldConfig);
        newConfig.getRepoTypeConfig().setCustomHttpHeaders(new ArrayList<>(List.of(
                new CustomHttpHeader("X-Common", "common", false),
                new CustomHttpHeader("X-Extra", "plain", false))));

        DiffEntry added = entryEndingWith(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig),
                "customHttpHeaders" + D + "x-extra" + D + DiffUtils.NEW_MARKER);
        assertNull(added.getOldValue());
        CustomHttpHeader addedHeader = (CustomHttpHeader) added.getNewValue();
        assertEquals("plain", addedHeader.getValue());
    }

    @Test
    void ignoresBaseConfigModelVersion() {
        RemoteRepoConfig oldConfig = remote();
        oldConfig.getBaseConfig().setModelVersion(1L);
        RemoteRepoConfig newConfig = copyOf(oldConfig);
        newConfig.getBaseConfig().setModelVersion(2L);

        assertTrue(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig).isEmpty());
    }

    @Test
    void ignoresRepoBookkeepingFields() {
        LocalRepoConfig oldConfig = LocalRepoConfig.create("repo", RepoType.Generic);
        LocalRepoConfig newConfig = copyOf(oldConfig);
        oldConfig.setModification(1_000L);
        oldConfig.setCreation(2_000L);
        newConfig.setModification(9_999L);
        newConfig.setCreation(8_888L);

        assertTrue(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig).isEmpty());
    }

    @Test
    void repositoryRefsDifferentOrderProducesAtomicDiff() {
        VirtualRepoConfig oldConfig = virtual();
        oldConfig.getRepoTypeConfig().setRepositoryRefs(List.of("repo-a", "repo-b"));
        VirtualRepoConfig newConfig = copyOf(oldConfig);
        newConfig.getRepoTypeConfig().setRepositoryRefs(List.of("repo-b", "repo-a"));

        DiffEntry diff = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig),
                "repoTypeConfig" + D + "repositoryRefs");
        assertEquals(List.of("repo-a", "repo-b"), diff.getOldValue());
        assertEquals(List.of("repo-b", "repo-a"), diff.getNewValue());
    }

    @Test
    void packageTypeConfigChangedValueProducesPerKeyDiff() {
        LocalRepoConfig oldConfig = localWithPackageTypeConfig(map("myKey", "oldValue"));
        LocalRepoConfig newConfig = copyOf(oldConfig);
        replacePackageTypeConfig(newConfig, map("myKey", "newValue"));

        String path = PACKAGE_TYPE_CONFIG_PREFIX + D + "myKey";
        DiffEntry diff = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig), path);
        assertEquals("oldValue", diff.getOldValue());
        assertEquals("newValue", diff.getNewValue());
    }

    @ParameterizedTest
    @MethodSource("repoConfigsWithPackageTypeConfig")
    void packageTypeConfigChangedValueProducesPerKeyDiffForAllRepoTypes(RepoConfig oldConfig) {
        RepoConfig newConfig = RepoConfigCopyFactory.copy(oldConfig);
        replacePackageTypeConfig(newConfig, map("myKey", "newValue"));

        String path = PACKAGE_TYPE_CONFIG_PREFIX + D + "myKey";
        DiffEntry diff = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig), path);
        assertEquals("oldValue", diff.getOldValue());
        assertEquals("newValue", diff.getNewValue());
    }

    @Test
    void packageTypeConfigChangedValueProducesPerKeyDiffForVirtualRepo() {
        VirtualRepoConfig oldConfig =
                repoWithPackageTypeConfig(VirtualRepoConfig.create("virtual-repo", RepoType.Generic));
        VirtualRepoConfig newConfig = copyOf(oldConfig);
        replacePackageTypeConfig(newConfig, map("myKey", "newValue"));

        String path = PACKAGE_TYPE_CONFIG_PREFIX + D + "myKey";
        DiffEntry diff = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig), path);
        assertEquals("oldValue", diff.getOldValue());
        assertEquals("newValue", diff.getNewValue());
    }

    @Test
    void packageTypeConfigChangedValueProducesPerKeyDiffForFederatedRepo() {
        FederatedRepoConfig oldConfig =
                repoWithPackageTypeConfig(FederatedRepoConfig.create("federated-repo", RepoType.Generic));
        FederatedRepoConfig newConfig = (FederatedRepoConfig) RepoConfigCopyFactory.copy(oldConfig);
        replacePackageTypeConfig(newConfig, map("myKey", "newValue"));

        String path = PACKAGE_TYPE_CONFIG_PREFIX + D + "myKey";
        DiffEntry diff = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig), path);
        assertEquals("oldValue", diff.getOldValue());
        assertEquals("newValue", diff.getNewValue());
    }

    @Test
    void packageTypeConfigAddedEntryProducesPerKeyDiff() {
        LocalRepoConfig oldConfig = localWithPackageTypeConfig(Map.of());
        LocalRepoConfig newConfig = copyOf(oldConfig);
        replacePackageTypeConfig(newConfig, map("newKey", "newVal"));

        DiffEntry added = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig),
                PACKAGE_TYPE_CONFIG_PREFIX + D + "newKey");
        assertNull(added.getOldValue());
        assertEquals("newVal", added.getNewValue());
    }

    @Test
    void packageTypeConfigRemovedEntryProducesPerKeyDiff() {
        LocalRepoConfig oldConfig = localWithPackageTypeConfig(map("removedKey", "val"));
        LocalRepoConfig newConfig = copyOf(oldConfig);
        replacePackageTypeConfig(newConfig, Map.of());

        DiffEntry removed = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig),
                PACKAGE_TYPE_CONFIG_PREFIX + D + "removedKey");
        assertEquals("val", removed.getOldValue());
        assertNull(removed.getNewValue());
    }

    @Test
    void packageTypeConfigAddedNullValuedKeyProducesPerKeyDiff() {
        LocalRepoConfig oldConfig = localWithPackageTypeConfig(Map.of());
        LocalRepoConfig newConfig = copyOf(oldConfig);
        replacePackageTypeConfig(newConfig, mapWithNullValue("nullableKey"));

        DiffEntry added = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig),
                PACKAGE_TYPE_CONFIG_PREFIX + D + "nullableKey");
        assertNull(added.getOldValue());
        assertNull(added.getNewValue());
    }

    @Test
    void packageTypeConfigRemovedNullValuedKeyProducesPerKeyDiff() {
        LocalRepoConfig oldConfig = localWithPackageTypeConfig(mapWithNullValue("nullableKey"));
        LocalRepoConfig newConfig = copyOf(oldConfig);
        replacePackageTypeConfig(newConfig, Map.of());

        DiffEntry removed = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig),
                PACKAGE_TYPE_CONFIG_PREFIX + D + "nullableKey");
        assertNull(removed.getOldValue());
        assertNull(removed.getNewValue());
    }

    @Test
    void packageTypeConfigSameValuesProducesNoDiff() {
        LocalRepoConfig oldConfig = localWithPackageTypeConfig(map("stableKey", "stableVal"));
        LocalRepoConfig newConfig = copyOf(oldConfig);

        List<DiffEntry> diffs = RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig);
        assertTrue(diffs.stream().noneMatch(diff -> diff.getAttributeName().startsWith(PACKAGE_TYPE_CONFIG_PREFIX)),
                "Expected no packageTypeConfig diff for identical configs but got: "
                        + diffs.stream().map(DiffEntry::getAttributeName).toList());
    }

    @ParameterizedTest
    @MethodSource("allRepoConfigTypes")
    @SuppressWarnings("java:S3011")
    void nullPackageTypeConfigDoesNotThrow(RepoConfig template) throws Exception {
        RepoConfig oldConfig = RepoConfigCopyFactory.copy(template);
        setPackageTypeConfigNull(oldConfig);
        RepoConfig newConfig = RepoConfigCopyFactory.copy(template);
        replacePackageTypeConfig(newConfig, Map.of());

        assertTrue(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig).stream()
                        .noneMatch(diff -> diff.getAttributeName().startsWith(PACKAGE_TYPE_CONFIG_PREFIX)),
                "A null packageTypeConfig with an empty new map must produce no diff entries, not throw");
    }

    @Test
    @SuppressWarnings("java:S3011")
    void nullPackageTypeConfigWithNewEntriesProducesPerKeyDiff() throws Exception {
        LocalRepoConfig oldConfig = LocalRepoConfig.create("repo", RepoType.Generic);
        setPackageTypeConfigNull(oldConfig);
        LocalRepoConfig newConfig = localWithPackageTypeConfig(map("addedKey", "addedVal"));

        DiffEntry added = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig),
                PACKAGE_TYPE_CONFIG_PREFIX + D + "addedKey");
        assertNull(added.getOldValue());
        assertEquals("addedVal", added.getNewValue());
    }

    @Test
    void packageTypeConfigNullValueChangedToStringProducesPerKeyDiff() {
        LocalRepoConfig oldConfig = localWithPackageTypeConfig(mapWithNullValue("nullableKey"));
        LocalRepoConfig newConfig = copyOf(oldConfig);
        replacePackageTypeConfig(newConfig, map("nullableKey", "set"));

        DiffEntry diff = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig),
                PACKAGE_TYPE_CONFIG_PREFIX + D + "nullableKey");
        assertNull(diff.getOldValue());
        assertEquals("set", diff.getNewValue());
    }

    @Test
    void packageTypeConfigStringValueChangedToNullProducesPerKeyDiff() {
        LocalRepoConfig oldConfig = localWithPackageTypeConfig(map("nullableKey", "set"));
        LocalRepoConfig newConfig = copyOf(oldConfig);
        replacePackageTypeConfig(newConfig, mapWithNullValue("nullableKey"));

        DiffEntry diff = entry(RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig),
                PACKAGE_TYPE_CONFIG_PREFIX + D + "nullableKey");
        assertEquals("set", diff.getOldValue());
        assertNull(diff.getNewValue());
    }

    @Test
    void packageTypeConfigMultipleChangesAreSortedAndMergedWithOtherDiffs() {
        RemoteRepoConfig oldConfig = RemoteRepoConfig.create("repo", RepoType.Generic);
        Map<String, String> oldPackageTypeConfig = new HashMap<>();
        oldPackageTypeConfig.put("zeta", "z-old");
        oldPackageTypeConfig.put("alpha", "a-old");
        replacePackageTypeConfig(oldConfig, oldPackageTypeConfig);
        oldConfig.getRepoTypeConfig().setUrl("http://old");
        RemoteRepoConfig newConfig = (RemoteRepoConfig) RepoConfigCopyFactory.copy(oldConfig);
        Map<String, String> newPackageTypeConfig = new HashMap<>();
        newPackageTypeConfig.put("zeta", "z-new");
        newPackageTypeConfig.put("alpha", "a-new");
        replacePackageTypeConfig(newConfig, newPackageTypeConfig);
        newConfig.getRepoTypeConfig().setUrl("http://new");

        List<DiffEntry> diffs = RepoConfigDiffUtil.maskedDiff(oldConfig, newConfig);
        List<String> paths = diffs.stream().map(DiffEntry::getAttributeName).toList();

        // per-key packageTypeConfig entries must appear in sorted key order, interleaved correctly with other fields
        assertTrue(paths.indexOf(PACKAGE_TYPE_CONFIG_PREFIX + D + "alpha")
                        < paths.indexOf(PACKAGE_TYPE_CONFIG_PREFIX + D + "zeta"),
                "'alpha' must sort before 'zeta' in: " + paths);
        // packageTypeConfig»... sorts before repoTypeConfig»url alphabetically
        assertTrue(paths.indexOf(PACKAGE_TYPE_CONFIG_PREFIX + D + "zeta")
                        < paths.indexOf(URL_PATH),
                "packageTypeConfig entries must sort before repoTypeConfig»url in: " + paths);
        assertEquals("a-old", entry(diffs, PACKAGE_TYPE_CONFIG_PREFIX + D + "alpha").getOldValue());
        assertEquals("a-new", entry(diffs, PACKAGE_TYPE_CONFIG_PREFIX + D + "alpha").getNewValue());
        assertEquals("z-old", entry(diffs, PACKAGE_TYPE_CONFIG_PREFIX + D + "zeta").getOldValue());
        assertEquals("z-new", entry(diffs, PACKAGE_TYPE_CONFIG_PREFIX + D + "zeta").getNewValue());
    }

    @Test
    void returnsEmptyWhenNoFieldsChanged() {
        RemoteRepoConfig config = remote();
        config.getRepoTypeConfig().setUrl("http://same");
        assertTrue(RepoConfigDiffUtil.maskedDiff(config, copyOf(config)).isEmpty());
    }

    @Test
    void returnsEmptyForNullOrDifferentTypes() {
        RemoteRepoConfig remote = remote();
        assertTrue(RepoConfigDiffUtil.maskedDiff(null, remote).isEmpty());
        assertTrue(RepoConfigDiffUtil.maskedDiff(remote, null).isEmpty());

        LocalRepoConfig local = LocalRepoConfig.create("repo", RepoType.Generic);
        replacePackageTypeConfig(local, map("localKey", "localVal"));
        RemoteRepoConfig remoteWithPackageTypeConfig = RemoteRepoConfig.create("repo", RepoType.Generic);
        replacePackageTypeConfig(remoteWithPackageTypeConfig, map("remoteKey", "remoteVal"));
        assertTrue(RepoConfigDiffUtil.maskedDiff(local, remoteWithPackageTypeConfig).isEmpty());
    }

    private static RemoteRepoConfig remote() {
        return RemoteRepoConfig.create("repo", RepoType.Generic);
    }

    private static RemoteRepoConfig copyOf(RemoteRepoConfig config) {
        return (RemoteRepoConfig) RepoConfigCopyFactory.copy(config);
    }

    private static LocalRepoConfig copyOf(LocalRepoConfig config) {
        return (LocalRepoConfig) RepoConfigCopyFactory.copy(config);
    }

    private static VirtualRepoConfig virtual() {
        return VirtualRepoConfig.create("repo", RepoType.Generic);
    }

    private static VirtualRepoConfig copyOf(VirtualRepoConfig config) {
        return (VirtualRepoConfig) RepoConfigCopyFactory.copy(config);
    }

    private static LocalRepoConfig localWithPackageTypeConfig(Map<String, String> packageTypeConfig) {
        LocalRepoConfig config = LocalRepoConfig.create("repo", RepoType.Generic);
        replacePackageTypeConfig(config, packageTypeConfig);
        return config;
    }

    private static Stream<Arguments> allRepoConfigTypes() {
        return Stream.of(
                Arguments.of(LocalRepoConfig.create("local-repo", RepoType.Generic)),
                Arguments.of(RemoteRepoConfig.create("remote-repo", RepoType.Generic)),
                Arguments.of(VirtualRepoConfig.create("virtual-repo", RepoType.Generic)),
                Arguments.of(FederatedRepoConfig.create("federated-repo", RepoType.Generic))
        );
    }

    private static Stream<Arguments> repoConfigsWithPackageTypeConfig() {
        return Stream.of(
                Arguments.of(repoWithPackageTypeConfig(LocalRepoConfig.create("local-repo", RepoType.Generic))),
                Arguments.of(repoWithPackageTypeConfig(RemoteRepoConfig.create("remote-repo", RepoType.Generic))),
                Arguments.of(repoWithPackageTypeConfig(VirtualRepoConfig.create("virtual-repo", RepoType.Generic))),
                Arguments.of(repoWithPackageTypeConfig(FederatedRepoConfig.create("federated-repo", RepoType.Generic)))
        );
    }

    private static <T extends RepoConfig> T repoWithPackageTypeConfig(T config) {
        replacePackageTypeConfig(config, map("myKey", "oldValue"));
        return config;
    }

    private static void replacePackageTypeConfig(RepoConfig config, Map<String, String> packageTypeConfig) {
        config.getPackageTypeConfig().clear();
        config.getPackageTypeConfig().putAll(packageTypeConfig);
    }

    private static Map<String, String> map(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private static Map<String, String> mapWithNullValue(String key) {
        Map<String, String> map = new HashMap<>();
        map.put(key, null);
        return map;
    }

    @SuppressWarnings("java:S3011")
    private static void setPackageTypeConfigNull(RepoConfig config) throws Exception {
        java.lang.reflect.Field field = config.getClass().getDeclaredField("packageTypeConfig");
        field.setAccessible(true);
        field.set(config, null);
    }

    private static List<CustomHttpHeader> headers(String name, String value, boolean sensitive) {
        return new ArrayList<>(List.of(new CustomHttpHeader(name, value, sensitive)));
    }

    private static DiffEntry entry(List<DiffEntry> diffs, String attributeName) {
        return diffs.stream()
                .filter(diff -> attributeName.equals(diff.getAttributeName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No diff entry for '" + attributeName + "' in " + diffs.stream()
                        .map(DiffEntry::getAttributeName).toList()));
    }

    private static DiffEntry entryEndingWith(List<DiffEntry> diffs, String attributeNameSuffix) {
        return diffs.stream()
                .filter(diff -> diff.getAttributeName().endsWith(attributeNameSuffix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No diff entry ending with '" + attributeNameSuffix + "' in "
                        + diffs.stream().map(DiffEntry::getAttributeName).toList()));
    }

    @SuppressWarnings("unchecked")
    private static void bindArtifactoryContextWithHeaderHandler() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type == CustomHttpHeadersSensitiveHandler.class) {
                CustomHttpHeadersSensitiveHandler handler = new CustomHttpHeadersSensitiveHandler();
                handler.init();
                return handler;
            }
            throw new IllegalArgumentException("Unexpected handler type for test context: " + type.getName());
        });
        ArtifactoryContextThreadBinder.bind(context);
    }
}
