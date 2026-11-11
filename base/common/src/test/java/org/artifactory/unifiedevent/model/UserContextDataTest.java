package org.artifactory.unifiedevent.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UserContextDataTest {

    @Test
    void testUserContextDataCreation() {
        UserContextData userContextData = UserContextData.builder()
                .actor("testActor")
                .build();
        Assertions.assertEquals("testActor", userContextData.actor());
    }

}
