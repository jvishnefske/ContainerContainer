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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests for migrated-routes payload deserialization.
 *
 * @author michaelku
 */
class MigratedRoutesPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void routesOnlyJson_hasEmptyRolloutPhaseList() throws JsonProcessingException {
        MigratedRoutesPayload payload =
                objectMapper.readValue("{\"routesList\": [\"/a\", \"/b\"]}", MigratedRoutesPayload.class);
        Assertions.assertEquals(List.of("/a", "/b"), payload.getRoutesList());
        Assertions.assertTrue(payload.getRolloutPhaseList().isEmpty());
    }

    @Test
    void bothKeysDeserialize() throws JsonProcessingException {
        MigratedRoutesPayload payload = objectMapper.readValue(
                "{\"routesList\":[\"/x\"],\"rolloutPhaseList\":[\"/y\"]}", MigratedRoutesPayload.class);
        Assertions.assertEquals(List.of("/x"), payload.getRoutesList());
        Assertions.assertEquals(List.of("/y"), payload.getRolloutPhaseList());
    }

    @Test
    void emptyRolloutPhaseKey_isEmptyNotNullList() throws JsonProcessingException {
        MigratedRoutesPayload payload = objectMapper.readValue(
                "{\"routesList\": [\"/only\"], \"rolloutPhaseList\": []}", MigratedRoutesPayload.class);
        Assertions.assertNotNull(payload.getRolloutPhaseList());
        Assertions.assertTrue(payload.getRolloutPhaseList().isEmpty());
    }

    @Test
    void listsAreImmutable() throws JsonProcessingException {
        MigratedRoutesPayload payload = objectMapper.readValue(
                "{\"routesList\":[\"/x\"],\"rolloutPhaseList\":[\"/y\"]}", MigratedRoutesPayload.class);
        Assertions.assertThrows(UnsupportedOperationException.class, () -> payload.getRoutesList().add("/z"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> payload.getRolloutPhaseList().add("/z"));
    }
}
