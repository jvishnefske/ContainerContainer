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

package org.artifactory.test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import lombok.SneakyThrows;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.mime.MimeTypes;
import org.artifactory.mime.MimeTypesReader;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.jfrog.common.ResourceUtils;
import org.jfrog.config.ConfigurationManagerImpl;
import org.jfrog.config.ifc.ConfigurationManager;
import org.jfrog.config.ifc.ConfigurationManagerAdapter;
import org.jfrog.config.watch.FileWatcher;
import org.jfrog.security.common.KeyUtils;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.EncryptionWrapperFactory;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import jakarta.xml.bind.JAXBException;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter.META_INF_DEFAULT_PATH;
import static org.artifactory.common.home.ArtifactoryHome.MIME_TYPES_FILE_NAME;

/**
 * A convenience class for tests that require bind/unbind of {@link ArtifactoryHome} (usually for system
 * properties and mime types).
 *
 * @author Yossi Shaul
 */
@SuppressWarnings("squid:S2187")
public class ArtifactoryHomeBoundTest {
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryHomeBoundTest.class);
    private static final String ARTIFACTORY_PRODUCT_VERSION_PROPERTIES = "artifactory.product.version.properties";
    private static final String ARTIFACTORY_PRODUCT_VERSION_PROPERTIES_RESOURCE_PATH =
            "/config/system/valid.artifactory.product.version.properties";
    protected MimeTypes mimeTypes;
    protected ArtifactoryHomeStub homeStub;
    protected ConfigurationManager configurationManager;

    @BeforeClass
    public void readMimeTypes() throws JAXBException {
        // read and keep the default mime types
        InputStream mimeTypesFile = ResourceUtils.getResource(META_INF_DEFAULT_PATH + MIME_TYPES_FILE_NAME);
        mimeTypes = new MimeTypesReader().read(mimeTypesFile);
        getOrCreateArtifactoryHomeStub();
    }

    @BeforeMethod
    public ConfigurationManager bindArtifactoryHome() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        lc.getLogger(ArtifactoryHome.class.getName()).setLevel(Level.OFF);
        ArtifactoryHomeStub home = getOrCreateArtifactoryHomeStub();
        if (configurationManager == null) {
            ConfigurationManagerAdapter adapter = new MockConfigurationManagerAdapter(home);
            FileWatcher fileWatchingManager = Mockito.mock(FileWatcher.class);
            configurationManager = ConfigurationManagerImpl.create(adapter, fileWatchingManager);
        }
        home.initDBProperties();
        configurationManager.initDefaultFiles();
        home.initArtifactorySystemProperties();
        home.initMimeTypes();
        ArtifactoryHomeStub.bind(home);
        generateMasterKey();
        return configurationManager;
    }

    @SneakyThrows
    private void generateProductVersionFile() {
        String productVersionProperties =
                ResourceUtils.getResourceAsString(ARTIFACTORY_PRODUCT_VERSION_PROPERTIES_RESOURCE_PATH);
        Path productVersionPropsPath =
                Path.of(homeStub.getAppDir() + File.separator + ARTIFACTORY_PRODUCT_VERSION_PROPERTIES);
        Files.createDirectories(homeStub.getAppDir().toPath());
        Files.write(productVersionPropsPath, productVersionProperties.getBytes());
    }

    private void generateMasterKey() {
        EncryptionWrapper aesKeyFile = EncryptionWrapperFactory.createAesKeyFile();
        KeyUtils.saveKeyToFile(new File(ArtifactoryHome.get().getSysConfig().helper().getMasterKeyFile()), aesKeyFile);
    }

    public ArtifactoryHomeStub getOrCreateArtifactoryHomeStub() {
        if (homeStub == null) {
            String homeIdentifier = getHomeIdentifier();
            log.info("Artifactory home identifier for current test: " + homeIdentifier);
            homeStub = new ArtifactoryHomeStub(homeIdentifier);
            homeStub.setMimeTypes(mimeTypes);
            loadAndBindArtifactoryProperties(homeStub);
            generateProductVersionFile();
        }
        return homeStub;
    }

    private void loadAndBindArtifactoryProperties(ArtifactoryHomeStub artifactory) {
        artifactory.loadSystemProperties();
        artifactory.setProperty(ConstantValues.artifactoryServiceVersion,
                ArtifactoryServiceVersion.getCurrent().getVersion());
    }

    public void setStringSystemProperty(ConstantValues cv, String value) {
        homeStub.setProperty(cv, value);
    }

    @AfterMethod
    public void unbindArtifactoryHome() {
        ArtifactoryHomeStub.unbind();
    }

    @AfterClass
    public void cleanEnv() {
        if (configurationManager != null) {
            configurationManager.destroy();
        }
    }

    protected ArtifactoryHomeStub getBound() {
        return (ArtifactoryHomeStub) ArtifactoryHomeStub.get();
    }

    protected String getHomeIdentifier() {
        return getClass().getCanonicalName();
    }
}
