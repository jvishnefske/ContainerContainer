package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;


class HelmTypeSpecificConfigModelTest {

    @Test
    void testEnforceLayoutDefaultValues(){
        HelmTypeSpecificConfigModel helmTypeSpecificConfigModel = new HelmTypeSpecificConfigModel();
        assertFalse(helmTypeSpecificConfigModel.isForceNonDuplicateChart());
        assertFalse(helmTypeSpecificConfigModel.isForceMetadataNameVersion());
    }

}