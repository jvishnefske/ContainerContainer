package org.artifactory.rest.resource.mfe;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MfeProjectRegistryTest {

    @Test
    void findProjectForResource_shouldReturnCore_whenPathIsRootJs() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectForResource("/js/manifest.esm.js");

        assertNotNull(project);
        assertEquals(MfeName.CORE, project.getMfeName());
    }

    @Test
    void findProjectForResource_shouldReturnCore_whenPathIsRootImg() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectForResource("/img/logo.png");

        assertNotNull(project);
        assertEquals(MfeName.CORE, project.getMfeName());
    }

    @Test
    void findProjectForResource_shouldReturnCore_whenPathIsRootAssets() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectForResource("/assets/chunk.js");

        assertNotNull(project);
        assertEquals(MfeName.CORE, project.getMfeName());
    }

    @Test
    void findProjectForResource_shouldReturnRtui_whenPathIsRtuiImg() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectForResource("/rtui/img/logo.png");

        assertNotNull(project);
        assertEquals(MfeName.RTUI, project.getMfeName());
    }

    @Test
    void findProjectForResource_shouldReturnRtui_whenPathIsRtuiJs() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectForResource("/rtui/js/app.esm.js");

        assertNotNull(project);
        assertEquals(MfeName.RTUI, project.getMfeName());
    }

    @Test
    void findProjectForResource_shouldReturnRtui_whenPathIsRtuiAssets() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectForResource("/rtui/assets/chunk.js");

        assertNotNull(project);
        assertEquals(MfeName.RTUI, project.getMfeName());
    }

    @Test
    void findProjectForResource_shouldReturnBuildinfomfe_whenPathIsBuildinfomfeAssets() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectForResource("/buildinfomfe/assets/index.js");

        assertNotNull(project);
        assertEquals(MfeName.BUILDINFOMFE, project.getMfeName());
    }

    @Test
    void findProjectForResource_shouldReturnNull_whenPathIsNull() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectForResource(null);

        assertNull(project);
    }

    @Test
    void findProjectForResource_shouldReturnNull_whenPathDoesNotMatchAnyProject() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectForResource("/unknown/foo.js");

        assertNull(project);
    }

    @Test
    void findProjectForResource_shouldReturnRtui_whenPathIsExactMigratedRoutes() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectForResource("/rtui/migrated-routes.json");

        assertNotNull(project);
        assertEquals(MfeName.RTUI, project.getMfeName());
    }

    @Test
    void findProjectForResource_shouldReturnNull_whenPathSharesMigratedRoutesPrefix() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectForResource("/rtui/migrated-routes.json.backup");

        assertNull(project);
    }

    @Test
    void findProjectForResource_shouldReturnNull_whenPathIsMigratedRoutesWithTrailingSlash() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectForResource("/rtui/migrated-routes.json/");

        assertNull(project);
    }

    @Test
    void findProjectForResource_shouldReturnRetention_whenPathIsExactMigratedRoutes() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectForResource("/retention/migrated-routes.json");

        assertNotNull(project);
        assertEquals(MfeName.RETENTION, project.getMfeName());
    }

    @Test
    void findProjectByMfeName_shouldReturnProject_whenKnownName() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectByMfeName(MfeName.PACKAGES);

        assertNotNull(project);
        assertEquals(MfeName.PACKAGES, project.getMfeName());
    }

    @Test
    void findProjectByMfeName_shouldReturnNull_whenMfeNameIsNull() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectByMfeName(null);

        assertNull(project);
    }

    @Test
    void findProjectByMfeName_shouldReturnProject_whenCore() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectByMfeName(MfeName.CORE);

        assertNotNull(project);
        assertEquals(MfeName.CORE, project.getMfeName());
    }

    @Test
    void findProjectByMfeName_shouldReturnProject_whenRetention() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectByMfeName(MfeName.RETENTION);

        assertNotNull(project);
        assertEquals(MfeName.RETENTION, project.getMfeName());
    }

    @Test
    void findProjectByMfeName_shouldReturnProject_whenBuildinfomfe() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        MfeProjectConfig project = registry.findProjectByMfeName(MfeName.BUILDINFOMFE);

        assertNotNull(project);
        assertEquals(MfeName.BUILDINFOMFE, project.getMfeName());
    }

    @Test
    void getAllProjects_shouldReturnAllFiveConfiguredProjects() {
        MfeProjectRegistry registry = new MfeProjectRegistry();

        List<MfeProjectConfig> projects = registry.getAllProjects();

        assertEquals(7, projects.size());
        List<MfeName> names = projects.stream().map(MfeProjectConfig::getMfeName).collect(Collectors.toList());
        assertTrue(names.contains(MfeName.CORE));
        assertTrue(names.contains(MfeName.RTUI));
        assertTrue(names.contains(MfeName.RETENTION));
        assertTrue(names.contains(MfeName.PACKAGES));
        assertTrue(names.contains(MfeName.BUILDINFOMFE));
        assertTrue(names.contains(MfeName.RELEASEBUNDLES));
        assertTrue(names.contains(MfeName.RBV1RECEIVED));
    }
}
