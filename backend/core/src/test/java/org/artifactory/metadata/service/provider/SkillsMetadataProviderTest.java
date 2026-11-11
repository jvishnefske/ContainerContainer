package org.artifactory.metadata.service.provider;

import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Shahaf Golan
 */
@ExtendWith(MockitoExtension.class)
class SkillsMetadataProviderTest {

    private SkillsMetadataProvider skillsMetadataProvider;

    @Mock
    private RepoPath repoPath;

    @BeforeEach
    void setUp() {
        skillsMetadataProvider = new SkillsMetadataProvider();
    }

    @ParameterizedTest
    @CsvSource({
            "'skills-repo/my-package-1.0.0.zip', true",
            "'skills-repo/my-package-1.0.0.claw', false",
            "'skills-repo/readme.txt', false",
            "'', false"
    })
    void isLeadArtifact_shouldReturnExpected_whenGivenPath(String filePath,
            boolean expectedResult) {
        when(repoPath.getPath()).thenReturn(filePath);

        assertEquals(expectedResult, skillsMetadataProvider.isLeadArtifact(repoPath));
    }

    @Test
    void getType_shouldReturnSkills_always() {
        // Act
        String type = skillsMetadataProvider.getType(new PropertiesImpl());

        // Assert
        assertEquals("skills", type);
    }

    @Test
    void getLeadArtifactExtensions_shouldReturnZipExtension() {
        // Act & Assert
        assertEquals(Set.of("zip"), skillsMetadataProvider.getLeadArtifactExtensions());
    }

    @Test
    void getNameKey_shouldReturnSkillName() {
        // Act & Assert
        assertEquals(Optional.of("skill.name"),
                skillsMetadataProvider.typeSpecificMetadataTemplate.getNameKey());
    }

    @Test
    void getVersionKey_shouldReturnSkillVersion() {
        // Act & Assert
        assertEquals(Optional.of("skill.version"),
                skillsMetadataProvider.typeSpecificMetadataTemplate.getVersionKey());
    }

    @Test
    void getDescriptionKey_shouldReturnSkillDescription() {
        // Act & Assert
        assertEquals(Optional.of("skill.description"),
                skillsMetadataProvider.typeSpecificMetadataTemplate.getDescriptionKey());
    }

    @Test
    void getExcludedUserProperties_shouldReturnNameAndVersion() {
        // Act & Assert
        assertEquals(
                Set.of("skill.name", "skill.version", "skill.description", "skill.os"),
                skillsMetadataProvider.typeSpecificMetadataTemplate
                        .getExcludedUserProperties());
    }

    @Test
    void buildVersionFilesQuery_shouldReturnEmpty_whenLeadArtifactHasNoParent() {
        assertTrue(skillsMetadataProvider.buildVersionFilesQuery(
                new RepoPathImpl("skills-remote", "")).isEmpty());
    }

    @Test
    void resolvers_shouldPopulateBom_whenGivenValidProperties() {
        // Arrange
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("skills-remote", "my-package-1.0.0.zip"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("skill.name", "my-package");
        props.put("skill.version", "1.0.0");
        props.put("skill.description", "Build interactive HTML dashboards");

        // Act
        skillsMetadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        skillsMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        skillsMetadataProvider.versionResolver().apply(bom, props, fileInfo, null);
        skillsMetadataProvider.packageDescriptionResolver().apply(bom, props, fileInfo, null);

        // Assert
        assertEquals("my-package", bom.getName());
        assertEquals("skills://my-package", bom.getPkgid());
        assertEquals("1.0.0", bom.getVersion());
        assertEquals("Build interactive HTML dashboards", bom.getDescription());
    }

    @Test
    void resolvers_shouldReturnNull_whenPropertiesAreMissing() {
        // Arrange
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("skills-remote", "my-package-1.0.0.zip"));
        PropertiesImpl props = new PropertiesImpl();

        // Act
        skillsMetadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        skillsMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        skillsMetadataProvider.versionResolver().apply(bom, props, fileInfo, null);

        // Assert
        assertNull(bom.getName());
        assertNull(bom.getPkgid());
        assertNull(bom.getVersion());
    }

    @Test
    void userPropsResolver_shouldExcludeNameAndVersion_whenResolvingUserProperties() {
        // Arrange
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        PropertiesImpl props = new PropertiesImpl();
        props.put("skill.name", "my-package");
        props.put("skill.version", "1.0.0");
        props.put("skill.arch", "linux-x86_64");
        props.put("skill.summary", "A useful package");

        // Act
        skillsMetadataProvider.userPropsResolver().apply(bom, props, null, null);

        // Assert
        assertNull(bom.getUserProperties().get("skill.name"));
        assertNull(bom.getUserProperties().get("skill.version"));
        assertEquals(Set.of("linux-x86_64"),
                bom.getUserProperties().get("skill.arch"));
        assertEquals(Set.of("A useful package"),
                bom.getUserProperties().get("skill.summary"));
    }
}
