package org.artifactory.addon.docker;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DockerV2PromotionTest {

    @Test
    public void testIsCopyWhenActionIsCopy() {
        DockerV2Promotion dockerV2Promotion = new DockerV2Promotion();
        dockerV2Promotion.setAction(DockerV2Promotion.EventType.COPY);
        assertTrue(dockerV2Promotion.isCopy());
        assertFalse(dockerV2Promotion.isDelete());

        dockerV2Promotion.setAction(DockerV2Promotion.EventType.DELETE);
        assertFalse(dockerV2Promotion.isCopy());
        assertTrue(dockerV2Promotion.isDelete());
    }

}