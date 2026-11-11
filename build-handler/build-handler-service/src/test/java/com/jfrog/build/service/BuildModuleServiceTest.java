package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.model.api.ModuleArtifact;
import com.jfrog.build.model.api.ModuleDependency;
import com.jfrog.build.model.api.BaseArtifact;
import com.jfrog.build.model.api.ModuleArtifactModel;
import com.jfrog.build.model.api.ModuleDependencyModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BuildModuleServiceTest {

    private static final String MODULE_ID = "org.example:lib";
    private static final String NO_PATH = "No path found (externally resolved or deleted/overwritten)";

    private BuildPermissionService permissionService;
    private BuildModuleService moduleService;

    @BeforeEach
    void setUp() {
        BuildBridgeToArtifactory bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        BuildDatabaseService databaseService = mock(BuildDatabaseService.class);
        permissionService = mock(BuildPermissionService.class);
        BuildReadService readService = mock(BuildReadService.class);
        BuildRepoService repoService = mock(BuildRepoService.class);
        moduleService = new BuildModuleService(bridgeToArtifactory, databaseService, permissionService, readService,
                repoService);
    }

    @Test
    void convertToModuleArtifactModel_shouldMapAllFieldsAndActions_whenRepoKeyPresent() {
        when(permissionService.getArtifactActions("repo-local", "lib/lib.jar"))
                .thenReturn(List.of("Download", "ShowInTree", "View"));

        ModuleArtifactModel model = moduleService.convertToModuleArtifactModel(
                moduleArtifact("repo-local", "lib/lib.jar", "lib.jar", "jar", "Add"));

        assertEquals("repo-local", model.getRepoKey());
        assertEquals("lib/lib.jar", model.getPath());
        assertEquals("lib.jar", model.getName());
        assertEquals("jar", model.getType());
        assertEquals(MODULE_ID, model.getModuleName());
        assertEquals("Add", model.getStatus());
        assertEquals("repo-local:lib/lib.jar", model.getRepoPath());
        assertEquals(List.of("Download", "ShowInTree", "View"),
                model.getActions().stream().map(BaseArtifact::getName).toList());
    }

    @Test
    void convertToModuleArtifactModel_shouldReplacePathAndSkipActions_whenRepoKeyBlank() {
        ModuleArtifactModel model = moduleService.convertToModuleArtifactModel(
                moduleArtifact("", "lib/lib.jar", "lib.jar", "jar", "Removed"));

        assertEquals(NO_PATH, model.getPath());
        assertNull(model.getRepoPath());
        assertTrue(model.getActions().isEmpty());
        verifyNoInteractions(permissionService);
    }

    @Test
    void convertToModuleDependencyModel_shouldMapAllFieldsAndActions_whenRepoKeyPresent() {
        when(permissionService.getArtifactActions("repo-local", "org/dep.jar"))
                .thenReturn(List.of("Download", "ShowInTree"));

        ModuleDependencyModel model = moduleService.convertToModuleDependencyModel(
                moduleDependency("repo-local", "org/dep.jar", "dep.jar", "jar", "compile", "Updated"));

        assertEquals("repo-local", model.getRepoKey());
        assertEquals("org/dep.jar", model.getPath());
        assertEquals("dep.jar", model.getName());
        assertEquals("jar", model.getType());
        assertEquals("compile", model.getScope());
        assertEquals(MODULE_ID, model.getModuleName());
        assertEquals("Updated", model.getStatus());
        assertEquals("repo-local:org/dep.jar", model.getRepoPath());
        assertEquals(List.of("Download", "ShowInTree"),
                model.getActions().stream().map(BaseArtifact::getName).toList());
    }

    @Test
    void convertToModuleDependencyModel_shouldReplacePathAndSkipActions_whenRepoKeyBlank() {
        ModuleDependencyModel model = moduleService.convertToModuleDependencyModel(
                moduleDependency("", "org/dep.jar", "dep.jar", "jar", "compile", "Removed"));

        assertEquals(NO_PATH, model.getPath());
        assertNull(model.getRepoPath());
        assertTrue(model.getActions().isEmpty());
        verifyNoInteractions(permissionService);
    }

    private static ModuleArtifact moduleArtifact(
            String repoKey, String path, String name, String type, String status) {
        ModuleArtifact artifact = new ModuleArtifact(repoKey, ".", name, type, "sha1");
        artifact.setRepoKey(repoKey);
        artifact.setPath(path);
        artifact.setStatus(status);
        artifact.setModule(MODULE_ID);
        return artifact;
    }

    private static ModuleDependency moduleDependency(
            String repoKey, String path, String name, String type, String scope, String status) {
        ModuleDependency dependency = new ModuleDependency(repoKey, ".", name, type, scope, "sha1");
        dependency.setRepoKey(repoKey);
        dependency.setPath(path);
        dependency.setStatus(status);
        dependency.setModule(MODULE_ID);
        return dependency;
    }
}
