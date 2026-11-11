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

package org.artifactory.rest.resource.mfe;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for core unload-set computation on {@link MigratedRoutesPayload}.
 *
 * @author michaelku
 */
class MigratedRoutesUnloadTest {

    @Test
    void rolloutEnabled_unloadsFullRoutesList() {
        List<String> unload = MigratedRoutesPayload.computeUnloadFromCore(
                List.of("/keep-core", "/target"),
                List.of("/keep-core"),
                true);
        Assertions.assertEquals(List.of("/keep-core", "/target"), unload);
    }

    @Test
    void rolloutDisabled_unloadsDifference() {
        List<String> unload = MigratedRoutesPayload.computeUnloadFromCore(
                List.of("/a", "/b", "/c"),
                List.of("/b"),
                false);
        Assertions.assertEquals(List.of("/a", "/c"), unload);
    }

    @Test
    void rolloutDisabled_emptyPhase_unloadsFullList() {
        List<String> routes = List.of("/x", "/y");
        List<String> unload =
                MigratedRoutesPayload.computeUnloadFromCore(routes, List.of(), false);
        Assertions.assertEquals(routes, unload);
    }

    @Test
    void rolloutPhaseEntriesNotInRoutesList_areIgnoredSilently() {
        List<String> unload = MigratedRoutesPayload.computeUnloadFromCore(
                List.of("/registered"),
                List.of("/orphan-phase", "/registered"),
                false);
        Assertions.assertTrue(unload.isEmpty());
    }
}
