package org.artifactory.storage.db.spring;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.jfrog.storage.DbType;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.BeanFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;
import java.util.Properties;

import static org.artifactory.common.config.db.ArtifactoryDbProperties.DEFAULT_POOL_TYPE;
import static org.artifactory.common.config.db.ArtifactoryDbProperties.HIKARI_POOL_TYPE;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Shay Bagants
 */
@Test
public class DbConfigFactoryTest {

    private DbConfigFactory factory = new DbConfigFactory();

    @Mock
    private BeanFactory beanFactory;
    @Mock
    private ArtifactoryDbProperties dbProperties;
    private AutoCloseable mocks;

    @BeforeMethod
    private void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        factory.setBeanFactory(beanFactory);
        when(dbProperties.getProperty(eq("connectivityCheckTimeoutMs"), anyString()))
                .thenReturn("5000");
        when(dbProperties.getDriverProperties()).thenReturn(new Properties());
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    public void testCreateUniqueLockDataSource() {
        when(beanFactory.getBean(ArtifactoryDbProperties.class)).thenReturn(dbProperties);
        when(dbProperties.getProperty(ArtifactoryDbProperties.Key.POOL_TYPE, DEFAULT_POOL_TYPE))
                .thenReturn(DEFAULT_POOL_TYPE);
        when(dbProperties.getConnectionUrl()).thenReturn("jdbc:oracle:thin:@localhost:1521/FREEPDB1");
        when(dbProperties.getDriverClass()).thenReturn("oracle.jdbc.OracleDriver");
        when(dbProperties.getUsername()).thenReturn("orcluser");
        when(dbProperties.getPassword()).thenReturn("orclpass");
        when(dbProperties.getLockingDbSpecificType()).thenReturn(Optional.empty());
        when(dbProperties.getDbType()).thenReturn(DbType.ORACLE);
        when(dbProperties.getProperty("validationQuery", "SELECT 1 FROM DUAL")).thenReturn("query");
        when(dbProperties.getIntProperty("validationQueryTimeout", 5000)).thenReturn(5000);
        when(dbProperties.getMaxActiveConnections()).thenReturn(50);
        factory.createUniqueLockDataSource();
        verify(dbProperties, times(2)).getProperty(ArtifactoryDbProperties.Key.POOL_TYPE, DEFAULT_POOL_TYPE);
        verify(dbProperties, times(2)).getConnectionUrl();
        verify(dbProperties, times(1)).getDriverClass();
        verify(dbProperties, times(1)).getUsername();
        verify(dbProperties, times(1)).getPassword();
    }

    public void testCreateUniqueLockDataSourceOnDedicatedDb() {
        testUniqueLockDatasourceOnDifferentDb();
    }

    public void testCreateUniqueLockDataSourceOnDedicatedDbWhileMainDbIsHikari() {
        when(dbProperties.getProperty(ArtifactoryDbProperties.Key.POOL_TYPE))
                .thenReturn(HIKARI_POOL_TYPE);
        testUniqueLockDatasourceOnDifferentDb();
    }

    private void testUniqueLockDatasourceOnDifferentDb() {
        when(beanFactory.getBean(ArtifactoryDbProperties.class)).thenReturn(dbProperties);
        when(dbProperties.getProperty(ArtifactoryDbProperties.Key.POOL_TYPE, DEFAULT_POOL_TYPE))
                .thenReturn(DEFAULT_POOL_TYPE);
        when(dbProperties.getLockingDbConnectionUrl()).thenReturn("jdbc:oracle:thin:@localhost:1521/FREEPDB1");
        when(dbProperties.getDriverClass()).thenReturn("oracle.jdbc.OracleDriver");
        when(dbProperties.getLockingDbDriverClass()).thenReturn("oracle.jdbc.OracleDriver");
        when(dbProperties.getLockingDbUsername()).thenReturn("orcluser");
        when(dbProperties.getLockingDbPassword()).thenReturn("orclpass");
        when(dbProperties.getLockingDbSpecificType()).thenReturn(Optional.of(DbType.ORACLE));
        when(dbProperties.getProperty("lockingdb.validationQuery", "SELECT 1 FROM DUAL")).thenReturn("query");
        when(dbProperties.getMaxActiveConnections()).thenReturn(20);
        when(dbProperties.getIntProperty("validationQueryTimeout", 5000)).thenReturn(5000);
        factory.createUniqueLockDataSource();
        verify(dbProperties, times(1)).getLockingDbConnectionUrl();
        verify(dbProperties, times(1)).getLockingDbDriverClass();
        verify(dbProperties, times(1)).getLockingDbUsername();
        verify(dbProperties, times(1)).getLockingDbPassword();
    }
}