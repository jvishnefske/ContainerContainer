package org.artifactory.storage.db.repo;

import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RepositoryConfigStorageUtilsTest {

    @ParameterizedTest
    @MethodSource("provideNonValidModificationDbRepoConfig")
    public void testAssertZeroModificationDates(Set<DbRepositoryConfig> configs, String expectedMessage) {
        assertThrows(IllegalArgumentException.class,
                () -> RepositoryConfigStorageUtils.assertNonZeroModificationDates(configs), expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("provideValidDbRepoConfig")
    public void testAssertNonZeroModificationDates(Set<DbRepositoryConfig> configs) {
        assertDoesNotThrow(() -> RepositoryConfigStorageUtils.assertNonZeroModificationDates(configs));
    }

    @ParameterizedTest
    @MethodSource("provideNonValidCreationDbRepoConfig")
    public void testAssertZeroCreationDates(Set<DbRepositoryConfig> configs, String expectedMessage) {
        assertThrows(IllegalArgumentException.class,
                () -> RepositoryConfigStorageUtils.assertNonZeroCreatedDates(configs), expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("provideValidDbRepoConfig")
    public void testAssertNonZeroCreationDates(Set<DbRepositoryConfig> configs) {
        assertDoesNotThrow(() -> RepositoryConfigStorageUtils.assertNonZeroCreatedDates(configs));
    }

    public static Stream<Arguments> provideValidDbRepoConfig() {
        DbRepositoryConfig validRepo1 = new DbRepositoryConfig();
        validRepo1.setModified(1L);
        validRepo1.setCreated(1L);
        DbRepositoryConfig validRepo2 = new DbRepositoryConfig();
        validRepo2.setModified(2L);
        validRepo2.setCreated(2L);

        return Stream.of(
                Arguments.of(Set.of(validRepo1, validRepo2)),
                Arguments.of(Set.of())
        );
    }

    public static Stream<Arguments> provideNonValidModificationDbRepoConfig() {
        DbRepositoryConfig withoutRepoKey = new DbRepositoryConfig();
        DbRepositoryConfig withRepoKey1 = new DbRepositoryConfig();
        withRepoKey1.setKey("repo1");
        DbRepositoryConfig withRepoKey2 = new DbRepositoryConfig();
        withRepoKey2.setKey("repo2");
        DbRepositoryConfig withRepoKeAndModificationDate = new DbRepositoryConfig();
        withRepoKeAndModificationDate.setKey("repo3");
        withRepoKeAndModificationDate.setModified(1L);

        return Stream.of(
                Arguments.of(Set.of(withoutRepoKey),
                        "Can not save repositories without modification dates. Repo keys: null"),
                Arguments.of(Set.of(withRepoKey1, withRepoKey2, withRepoKeAndModificationDate),
                        "Can not save repositories without modification dates. Repo keys: repo1,repo2")
        );
    }

    public static Stream<Arguments> provideNonValidCreationDbRepoConfig() {
        DbRepositoryConfig withoutRepoKey = new DbRepositoryConfig();
        DbRepositoryConfig withRepoKey1 = new DbRepositoryConfig();
        withRepoKey1.setKey("repo1");
        DbRepositoryConfig withRepoKey2 = new DbRepositoryConfig();
        withRepoKey2.setKey("repo2");
        DbRepositoryConfig withRepoKeAndModificationDate = new DbRepositoryConfig();
        withRepoKeAndModificationDate.setKey("repo3");
        withRepoKeAndModificationDate.setCreated(1L);

        return Stream.of(
                Arguments.of(Set.of(withoutRepoKey),
                        "Can not save repositories without creation dates. Repo keys: null"),
                Arguments.of(Set.of(withRepoKey1, withRepoKey2, withRepoKeAndModificationDate),
                        "Can not save repositories without creation dates. Repo keys: repo1,repo2")
        );
    }
}