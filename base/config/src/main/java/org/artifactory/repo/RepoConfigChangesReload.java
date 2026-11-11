package org.artifactory.repo;

import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.artifactory.repo.RepoConfigChange.ConfigAction;

public record RepoConfigChangesReload(Map<RepoDetailsType, List<RepoConfigChangeReload>> mappedByRepoType) {

    public RepoConfigChangesReload(RepoConfigChangeReload repoConfigChangeReload) {
        this(Set.of(repoConfigChangeReload));
    }

    public RepoConfigChangesReload(Collection<RepoConfigChangeReload> repoConfigReloads) {
        this(new EnumMap<>(RepoDetailsType.class));
        repoConfigReloads.forEach(this::add);
    }

    public static RepoConfigChangesReload fromChanges(
            Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChangesByType) {
        Map<RepoDetailsType, List<RepoConfigChangeReload>> repoConfigReloadsByType =
                new EnumMap<>(RepoDetailsType.class);
        repoConfigChangesByType.forEach((type, repoConfigChanges) -> repoConfigReloadsByType.put(type,
                repoConfigChanges.stream().map(RepoConfigChangeReload::new).toList()));
        return new RepoConfigChangesReload(repoConfigReloadsByType);
    }

    public List<RepoConfigChangeReload> filterBy(RepoDetailsType repoType) {
        return mappedByRepoType.getOrDefault(repoType, Lists.newArrayList());
    }

    public List<RepoConfigChangeReload> filterBy(RepoDetailsType repoType, ConfigAction action) {
        return filterBy(repoType, Set.of(action));
    }

    public List<RepoConfigChangeReload> filterBy(RepoDetailsType repoType, Set<ConfigAction> actions) {
        return mappedByRepoType.getOrDefault(repoType, Lists.newArrayList())
                .stream()
                .filter(repoConfigReload -> actions.contains(repoConfigReload.getConfigAction()))
                .toList();
    }

    public List<RepoConfigChangeReload> filterBy(Set<RepoDetailsType> repoTypes, ConfigAction action) {
        return filterBy(repoTypes, Set.of(action));
    }

    public List<RepoConfigChangeReload> filterBy(Set<RepoDetailsType> repoTypes, Set<ConfigAction> actions) {
        return mappedByRepoType.keySet().stream()
                .filter(repoTypes::contains)
                .flatMap(repoType -> filterBy(repoType, actions).stream())
                .toList();
    }

    public Set<RepoConfigChangeReload> filterBy(ConfigAction configAction) {
        return mappedByRepoType.values().stream()
                .flatMap(List::stream)
                .filter(repoConfigChangeReload -> repoConfigChangeReload.getConfigAction() == configAction)
                .collect(Collectors.toSet());
    }

    public List<RepoConfigChangeReload> getAll() {
        return mappedByRepoType.values().stream().flatMap(List::stream).toList();
    }

    public void add(RepoConfigChangeReload repoConfigReload) {
        mappedByRepoType.putIfAbsent(repoConfigReload.getRepoType(), Lists.newArrayList());
        mappedByRepoType.get(repoConfigReload.getRepoType()).add(repoConfigReload);
    }

    public Map<RepoDetailsType, List<RepoConfigReload>> toRepoConfigReloadMap() {
        Map<RepoDetailsType, List<RepoConfigReload>> repoConfigReloadMap = new EnumMap<>(RepoDetailsType.class);
        mappedByRepoType.forEach((repoType, repoConfigChangeReloads) -> {
            List<RepoConfigReload> repoConfigReloads = repoConfigChangeReloads.stream()
                    .map(RepoConfigChangeReload::toRepoConfigReload)
                    .toList();
            repoConfigReloadMap.put(repoType, repoConfigReloads);
        });
        return repoConfigReloadMap;
    }
}