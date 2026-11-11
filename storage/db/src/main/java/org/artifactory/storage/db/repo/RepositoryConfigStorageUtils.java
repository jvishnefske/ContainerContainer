package org.artifactory.storage.db.repo;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.springframework.util.CollectionUtils;

import java.util.Set;
import java.util.stream.Collectors;

public class RepositoryConfigStorageUtils {

    public static void assertNonZeroModificationDates(Set<DbRepositoryConfig> dbRepositoryConfigs) {
        if (CollectionUtils.isEmpty(dbRepositoryConfigs)) {
            return;
        }
        String repoKeysWithoutModificationDate = dbRepositoryConfigs.stream()
                .filter(config -> config.getModified() == 0)
                .map(DbRepositoryConfig::getKey)
                .collect(Collectors.joining(","));
        if (StringUtils.isNoneEmpty(repoKeysWithoutModificationDate)) {
            throw new IllegalArgumentException(
                    "Can not save repositories without modification dates. Repo keys: " +
                            repoKeysWithoutModificationDate);
        }
    }

    public static void assertNonZeroCreatedDates(Set<DbRepositoryConfig> dbRepositoryConfigs) {
        if (CollectionUtils.isEmpty(dbRepositoryConfigs)) {
            return;
        }
        String repoKeysWithoutCreationDate = dbRepositoryConfigs.stream()
                .filter(config -> config.getCreated() == 0)
                .map(DbRepositoryConfig::getKey)
                .collect(Collectors.joining(","));
        if (StringUtils.isNoneEmpty(repoKeysWithoutCreationDate)) {
            throw new IllegalArgumentException(
                    "Can not save repositories without creation dates. Repo keys: " + repoKeysWithoutCreationDate);
        }
    }
}