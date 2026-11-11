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

package org.artifactory.storage.db.spring;

import com.codahale.metrics.MetricRegistry;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.init.CommonDataSourceFactory;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.querybuilder.ArtifactorySqlServerQueryBuilder;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.util.querybuilder.DerbyQueryBuilder;
import org.jfrog.storage.util.querybuilder.IQueryBuilder;
import org.jfrog.storage.util.querybuilder.MysqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.OracleQueryBuilder;
import org.jfrog.storage.util.querybuilder.PostgresqlQueryBuilder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jndi.JndiObjectFactoryBean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.StringUtils;

import javax.naming.NamingException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * A Spring {@link org.springframework.context.annotation.Configuration} to initialized database beans.
 *
 * @author Yossi Shaul
 */
@Configuration
@EnableScheduling
public class DbConfigFactory implements BeanFactoryAware {

    private static final String BEAN_PREFIX = "bean:";
    private static final String JNDI_PREFIX = "jndi:";
    private BeanFactory beanFactory;

    @Bean(name = "dataSource")
    public JFrogDataSource createDataSource() {
        ArtifactoryDbProperties dbProperties = beanFactory.getBean("dbProperties", ArtifactoryDbProperties.class);
        JFrogDataSource result = getDataSourceFromBeanOrJndi(dbProperties, "");
        return Objects.requireNonNullElseGet(
                result, () -> CommonDataSourceFactory.createDataSource(dbProperties, false));
    }

    /**
     * Returns a separate non-transactional auto-commit datasource. This data source is currently used only by the id
     * generator.
     *
     * @return An auto-commit non-transactional datasource.
     */
    @Bean(name = "uniqueIdsDataSource")
    public JFrogDataSource createUniqueIdsDataSource() {
        ArtifactoryDbProperties dbProperties = beanFactory.getBean(ArtifactoryDbProperties.class);
        JFrogDataSource result = getDataSourceFromBeanOrJndi(dbProperties, "noTX");
        return Objects.requireNonNullElseGet(result,
                () -> CommonDataSourceFactory.createUniqueDataSourceByType(dbProperties));
    }

    /**
     * Returns a separate non-transactional auto-commit datasource. This data source is currently used only by HA
     * locking.
     *
     * @return An auto-commit non-transactional datasource.
     */
    @Bean(name = "uniqueLockDataSource")
    public JFrogDataSource createUniqueLockDataSource() {
        ArtifactoryDbProperties dbProperties = beanFactory.getBean(ArtifactoryDbProperties.class);
        JFrogDataSource result = getDataSourceFromBeanOrJndi(dbProperties, "locks");
        return Objects.requireNonNullElseGet(result, () ->
                CommonDataSourceFactory.createDifferentDatasourceForDbLock(dbProperties, false));
    }

    @Bean(name = "dbProperties")
    public ArtifactoryDbProperties getDbProperties() {
        ArtifactoryHome artifactoryHome = ContextHelper.get().getArtifactoryHome();
        ArtifactoryDbProperties dbProperties = artifactoryHome.getDBProperties();
        // first for loading of the driver class. automatic registration doesn't work on some Tomcat installations
        String driver = dbProperties.getDriverClass();
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load JDBC driver '" + driver + "'", e);
        }
        return dbProperties;
    }

    @Bean
    public MetricRegistry getMetricRegistry() {
        return new MetricRegistry();
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
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
            case MSSQL -> new ArtifactorySqlServerQueryBuilder();
            case POSTGRESQL -> new PostgresqlQueryBuilder();
            case MYSQL -> new MysqlQueryBuilder();
            // MariaDB is treated as MySql
            case MARIADB -> new MysqlQueryBuilder();
            default -> new DerbyQueryBuilder();
        };
    }

    private JFrogDataSource getDataSourceFromBeanOrJndi(ArtifactoryDbProperties dbProperties, String suffix) {
        JFrogDataSource result = null;
        String connectionUrl = dbProperties.getConnectionUrl();
        if (StringUtils.startsWithIgnoreCase(connectionUrl, BEAN_PREFIX)) {
            result = beanFactory.getBean(connectionUrl.substring(BEAN_PREFIX.length()) + suffix, JFrogDataSource.class);
        } else if (StringUtils.startsWithIgnoreCase(connectionUrl, JNDI_PREFIX)) {
            String jndiName = connectionUrl.substring(JNDI_PREFIX.length());
            JndiObjectFactoryBean jndiObjectFactoryBean = new JndiObjectFactoryBean();
            jndiObjectFactoryBean.setJndiName(jndiName + suffix);
            try {
                jndiObjectFactoryBean.afterPropertiesSet();
            } catch (NamingException e) {
                throw new IllegalStateException(e);
            }
            result = (JFrogDataSource) jndiObjectFactoryBean.getObject();
        }
        return result;
    }
}
