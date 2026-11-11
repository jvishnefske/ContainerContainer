package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Shahaf Golan
 */
class SkillsTypeSpecificConfigModelTest {

    @Test
    void getRepoType_shouldReturnSkills_always() {
        SkillsTypeSpecificConfigModel model =
                new SkillsTypeSpecificConfigModel();
        assertEquals(RepoType.Skills, model.getRepoType());
    }

    @Test
    void validateRemoteTypeSpecific_shouldThrowException() {
        SkillsTypeSpecificConfigModel model =
                new SkillsTypeSpecificConfigModel();
        RepoConfigException exception = assertThrows(
                RepoConfigException.class,
                model::validateRemoteTypeSpecific);
        assertEquals(
                "Package type Skills is unsupported in remote repositories",
                exception.getMessage());
    }

    @Test
    void validateVirtualTypeSpecific_shouldThrowException() {
        SkillsTypeSpecificConfigModel model =
                new SkillsTypeSpecificConfigModel();
        RepoConfigException exception = assertThrows(
                RepoConfigException.class,
                () -> model.validateVirtualTypeSpecific(null));
        assertEquals(
                "Package type Skills is unsupported in virtual repositories",
                exception.getMessage());
    }
}
