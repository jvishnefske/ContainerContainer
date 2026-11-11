package org.artifactory.util;

import lombok.experimental.UtilityClass;
import org.artifactory.model.DiffFunctionsImpl;
import org.artifactory.model.RepoConfig;
import org.artifactory.sensitive.fields.SensitiveDiffUtil;
import org.jfrog.common.config.diff.DiffFunctions;
import org.jfrog.common.config.diff.DiffUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Repo-config-specific entry point for masked field-level diffs. Binds the generic
 * {@link SensitiveDiffUtil} to the repo config object graph by supplying the annotation-processor-generated
 * {@link DiffFunctionsImpl} registry, so callers in the repo-config layer do not need to know about the
 * {@link DiffFunctions} plumbing.
 * <p>
 * <b>Audit contract — {@code packageTypeConfig}:</b> each changed key is emitted as
 * {@code packageTypeConfig»<key>} with scalar {@code String} old/new values. Added or removed keys use
 * {@code null} on the missing side; scalar map entries do not use {@code $$new}/{@code $$deleted} markers.
 *
 * @author Revital Abuhatzira
 */
@UtilityClass
public class RepoConfigDiffUtil {

    private static final DiffFunctions DIFF_FUNCTIONS = new DiffFunctionsImpl();
    /**
     * {@code packageTypeConfig} is {@code @DiffIgnore}'d on every repo config so the diff engine never emits it as an
     * atomic blob; its per-key entries are appended here under this prefix instead.
     */
    private static final String PACKAGE_TYPE_CONFIG_PATH = "packageTypeConfig";

    /**
     * Field-level diff between two repo configs with sensitive leaves masked, plus per-key
     * {@code packageTypeConfig»<key>} entries (see {@link #appendPackageTypeConfigDiff}).
     *
     * @see SensitiveDiffUtil#maskedDiff(DiffFunctions, Object, Object)
     */
    public static List<DiffEntry> maskedDiff(RepoConfig oldConfig, RepoConfig newConfig) {
        // Same null/type guard as SensitiveDiffUtil.maskedDiff; duplicated here so appendPackageTypeConfigDiff
        // is skipped when configs cannot be compared (SensitiveDiffUtil does not append packageTypeConfig entries).
        if (oldConfig == null || newConfig == null || oldConfig.getClass() != newConfig.getClass()) {
            return List.of();
        }
        List<DiffEntry> diffs = new ArrayList<>(SensitiveDiffUtil.maskedDiff(DIFF_FUNCTIONS, oldConfig, newConfig));
        appendPackageTypeConfigDiff(diffs, oldConfig, newConfig);
        diffs.sort(Comparator.comparing(DiffEntry::getAttributeName));
        return List.copyOf(diffs);
    }

    /**
     * Appends per-key {@code packageTypeConfig»<key>} entries by diffing the two {@code String} maps directly.
     * Added or removed keys are plain leaves with {@code null} on the missing side — no {@code $$new}/
     * {@code $$deleted} markers for scalar map values.
     */
    private static void appendPackageTypeConfigDiff(List<DiffEntry> diffs, RepoConfig oldConfig, RepoConfig newConfig) {
        // packageTypeConfig keys/values are non-sensitive package settings (e.g. blockPushingSchema1), so these
        // entries are intentionally added raw without sensitive masking.
        diffs.addAll(diffPackageTypeConfigMaps(oldConfig.getPackageTypeConfig(), newConfig.getPackageTypeConfig()));
    }

    private static List<DiffEntry> diffPackageTypeConfigMaps(Map<String, String> oldMap, Map<String, String> newMap) {
        Map<String, String> oldEntries = nullToEmptyMap(oldMap);
        Map<String, String> newEntries = nullToEmptyMap(newMap);
        Set<String> allKeys = new HashSet<>(oldEntries.keySet());
        allKeys.addAll(newEntries.keySet());
        List<DiffEntry> entries = new ArrayList<>();
        for (String key : allKeys) {
            boolean inOld = oldEntries.containsKey(key);
            boolean inNew = newEntries.containsKey(key);
            String oldVal = inOld ? oldEntries.get(key) : null;
            String newVal = inNew ? newEntries.get(key) : null;
            if (inOld == inNew && Objects.equals(oldVal, newVal)) {
                continue;
            }
            entries.add(new DiffEntry(PACKAGE_TYPE_CONFIG_PATH + DiffUtils.DELIMITER + key, oldVal, newVal));
        }
        return entries;
    }

    private static Map<String, String> nullToEmptyMap(Map<String, String> map) {
        return map != null ? map : Collections.emptyMap();
    }
}
