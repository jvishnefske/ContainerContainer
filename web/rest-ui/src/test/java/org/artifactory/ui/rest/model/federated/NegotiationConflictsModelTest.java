package org.artifactory.ui.rest.model.federated;

import org.artifactory.common.config.Conflict;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Oleksandr Legenkiy
 */
class NegotiationConflictsModelTest {

    private static final String ERROR_MESSAGE = "some test error message";

    @Test
    void testConstructor() {
        Map<String, Collection<Conflict>> conflictsMap = new HashMap<>();
        Conflict conf1 = new Conflict("key1", "reason1");
        Conflict conf2 = new Conflict("key2", "reason2");
        Conflict conf3 = new Conflict("key3", "reason3");
        conflictsMap.put("url1", List.of(conf1));
        conflictsMap.put("url2", List.of(conf2, conf3));
        NegotiationConflictsModel model = new NegotiationConflictsModel(ERROR_MESSAGE, conflictsMap);

        assertEquals(ERROR_MESSAGE, model.getError());
        assertNotNull(model.getMembers());
        assertEquals(2, model.getMembers().size());
        assertEquals("url1", model.getMembers().get(0).getUrl());
        assertTrue(model.getMembers().get(0).getConflicts().contains(conf1));

        assertEquals("url2", model.getMembers().get(1).getUrl());
        assertTrue(model.getMembers().get(1).getConflicts().contains(conf2));
        assertTrue(model.getMembers().get(1).getConflicts().contains(conf3));
    }

    @Test
    void testConstructorWithNullConflicts() {
        NegotiationConflictsModel model = new NegotiationConflictsModel(ERROR_MESSAGE, null);
        assertEquals(ERROR_MESSAGE, model.getError());
        assertNull(model.getMembers());
    }
}