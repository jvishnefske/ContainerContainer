package org.artifactory.repo.converter.v4;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.artifactory.repo.converter.v4.SkillsRepoLayoutConverter.BASE_CONFIG_KEY;
import static org.artifactory.repo.converter.v4.SkillsRepoLayoutConverter.REPO_LAYOUT_REF_KEY;
import static org.artifactory.util.RepoLayoutUtils.SIMPLE_DEFAULT_NAME;
import static org.artifactory.util.RepoLayoutUtils.SKILLS_DEFAULT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillsRepoLayoutConverterTest {

    private final SkillsRepoLayoutConverter converter = new SkillsRepoLayoutConverter();

    @Test
    void testSkillsRepoSimpleDefaultMigrated() {
        LocalRepoConfig config = LocalRepoConfig.create("skills-local", RepoType.Skills);
        config.getBaseConfig().setRepoLayoutRef(SIMPLE_DEFAULT_NAME);
        Map<String, Object> map = converter.deserializeMap(config);
        converter.convert(map);
        Map<String, Object> baseConfig = converter.deserializeMap(map.get(BASE_CONFIG_KEY));
        assertEquals(SKILLS_DEFAULT_NAME, baseConfig.get(REPO_LAYOUT_REF_KEY));
    }

    @Test
    void testSkillsRepoCustomLayoutMigrated() {
        String customLayout = "my-custom-layout";
        LocalRepoConfig config = LocalRepoConfig.create("skills-local", RepoType.Skills);
        config.getBaseConfig().setRepoLayoutRef(customLayout);
        Map<String, Object> map = converter.deserializeMap(config);
        converter.convert(map);
        Map<String, Object> baseConfig = converter.deserializeMap(map.get(BASE_CONFIG_KEY));
        assertEquals(SKILLS_DEFAULT_NAME, baseConfig.get(REPO_LAYOUT_REF_KEY));
    }

    @Test
    void testSkillsRepoAlreadySkillsDefaultNotChanged() {
        LocalRepoConfig config = LocalRepoConfig.create("skills-local", RepoType.Skills);
        config.getBaseConfig().setRepoLayoutRef(SKILLS_DEFAULT_NAME);
        Map<String, Object> map = converter.deserializeMap(config);
        converter.convert(map);
        Map<String, Object> baseConfig = converter.deserializeMap(map.get(BASE_CONFIG_KEY));
        assertEquals(SKILLS_DEFAULT_NAME, baseConfig.get(REPO_LAYOUT_REF_KEY));
    }

    @Test
    void testNonSkillsRepoNotChanged() {
        LocalRepoConfig config = LocalRepoConfig.create("generic-local", RepoType.Generic);
        config.getBaseConfig().setRepoLayoutRef(SIMPLE_DEFAULT_NAME);
        Map<String, Object> map = converter.deserializeMap(config);
        converter.convert(map);
        Map<String, Object> baseConfig = converter.deserializeMap(map.get(BASE_CONFIG_KEY));
        assertEquals(SIMPLE_DEFAULT_NAME, baseConfig.get(REPO_LAYOUT_REF_KEY));
    }
}
