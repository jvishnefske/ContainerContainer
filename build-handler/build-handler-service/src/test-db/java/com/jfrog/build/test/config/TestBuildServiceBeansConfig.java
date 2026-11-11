package com.jfrog.build.test.config;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.dao.BuildModulesDao;
import com.jfrog.build.dao.BuildsDao;
import com.jfrog.build.test.bridge.TestBuildBridgeToArtifactory;
import com.jfrog.build.test.dao.TestBuildArtifactsDao;
import com.jfrog.build.test.dao.TestBuildDependenciesDao;
import com.jfrog.build.test.dao.TestBuildModulesDao;
import com.jfrog.build.test.dao.TestBuildsDao;
import lombok.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

import static com.jfrog.build.test.database.TestDatabaseProvider.getJdbcHelper;

/**
 * There is no JDBC Helper available yet during eager Spring context init phase.
 * Hence, those beans, which depend on it, are created in a lazy way.
 */
@Configuration
@EnableTransactionManagement
public class TestBuildServiceBeansConfig {

    @Bean
    @Primary
    public TaskExecutor taskExecutor() {
        return new SyncTaskExecutor();
    }

    @Bean
    public BuildBridgeToArtifactory buildBridgeToArtifactory() {
        return new TestBuildBridgeToArtifactory();
    }

    @Bean
    public TestBuildArtifactsDao testBuildArtifactsDao(
            @NonNull BuildBridgeToArtifactory bridgeToArtifactory) {
        return new TestBuildArtifactsDao(bridgeToArtifactory);
    }

    @Bean
    public TestBuildDependenciesDao testBuildDependenciesDao(
            @NonNull BuildBridgeToArtifactory bridgeToArtifactory) {
        return new TestBuildDependenciesDao(bridgeToArtifactory);
    }

    @Bean
    public TestBuildModulesDao testBuildModulesDao(
            @NonNull BuildBridgeToArtifactory bridgeToArtifactory) {
        return new TestBuildModulesDao(bridgeToArtifactory);
    }

    @Bean
    public TestBuildsDao testBuildsDao(
            @NonNull BuildsDao buildsDao,
            @NonNull BuildModulesDao buildModulesDao) {
        return new TestBuildsDao(
                buildsDao,
                buildModulesDao
        );
    }

    @Bean
    @Lazy
    @Primary
    public PlatformTransactionManager transactionManager() {
        DataSource dataSource = getJdbcHelper().getDataSource();
        return new DataSourceTransactionManager(dataSource);
    }
}