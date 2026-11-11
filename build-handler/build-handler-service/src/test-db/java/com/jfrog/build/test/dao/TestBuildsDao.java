package com.jfrog.build.test.dao;

import com.jfrog.build.dao.BuildModulesDao;
import com.jfrog.build.dao.BuildsDao;
import com.jfrog.build.entity.BuildEntity;
import com.jfrog.build.entity.BuildModule;
import com.jfrog.build.entity.ModuleProperty;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.jfrog.build.test.util.TestBuildsUtils.createBuild11;
import static com.jfrog.build.test.util.TestBuildsUtils.createBuild12;
import static com.jfrog.build.test.util.TestBuildsUtils.createBuild14;

@RequiredArgsConstructor
public class TestBuildsDao {

    private final BuildsDao buildsDao;
    private final BuildModulesDao buildModulesDao;

    public void createAndInsertModules11() throws SQLException {
        BuildEntity build11 = createBuild11();
        buildsDao.createBuild(build11);
        long buildId = build11.getBuildId();
        BuildModule buildModule = new BuildModule(101L, buildId, "b11:mod1");
        buildModule.setProperties(new HashSet<>());
        buildModulesDao.createBuildModules(List.of(buildModule));
    }

    public long createAndInsertModules12() throws SQLException {
        BuildEntity build12 = createBuild12();
        long buildId = build12.getBuildId();
        List<ModuleProperty> moduleProperties = new ArrayList<>();
        buildsDao.createBuild(build12);
        BuildModule buildModule1 = new BuildModule(201L, buildId, "b12:the-mod1");
        HashSet<ModuleProperty> props1 = new HashSet<>();
        props1.add(new ModuleProperty(2001L, 201L, "art-name", "the-mod1"));
        props1.add(new ModuleProperty(2002L, 201L, "key", "value"));
        buildModule1.setProperties(props1);
        BuildModule buildModule2 = new BuildModule(202L, buildId, "b12:not-mod2");
        HashSet<ModuleProperty> props2 = new HashSet<>();
        props2.add(new ModuleProperty(2021L, 202L, "art-name", "the-mod2"));
        props2.add(new ModuleProperty(2022L, 202L, "key1", "v1"));
        props2.add(new ModuleProperty(2023L, 202L, "key2", "v2"));
        props2.add(new ModuleProperty(2024L, 202L, "key3", "v3"));
        String longValue = RandomStringUtils.secure().nextAscii(4020);
        props2.add(new ModuleProperty(2025L, 202L, "key4", longValue));
        buildModule2.setProperties(props2);
        buildModulesDao.createBuildModules(List.of(buildModule1, buildModule2));
        moduleProperties.addAll(buildModule1.getProperties());
        moduleProperties.addAll(buildModule2.getProperties());
        buildModulesDao.createBuildModuleProperties(moduleProperties);
        return buildId;
    }

    public long createAndInsertModules14() throws SQLException {
        BuildEntity build14 = createBuild14();
        long buildId = build14.getBuildId();
        buildsDao.createBuild(build14);
        BuildModule buildModule1 = new BuildModule(401L, buildId, "b14:the-mod1");
        buildModule1.setProperties(new HashSet<>());
        BuildModule buildModule2 = new BuildModule(402L, buildId, "b14:not-mod2");
        buildModule2.setProperties(new HashSet<>());
        List<BuildModule> buildModuleList = List.of(buildModule1, buildModule2);
        buildModulesDao.createBuildModules(buildModuleList);
        return buildId;
    }
}
