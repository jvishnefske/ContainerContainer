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

package org.artifactory.storage.db.itest;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.joran.spi.JoranException;
import com.jfrog.test.LogTestUtils;
import com.jfrog.test.Randoms;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.DbServiceImpl;
import org.artifactory.storage.db.home.ArtifactoryUniqueFolderHomeBoundTest;
import org.artifactory.storage.db.init.ArtifactoryDataSource;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.event.EventsService;
import org.artifactory.test.TestUtils;
import org.jfrog.common.ResourceUtils;
import org.jfrog.config.ifc.ConfigurationManager;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.authentication.AuthenticationTokenProvider;
import org.jfrog.storage.metric.MetricsDataSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.SQLException;

import static ch.qos.logback.classic.ClassicConstants.CONFIG_FILE_PROPERTY;
import static org.artifactory.storage.db.itest.DbTestUtils.initAndCreateUniqueDbNamespaceIfNeeded;
import static org.artifactory.storage.db.itest.DbTestUtils.refreshOrRecreateSchema;
import static org.jfrog.storage.util.DbStatementUtils.executeSqlStream;
import static org.jfrog.storage.util.DbUtils.doStreamWithConnection;
import static org.jfrog.storage.util.DbUtils.doWithConnection;
import static org.testng.Assert.assertEquals;

/**
 * Base class for the low level database integration tests.
 *
 * @author Yossi Shaul
 */
@Test(groups = "dbtest")
@ContextConfiguration(locations = {"classpath:spring/db-test-context.xml"})
@Slf4j
public abstract class DbBaseTest extends AbstractTestNGSpringContextTests {

    static {
        LogTestUtils.suppressIllegalAccessWarning();
        URL url = DbBaseTest.class.getClassLoader().getResource("logback-dbtest.xml");
        if (url == null) {
            throw new RuntimeException("Could not find logback-dbtest.xml");
        }
        System.setProperty(CONFIG_FILE_PROPERTY, url.getPath());

        // Force Logback to reload configuration
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset(); // Clear existing configuration
        try {
            new ContextInitializer(context).autoConfig(); // Reload configuration
        } catch (JoranException e) {
            log.warn("Failed to reload logback configuration", e);
        }
    }

    @Autowired
    protected ArtifactoryJdbcHelper jdbcHelper;

    @Autowired
    protected DbServiceImpl dbService;

    @Autowired
    protected EventsService eventsService;

    @Autowired
    protected ArtifactoryDbProperties dbProperties;

    @Autowired
    protected AuthenticationTokenProvider authenticationTokenProvider;

    protected ArtifactoryUniqueFolderHomeBoundTest artifactoryHomeBoundTest;
    protected DummyArtifactoryContext dummyArtifactoryContext;
    protected ConfigurationManager configurationManager;

    @BeforeClass
    @Override
    protected void springTestContextPrepareTestInstance() throws Exception {
        LogTestUtils.setLoggingOff(ArtifactoryHome.class);
        artifactoryHomeBoundTest = createArtifactoryHomeTest();
        configurationManager = artifactoryHomeBoundTest.bindArtifactoryHome();
        artifactoryHomeBoundTest.setStringSystemProperty(
                ConstantValues.enableAqlOptimizationForFromLeftOuterJoinToSubSelect, "true");
        initAndCreateUniqueDbNamespaceIfNeeded();
        super.springTestContextPrepareTestInstance();
        log.info("Setting up test class " + this.getClass().getName() + " for DB of type: " +
                ArtifactoryHome.get().getDBProperties().getDbType());
        dummyArtifactoryContext = new DummyArtifactoryContext(applicationContext);
        doWithConnection(this.jdbcHelper, conn -> refreshOrRecreateSchema(LoggerFactory.getLogger(getClass()), conn,
                ArtifactoryHome.get().getDBProperties().getDbType()));
        TestUtils.invokeMethodNoArgs(dbService, "initializeIdGenerator");
        // This is pretty much the same as having the db conversion callback run, since the dbService is
        // not initiated across multiple runs of this test's inheritors i'm forced to do it like this.
        TestUtils.invokeMethodNoArgs(dbService, "verifySha256State");
    }

    @AfterClass
    protected void verifyDbResourcesReleased() throws SQLException {
        // make sure there are no active connections
        ArtifactoryDataSource ds;
        JFrogDataSource jFrogDataSource = jdbcHelper.getDataSource();
        if (jFrogDataSource.isWrapperFor(MetricsDataSource.class)) {
            ds = jFrogDataSource.unwrap(MetricsDataSource.class).unwrap(ArtifactoryDataSource.class);
        } else {
            ds = jFrogDataSource.unwrap(ArtifactoryDataSource.class);
        }
        assertEquals(ds.getActiveConnectionsCount(), 0,
                "Found " + ds.getActiveConnectionsCount() + " active connections after test ended");

        artifactoryHomeBoundTest.unbindArtifactoryHome();
    }

    protected void addBean(Object bean, Class<?>... types) {
        dummyArtifactoryContext.addBean(bean, types);
    }

    protected ArtifactoryUniqueFolderHomeBoundTest createArtifactoryHomeTest() throws IOException {
        return new ArtifactoryUniqueFolderHomeBoundTest(UniqueJvmForkTestIdentifier.get());
    }

    protected String randomSha1() {
        return Randoms.randomSha1();
    }

    protected String randomSha2() {
        return Randoms.randomSha256();
    }

    @BeforeMethod
    public void bindArtifactoryHome() {
        artifactoryHomeBoundTest.bindArtifactoryHome();
    }

    @AfterMethod
    public void unbindArtifactoryHome() {
        artifactoryHomeBoundTest.unbindArtifactoryHome();
    }

    @AfterClass
    public void cleanEnv() {
        artifactoryHomeBoundTest.cleanEnv();
    }

    protected void importSql(String resourcePath, boolean shiftEvents) {
        try (InputStream resource = ResourceUtils.getResource(resourcePath)) {
            doStreamWithConnection(jdbcHelper, conn -> executeSqlStream(conn, resource));
            // update the id generator
            TestUtils.invokeMethodNoArgs(dbService, "initializeIdGenerator");
            if (shiftEvents) {
                // Move the events from node_events_tmp to node_events
                eventsService.shiftEvents();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void importSql(String resourcePath) {
        importSql(resourcePath, true);
    }

    @BeforeMethod
    public void bindDummyContext() {
        ArtifactoryContextThreadBinder.bind(dummyArtifactoryContext);
    }

    @AfterMethod
    public void unbindDummyContext() {
        ArtifactoryContextThreadBinder.unbind();
    }

    protected boolean isOracleDb() {
        return DbType.ORACLE.equals(dbProperties.getDbType());
    }
}
