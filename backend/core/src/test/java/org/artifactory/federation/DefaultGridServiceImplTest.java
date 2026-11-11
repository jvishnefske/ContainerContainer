package org.artifactory.federation;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultGridServiceImplTest {

    private final DefaultGridServiceImpl service = new DefaultGridServiceImpl();

    @Test
    void isPartOfGrid_returnsFalse_forOss() {
        assertFalse(service.isPartOfGrid());
    }

    @Test
    void getTopologyKey_returnsEmpty_forOss() {
        assertEquals(Optional.empty(), service.getTopologyKey());
    }

    @Test
    void getJpdId_returnsEmpty_forOss() {
        assertEquals(Optional.empty(), service.getJpdId());
    }

    @Test
    void getGridMainSiteUrl_returnsEmpty_forOss() {
        assertEquals(Optional.empty(), service.getGridMainSiteUrl());
    }
}
