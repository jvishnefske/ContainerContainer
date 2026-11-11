package org.artifactory.repo.interceptor;

import com.google.common.collect.Lists;
import org.artifactory.api.util.Idempotent;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.util.CollectionUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.repo.RepoConfigChange.ConfigAction;

public record RepoConfigChanges(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges, boolean isImport) {

    public RepoConfigChanges(RepoConfigChange repoConfigChange) {
        this(new EnumMap<>(RepoDetailsType.class), false);
        add(repoConfigChange);
    }

    public RepoConfigChanges(Collection<RepoConfigChange> repoConfigChanges) {
        this(new EnumMap<>(RepoDetailsType.class), false);
        repoConfigChanges.forEach(this::add);
    }

    public RepoConfigChanges(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges) {
        this(repoConfigChanges, false);
    }

    public static RepoConfigChanges of(Collection<RepoConfigChange> repoConfigChanges) {
        return new RepoConfigChanges(repoConfigChanges);
    }

    public static RepoConfigChanges of(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges) {
        return new RepoConfigChanges(repoConfigChanges);
    }

    public List<RepoConfigChange> filterBy(ConfigAction... actions) {
        if (actions == null || actions.length == 0) {
            return List.of();
        }
        Set<ConfigAction> actionSet = Arrays.stream(actions)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (actionSet.isEmpty()) {
            return List.of();
        }
        return repoConfigChanges.values().stream()
                .flatMap(List::stream)
                .filter(change -> actionSet.contains(change.getConfigAction()))
                .toList();
    }

    public List<RepoConfigChange> filterBy(RepoDetailsType repoType) {
        return repoConfigChanges.getOrDefault(repoType, Lists.newArrayList());
    }

    public List<RepoConfigChange> filterBy(ConfigAction action, RepoDetailsType repoType) {
        return filterBy(repoType).stream()
                .filter(repoConfigChange -> repoConfigChange.getConfigAction() == action)
                .toList();
    }

    /**
     * Returns changes for {@code repoType} whose {@link ConfigAction} is one of {@code actions}.
     * If {@code actions} is null or empty, returns all changes for that repo type
     * (same as {@link #filterBy(RepoDetailsType)}).
     */
    public List<RepoConfigChange> filterBy(RepoDetailsType repoType, ConfigAction... actions) {
        List<RepoConfigChange> byType = filterBy(repoType);
        if (actions == null || actions.length == 0) {
            return byType;
        }
        Set<ConfigAction> actionSet = Arrays.stream(actions)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (actionSet.isEmpty()) {
            return List.of();
        }
        return byType.stream()
                .filter(change -> actionSet.contains(change.getConfigAction()))
                .toList();
    }

    public List<RepoConfigChange> filterBy(ConfigAction action, RepoDetailsType repoType, RepoType packageType) {
        return filterBy(repoType).stream()
                .filter(repoConfigChange -> repoConfigChange.getConfigAction() == action)
                .filter(repoConfigChange -> repoConfigChange.getPackageType() == packageType)
                .toList();
    }

    public Map<RepoDetailsType, List<RepoConfigChange>> getMappedByType() {
        return repoConfigChanges;
    }

    public List<RepoConfigChange> getAll() {
        return repoConfigChanges.values().stream().flatMap(List::stream).toList();
    }

    public void add(RepoConfigChange repoConfigChange) {
        repoConfigChanges.putIfAbsent(repoConfigChange.getRepoType(), Lists.newArrayList());
        repoConfigChanges.get(repoConfigChange.getRepoType()).add(repoConfigChange);
    }

    @Idempotent
    public void remove(RepoConfigChange repoConfigChange) {
        List<RepoConfigChange> changesByRepoType = repoConfigChanges.get(repoConfigChange.getRepoType());
        if (CollectionUtils.notNullOrEmpty(changesByRepoType)) {
            changesByRepoType.remove(repoConfigChange);
        }
    }
}