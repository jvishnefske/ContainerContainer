/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.common.config.db;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeStub;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.jfrog.config.home.JFrogHome.ETC_FOLDER;
import static org.jfrog.config.home.JFrogHome.SYSTEM_YAML_FILENAME;
import static org.jfrog.config.home.JFrogHome.VAR_FOLDER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the {@link ArtifactoryDbProperties} class.
 * Saving system.yaml file at it's location and validating that the props are loaded into the class properly.
 *
 * Using a pre-defined system.yaml files (resources folder)
 *
 * @author Yossi Shaul
 */
@Test
public class ArtifactoryDbPropertiesTest {

    private File homeDir;

    @BeforeClass
    public void setup() throws IOException {
        homeDir = Files.createTempDirectory(getClass().getSimpleName()).toFile();
    }

    @AfterEach
    public void tearDown() {
        ArtifactoryHome.unbind();
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = ".*Failed to init db.*")
    public void failUnsupportedDatabaseType() throws IOException {
        ArtifactoryHome.bind(new ArtifactoryHomeStub(getClass().getName()));
        ArtifactoryHome internalHome = getArtifactoryHomeWithYaml("/db/unsupported.yaml");
        new ArtifactoryDbProperties(internalHome);
    }

    public void minimalPropertiesFile() throws IOException {
        ArtifactoryHome.bind(new ArtifactoryHomeStub(getClass().getName()));
        ArtifactoryHome internalHome = getArtifactoryHomeWithYaml("/db/minimalstorage.yaml");
        ArtifactoryDbProperties sp = new ArtifactoryDbProperties(internalHome);

        assertEquals(sp.getDbType(), DbType.DERBY);
        assertEquals(sp.getConnectionUrl(), "jdbc:to:somewhere");
        assertEquals(sp.getDriverClass(), "some.driver");
        assertNull(sp.getUsername());
        assertNull(sp.getPassword());
        assertEquals(sp.getMaxActiveConnections(), ArtifactoryDbProperties.DEFAULT_MAX_ACTIVE_CONNECTIONS);
        assertEquals(sp.getMaxIdleConnections(), ArtifactoryDbProperties.DEFAULT_MAX_IDLE_CONNECTIONS);
    }

    public void nonStandardParams() throws IOException {
        ArtifactoryHome.bind(new ArtifactoryHomeStub(getClass().getName()));
        ArtifactoryHome internalHome = getArtifactoryHomeWithYaml("/db/nonstandard.yaml");
        ArtifactoryDbProperties sp = new ArtifactoryDbProperties(internalHome);

        assertEquals(sp.getProperty("nonStandardShared"), "from shared");
        assertEquals(sp.getProperty("nonStandardArti"), "from arti");

    }

    public void valuesWithSpaces() throws IOException {
        ArtifactoryHome.bind(new ArtifactoryHomeStub(getClass().getName()));
        ArtifactoryHome internalHome = getArtifactoryHomeWithYaml("/db/trim.yaml");
        ArtifactoryDbProperties sp = new ArtifactoryDbProperties(internalHome);

        assertEquals(sp.getDbType(), DbType.DERBY);
        assertEquals(sp.getConnectionUrl(), "jdbc:to:removespaces");
        assertEquals(sp.getDriverClass(), "some.driver");
        assertEquals(sp.getProperty("empty", ""), "");
        assertEquals(sp.getMaxActiveConnections(), 300);
        assertEquals(sp.getMaxIdleConnections(), 1337);

    }

    public void isPostgres() throws IOException {
        ArtifactoryHome internalHome = getArtifactoryHomeWithYaml("/db/postgres.yaml");
        ArtifactoryDbProperties sp = new ArtifactoryDbProperties(internalHome);

        assertTrue(sp.isPostgres());
        assertEquals(sp.getDbType(), DbType.POSTGRESQL);

    }

    public void withAllowPostgresFlag() throws IOException {
        ArtifactoryHome internalHome = getArtifactoryHomeWithYaml("/db/db_config_with_allow_pg_flag.yaml");
        ArtifactoryDbProperties sp = new ArtifactoryDbProperties(internalHome);

        assertTrue(sp.allowNonPgFlag().isPresent());
    }

    public void withoutAllowPostgresFlag() throws IOException {
        ArtifactoryHome internalHome = getArtifactoryHomeWithYaml("/db/db_config_without_allow_pg_flag.yaml");
        ArtifactoryDbProperties sp = new ArtifactoryDbProperties(internalHome);

        assertTrue(sp.allowNonPgFlag().isPresent());
        assertFalse(sp.allowNonPgFlag().get());
    }


    private ArtifactoryHome getArtifactoryHomeWithYaml(String yamlPath) throws IOException {
        Path systemYamlPath = Paths.get(homeDir.getAbsolutePath(), VAR_FOLDER, ETC_FOLDER, SYSTEM_YAML_FILENAME);
        File systemYamlFile = systemYamlPath.toFile();
        // Clean the env before copy the desired yaml
        systemYamlFile.mkdirs();
        systemYamlFile.delete();
        // copy the resource to the .yaml location
        Files.copy(this.getClass().getResourceAsStream(yamlPath), systemYamlPath);
        return new ArtifactoryHome(homeDir.getAbsolutePath());
    }


}
