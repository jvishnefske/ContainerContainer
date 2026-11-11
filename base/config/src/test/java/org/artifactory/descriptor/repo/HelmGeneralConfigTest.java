package org.artifactory.descriptor.repo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HelmGeneralConfigTest {

    @Test
    void isForceOciFirstUiFlow() {
        Assertions.assertTrue(new HelmGeneralConfig().isForceOciFirstUiFlow());
    }

    @Test
    void isForceMetadataNameVersion() {
        Assertions.assertFalse(new HelmGeneralConfig().isForceMetadataNameVersion());

    }

    @Test
    void isForceNonDuplicateChart() {
        Assertions.assertFalse(new HelmGeneralConfig().isForceNonDuplicateChart());

    }
}