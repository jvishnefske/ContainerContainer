package com.jfrog.build.dao;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.dao.util.BuildsDaoUtils;
import com.jfrog.build.entity.BuildModule;
import com.jfrog.build.entity.ModuleProperty;
import com.jfrog.build.service.BuildSysConfigService;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.util.DbUtils;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.collect.Lists.partition;
import static com.jfrog.build.dao.util.BuildsDaoUtils.getDbIndexedValueMaxSize;
import static com.jfrog.build.dao.util.BuildsDaoUtils.preparePropValue;

@Slf4j
@Repository
@AllArgsConstructor
public class BuildModulesDao {

    private final BuildBridgeToArtifactory bridgeToArtifactory;
    private final BuildSysConfigService sysConfigService;

    public void createBuildModules(List<BuildModule> modules) throws SQLException {
        if (modules.isEmpty()) {
            return;
        }
        int bulkSize = sysConfigService.getDbOperationsBulkSize();
        for (List<BuildModule> partition : partition(modules, bulkSize)) {
            List<Object[]> params = mapBuildModuleParams(partition);
            getJdbcHelper().executeBulkUpdate("INSERT INTO build_modules VALUES(?, ?, ?, ?)", params);
        }
    }

    public void createBuildModuleProperties(@NonNull List<ModuleProperty> moduleProperties) throws SQLException {
        if (moduleProperties.isEmpty()) {
            return;
        }
        int bulkSize = sysConfigService.getDbOperationsBulkSize();
        for (List<ModuleProperty> partition : partition(moduleProperties, bulkSize)) {
            List<Object[]> params = mapBuildModulePropertyParams(partition);
            getJdbcHelper().executeBulkUpdate("INSERT INTO module_props VALUES(?, ?, ?, ?)", params);
        }
    }

    public int deleteBuildModules(List<Long> moduleIds, long buildId) throws SQLException {
        int res = 0;
        if (!moduleIds.isEmpty()) {
            res += BuildsDaoUtils.executeLimitedSizedQuery(
                    getJdbcHelper(),
                    "DELETE FROM module_props WHERE module_id IN (#)",
                    moduleIds,
                    sysConfigService.getDbOperationsBulkSize());
        }
        res += getJdbcHelper().executeUpdate("DELETE FROM build_modules WHERE build_id = ?", buildId);
        return res;
    }

    public int deleteAllBuildModules() throws SQLException {
        int res = getJdbcHelper().executeUpdate("DELETE FROM module_props");
        res += getJdbcHelper().executeUpdate("DELETE FROM build_modules");
        return res;
    }

    public List<Long> findModuleIdsForBuild(long buildId) throws SQLException {
        ResultSet rs = null;
        List<Long> moduleIds = null;
        try {
            rs = getJdbcHelper().executeSelect("SELECT module_id FROM build_modules WHERE build_id = ?", buildId);
            moduleIds = new ArrayList<>();
            while (rs.next()) {
                moduleIds.add(rs.getLong(1));
            }
        } finally {
            DbUtils.close(rs);
        }
        return moduleIds;
    }


    @NonNull
    private List<Object[]> mapBuildModuleParams(@NonNull List<BuildModule> modules) {
        return modules.stream().map(module -> new Object[] {
                module.getModuleId(),
                module.getBuildId(),
                module.getModuleNameId(),
                module.getType()
        }).toList();
    }

    @NonNull
    private List<Object[]> mapBuildModulePropertyParams(@NonNull List<ModuleProperty> moduleProperties) {
        DbType databaseType = bridgeToArtifactory.getDatabaseType();
        int indexedValueMaxSize = getDbIndexedValueMaxSize(databaseType);
        return moduleProperties.stream().map(property -> new Object[] {
                property.getPropId(),
                property.getModuleId(),
                property.getPropKey(),
                preparePropValue(indexedValueMaxSize, property.getPropKey(), property.getPropValue())
        }).toList();
    }

    @NonNull
    private JdbcHelper getJdbcHelper() {
        return bridgeToArtifactory.getJdbcHelper();
    }
}
