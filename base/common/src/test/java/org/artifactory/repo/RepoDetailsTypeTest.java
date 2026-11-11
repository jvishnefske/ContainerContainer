/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the RepoDetailsType enum
 *
 * @author Noam Y. Tenne
 */
class RepoDetailsTypeTest {

    /**
     * Tests the different type display names
     */
    @Test
    void testDisplayName() {
        assertEquals("Local", LOCAL.typeName());
        assertEquals("Remote", REMOTE.typeName());
        assertEquals("Virtual", VIRTUAL.typeName());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testGetRepoDetailsTypeWithNullOrEmptyList(Set<String> types) {
        Set<RepoDetailsType> expected = new HashSet<>(Arrays.asList(RepoDetailsType.values()));
        Set<RepoDetailsType> actual = RepoDetailsType.getRepoDetailsType(types);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("repoDetailsTypeProvider")
    void testGetRepoTypesWithValidDetailsType(Set<String> repoTypes, Set<RepoDetailsType> expected) {
        Set<RepoDetailsType> actual = RepoDetailsType.getRepoDetailsType(repoTypes);
        assertEquals(expected, actual);
    }

    @Test
    void testGetRepoDetailsTypeWithInvalidType() {
        Set<String> types = Set.of("invalid");
        assertThrows(IllegalArgumentException.class, () -> RepoDetailsType.getRepoDetailsType(types));
    }

    @ParameterizedTest
    @MethodSource("provideArgsForFromStringTest")
    void testFromString(String input, RepoDetailsType expected) {
        assertEquals(expected, RepoDetailsType.fromString(input));
    }

    static Stream<Arguments> repoDetailsTypeProvider() {
        return Stream.of(
                Arguments.of(Set.of("local"), Set.of(LOCAL)),
                Arguments.of(Set.of("remote"), Set.of(REMOTE)),
                Arguments.of(Set.of("virtual"), Set.of(VIRTUAL)),
                Arguments.of(Set.of("federated"), Set.of(FEDERATED)),
                Arguments.of(Set.of("releaseBundle"), Set.of(RELEASE_BUNDLE)),
                Arguments.of(Set.of("local", "remote"), Set.of(LOCAL, REMOTE)),
                Arguments.of(Set.of("local", "virtual"), Set.of(LOCAL, VIRTUAL)),
                Arguments.of(Set.of("remote", "virtual"), Set.of(REMOTE, VIRTUAL)),
                Arguments.of(Set.of("local", "remote", "virtual"), Set.of(LOCAL, REMOTE, VIRTUAL)),
                Arguments.of(Set.of("local", "remote", "virtual", "federated"),
                        Set.of(LOCAL, REMOTE, VIRTUAL, FEDERATED)),
                Arguments.of(Set.of("local", "remote", "virtual", "federated", "releaseBundle"),
                        Set.of(LOCAL, REMOTE, VIRTUAL, FEDERATED, RELEASE_BUNDLE))
        );
    }

    static Stream<Arguments> provideArgsForFromStringTest() {
        return Stream.of(
                Arguments.of("local", LOCAL),
                Arguments.of("remote", REMOTE),
                Arguments.of("virtual", VIRTUAL),
                Arguments.of("federated", FEDERATED),
                Arguments.of("ReleaseBundle", RELEASE_BUNDLE),
                Arguments.of("Release Bundle", RELEASE_BUNDLE),
                Arguments.of("distribution", RELEASE_BUNDLE),
                Arguments.of("someIvalidInput", null)
        );
    }
}