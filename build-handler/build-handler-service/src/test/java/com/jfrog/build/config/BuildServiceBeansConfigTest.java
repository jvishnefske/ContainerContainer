package com.jfrog.build.config;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.dao.BuildArtifactsDao;
import com.jfrog.build.dao.BuildDependenciesDao;
import com.jfrog.build.dao.BuildModulesDao;
import com.jfrog.build.dao.BuildsDao;
import com.jfrog.build.service.BuildDatabaseService;
import com.jfrog.build.service.BuildSysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class BuildServiceBeansConfigTest {

    private BuildServiceBeansConfig config;

    @BeforeEach
    void setUp() {
        BuildBridgeToArtifactory bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        config = new BuildServiceBeansConfig(bridgeToArtifactory);
    }

    @Test
    void buildSysConfigService_ReturnsNonNull() {
        BuildSysConfigService bean = config.buildSysConfigService();
        assertNotNull(bean);
    }

    @Test
    void buildsDao_ReturnsNonNull() {
        BuildSysConfigService sysConfigService = config.buildSysConfigService();
        BuildsDao bean = config.buildsDao(sysConfigService);
        assertNotNull(bean);
    }

    @Test
    void buildModulesDao_ReturnsNonNull() {
        BuildSysConfigService sysConfigService = config.buildSysConfigService();
        BuildModulesDao bean = config.buildModulesDao(sysConfigService);
        assertNotNull(bean);
    }

    @Test
    void buildArtifactsDao_ReturnsNonNull() {
        BuildSysConfigService sysConfigService = config.buildSysConfigService();
        BuildArtifactsDao bean = config.buildArtifactsDao(sysConfigService);
        assertNotNull(bean);
    }

    @Test
    void buildDependenciesDao_ReturnsNonNull() {
        BuildSysConfigService sysConfigService = config.buildSysConfigService();
        BuildDependenciesDao bean = config.buildDependenciesDao(sysConfigService);
        assertNotNull(bean);
    }

    @Test
    void buildDatabaseServiceImpl() {
        BuildSysConfigService sysConfigService = config.buildSysConfigService();
        BuildsDao buildsDao = config.buildsDao(sysConfigService);
        BuildModulesDao buildModulesDao = config.buildModulesDao(sysConfigService);
        BuildArtifactsDao buildArtifactsDao = config.buildArtifactsDao(sysConfigService);
        BuildDependenciesDao buildDependenciesDao = config.buildDependenciesDao(sysConfigService);
        BuildDatabaseService bean = config.buildDatabaseService(
                buildsDao,
                buildModulesDao,
                buildArtifactsDao,
                buildDependenciesDao
        );
        assertNotNull(bean);
        assertInstanceOf(BuildDatabaseService.class, bean);
    }
}
