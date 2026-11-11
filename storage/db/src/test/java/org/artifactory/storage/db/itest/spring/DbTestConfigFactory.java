/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.itest.spring;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.mbean.MBeanRegistrationServiceImpl;
import org.artifactory.storage.db.init.ArtifactoryTomcatDataSource;
import org.artifactory.storage.db.init.CommonDataSourceFactory;
import org.artifactory.storage.db.spring.DbConfigFactory;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.common.ResourceUtils;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.util.querybuilder.DerbyQueryBuilder;
import org.jfrog.storage.util.querybuilder.IQueryBuilder;
import org.jfrog.storage.util.querybuilder.MysqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.OracleQueryBuilder;
import org.jfrog.storage.util.querybuilder.PostgresqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.SqlServerQueryBuilder;
import org.jfrog.storage.wrapper.JFrogDataSourceWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

import static org.artifactory.common.config.db.ArtifactoryDbProperties.DEFAULT_POOL_TYPE;

/**
 * A Spring {@link org.springframework.context.annotation.Configuration} to initialized database beans.
 *
 * @author Yossi Shaul
 */
@Configuration
public class DbTestConfigFactory implements BeanFactoryAware {

    public static final String DERBY = "derby";
    private BeanFactory beanFactory;

    @Bean(name = "dataSource")
    public JFrogDataSource createDataSource() {
        ArtifactoryDbProperties dbProperties = beanFactory.getBean("dbProperties", ArtifactoryDbProperties.class);
        return CommonDataSourceFactory.createDataSource(dbProperties, false);
    }

    /**
     * @return Auto-commit datasource for the unique ids generator.
     * @see org.artifactory.storage.db.spring.DbConfigFactory#createUniqueIdsDataSource()
     */
    @Bean(name = "uniqueIdsDataSource")
    public JFrogDataSource createUniqueIdsDataSource() {
        ArtifactoryDbProperties dbProperties = beanFactory.getBean("dbProperties", ArtifactoryDbProperties.class);
        return CommonDataSourceFactory.createUniqueDataSourceByType(dbProperties);
    }

    private String getPoolType(ArtifactoryDbProperties dbProperties) {
        String poolTypeSysProp = System.getProperty("artifactory.db.config.pool.type");
        if (StringUtils.isNotBlank(poolTypeSysProp)) {
            return poolTypeSysProp;
        } else {
            return dbProperties.getProperty(ArtifactoryDbProperties.Key.POOL_TYPE, DEFAULT_POOL_TYPE);
        }
    }

    /**
     * Returns a separate non-transactional auto-commit datasource. This data source is currently used only by HA
     * locking.
     *
     * @return An auto-commit non-transactional datasource.
     */
    @Bean(name = "uniqueLockDataSource")
    public JFrogDataSource createUniqueLockDataSource() {
        ArtifactoryDbProperties dbProperties = beanFactory.getBean("dbProperties", ArtifactoryDbProperties.class);
        ArtifactoryTomcatDataSource dataSource = new ArtifactoryTomcatDataSource(dbProperties, false);
        // the db tests has limited tx support (which is mostly controlled by the business logic layer) so we are using
        // auto commit
        dataSource.setDefaultAutoCommit(true);
        return JFrogDataSourceWrapper.newInstance(dataSource, dbProperties);
    }

    @Bean(name = "dbProperties")
    public ArtifactoryDbProperties getDbProperties() {
        return ArtifactoryHome.get().getDBProperties();
    }

    @Bean(name = "dbConfigFactory")
    public DbConfigFactory dbConfigFactory() {
        return new DbConfigFactory();
    }

    /**
     * Returns the db props File using system property: rt.db.type
     */
    protected File getDbPropsFileByDbConfigName(String dbConfigName) {
        return ResourceUtils.getResourceAsFile("/db/" + dbConfigName + ".yaml");
    }

    @Bean
    public MetricRegistry getMetricRegistry() {
        return new MetricRegistry();
    }

    @Bean
    public MBeanRegistrationServiceImpl getMBeanRegistrationService() {
        return new MBeanRegistrationServiceImpl(getMetricRegistry());
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    private String getGeneratedDbConfigLocation() {
        String generatedConfig = System.getProperty("artifactory.db.generated.config");
        // reset the value to make sure it's not propagated to other tests
        System.clearProperty("artifactory.db.generated.config");
        return generatedConfig;
    }

    /**
     * create  a query builder instance per db type
     *
     * @return query builder instance
     */
    @Bean(name = "queryBuilder")
    public IQueryBuilder createSqlBuilder() throws SQLException {
        ArtifactoryJdbcHelper jdbcHelper = beanFactory.getBean(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties dbProperties = beanFactory.getBean(ArtifactoryDbProperties.class);
        DbType dbType = dbProperties.getDbType();
        Connection connection = jdbcHelper.getDataSource().getConnection();
        connection.close();
        return switch (dbType) {
            case ORACLE -> new OracleQueryBuilder();
            case MSSQL -> new SqlServerQueryBuilder();
            case POSTGRESQL -> new PostgresqlQueryBuilder();
            case MYSQL, MARIADB -> new MysqlQueryBuilder();
            default -> new DerbyQueryBuilder();
        };
    }

    // Normally, Artifactory creates a Prometheus registry, but for our tests it is enough to use
    // the SimpleMeterRegistry.
    @Bean
    @Primary
    public MeterRegistry metricsForDbTests() {
        return new SimpleMeterRegistry();
    }

    @Bean(name = "applicationMeterRegistry")
    public MeterRegistry applicationMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}
