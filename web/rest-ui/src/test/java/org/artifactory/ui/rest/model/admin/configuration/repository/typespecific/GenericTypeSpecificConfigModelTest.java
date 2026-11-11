package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.rest.common.exception.RepoConfigException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Igor Usenko
 */
class GenericTypeSpecificConfigModelTest {

    @Test
    void whenNullValues() throws RepoConfigException {
        GenericTypeSpecificConfigModel model = new GenericTypeSpecificConfigModel();
        model.setEnableTokenAuthentication(null);
        model.setListRemoteFolderItems(null);

        model.validateRemoteTypeSpecific();

        assertFalse(model.isEnableTokenAuthentication());
        assertTrue(model.isListRemoteFolderItems());
    }
}