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

@ExtendWith(MockitoExtension.class)
class LuaRocksMetadataProviderTest {

    private LuaRocksMetadataProvider luaRocksMetadataProvider;

    @Mock
    private RepoPath repoPath;

    @BeforeEach
    void setUp() {
        luaRocksMetadataProvider = new LuaRocksMetadataProvider();
    }

    @ParameterizedTest
    @CsvSource({
            // Valid .rockspec extension - the primary lead artifact type
            "'luarocks-repo/lua-cjson-2.1.0.rockspec', true",
            // Valid .rock extension - not a lead artifact
            "'luarocks-repo/lua-cjson-2.1.0.rock', false",
            // Generic file - not a lead artifact
            "'luarocks-repo/readme.txt', false",
            // Empty path
            "'', false"
    })
    void isLeadArtifact_shouldReturnExpected_whenGivenPath(String filePath, boolean expectedResult) {
        when(repoPath.getPath()).thenReturn(filePath);

        assertEquals(expectedResult, luaRocksMetadataProvider.isLeadArtifact(repoPath));
    }

    @Test
    void getType_shouldReturnLuaRocks_always() {
        // Act
        String type = luaRocksMetadataProvider.getType(new PropertiesImpl());

        // Assert
        assertEquals("luarocks", type);
    }

    @Test
    void getLeadArtifactExtensions_shouldReturnRockExtension() {
        // Act & Assert
        assertEquals(Set.of("rockspec"), luaRocksMetadataProvider.getLeadArtifactExtensions());
    }

    @Test
    void getNameKey_shouldReturnLuaRocksName() {
        // Act & Assert
        assertEquals(Optional.of("luarocks.name"),
                luaRocksMetadataProvider.typeSpecificMetadataTemplate.getNameKey());
    }

    @Test
    void getVersionKey_shouldReturnLuaRocksVersion() {
        // Act & Assert
        assertEquals(Optional.of("luarocks.version"),
                luaRocksMetadataProvider.typeSpecificMetadataTemplate.getVersionKey());
    }

    @Test
    void getArchitectureKey_shouldReturnLuaRocksArch() {
        // Act & Assert
        assertEquals(Optional.of("luarocks.arch"),
                luaRocksMetadataProvider.typeSpecificMetadataTemplate.getArchitectureKey());
    }

    @Test
    void getDescriptionKey_shouldReturnLuaRocksSummary() {
        // Act & Assert
        assertEquals(Optional.of("luarocks.summary"),
                luaRocksMetadataProvider.typeSpecificMetadataTemplate.getDescriptionKey());
    }

    @Test
    void getExcludedUserProperties_shouldReturnNameAndVersion() {
        // Act & Assert
        assertEquals(Set.of("luarocks.name", "luarocks.version"),
                luaRocksMetadataProvider.typeSpecificMetadataTemplate.getExcludedUserProperties());
    }

    @Test
    void buildVersionFilesQuery_shouldReturnQuery_whenLeadArtifactHasParent() {
        assertTrue(luaRocksMetadataProvider.buildVersionFilesQuery(
                new RepoPathImpl("luarocks-local", "some/path/lua-cjson-2.1.0.rockspec")).isPresent());
    }

    @Test
    void buildVersionFilesQuery_shouldReturnEmpty_whenLeadArtifactHasNoParent() {
        assertTrue(luaRocksMetadataProvider.buildVersionFilesQuery(
                new RepoPathImpl("luarocks-local", "")).isEmpty());
    }

    @Test
    void resolvers_shouldPopulateBom_whenGivenValidProperties() {
        // Arrange
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("luarocks-local", "lua-cjson-2.1.0-1.rock"));
        PropertiesImpl props = new PropertiesImpl();
        props.put("luarocks.name", "lua-cjson");
        props.put("luarocks.version", "2.1.0-1");

        // Act
        luaRocksMetadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        luaRocksMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        luaRocksMetadataProvider.versionResolver().apply(bom, props, fileInfo, null);

        // Assert
        assertEquals("lua-cjson", bom.getName());
        assertEquals("luarocks://lua-cjson", bom.getPkgid());
        assertEquals("2.1.0-1", bom.getVersion());
    }

    @Test
    void resolvers_shouldReturnNull_whenPropertiesAreMissing() {
        // Arrange
        MutableMetadataEntityBOM bom = new MutableMetadataEntityBOM();
        FileInfoImpl fileInfo = new FileInfoImpl(
                new RepoPathImpl("luarocks-local", "lua-cjson-2.1.0-1.rock"));
        PropertiesImpl props = new PropertiesImpl();

        // Act
        luaRocksMetadataProvider.nameResolver().apply(bom, props, fileInfo, null);
        luaRocksMetadataProvider.pkgIdResolver().apply(bom, props, fileInfo, null);
        luaRocksMetadataProvider.versionResolver().apply(bom, props, fileInfo, null);

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
        props.put("luarocks.name", "lua-cjson");
        props.put("luarocks.version", "2.1.0-1");
        props.put("luarocks.arch", "linux-x86_64");
        props.put("luarocks.summary", "A fast JSON encoding library");

        // Act
        luaRocksMetadataProvider.userPropsResolver().apply(bom, props, null, null);

        // Assert - name and version should be excluded, arch and summary should be included
        assertNull(bom.getUserProperties().get("luarocks.name"));
        assertNull(bom.getUserProperties().get("luarocks.version"));
        assertEquals(Set.of("linux-x86_64"), bom.getUserProperties().get("luarocks.arch"));
        assertEquals(Set.of("A fast JSON encoding library"), bom.getUserProperties().get("luarocks.summary"));
    }
}
