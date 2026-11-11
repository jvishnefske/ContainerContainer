package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_ANSIBLE_REGISTRY_URL;

class AnsibleTypeSpecificConfigModelTest {

    @Test
    void testGetRepoType() {
        Assertions.assertEquals(RepoType.Ansible, new AnsibleTypeSpecificConfigModel().getRepoType());
    }

    @Test
    void testValidateRemoteTypeSpecific() {
        AnsibleTypeSpecificConfigModel configModel = new AnsibleTypeSpecificConfigModel();
        Assertions.assertDoesNotThrow(configModel::validateRemoteTypeSpecific);
        Assertions.assertEquals(DEFAULT_ANSIBLE_REGISTRY_URL, new AnsibleTypeSpecificConfigModel().getUrl());
    }

    @Test
    void testBypassHeadRequests() {
        AnsibleTypeSpecificConfigModel configModel = new AnsibleTypeSpecificConfigModel();
        Assertions.assertTrue(configModel.isBypassHeadRequests());
        configModel.setBypassHeadRequests(false);
        Assertions.assertFalse(configModel.isBypassHeadRequests());
    }

    @Test
    void testAuthUrlGetterSetter() {
        AnsibleTypeSpecificConfigModel configModel = new AnsibleTypeSpecificConfigModel();
        Assertions.assertEquals(StringUtils.EMPTY, configModel.getAuthUrl());
        configModel.setAuthUrl("https://sso.example.com/auth");
        Assertions.assertEquals("https://sso.example.com/auth", configModel.getAuthUrl());
    }
}
