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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import jakarta.xml.bind.JAXBException;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import static org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter.META_INF_DEFAULT_PATH;
import static org.artifactory.common.home.ArtifactoryHome.MIME_TYPES_FILE_NAME;

/**
 * A convenience class for tests that require bind/unbind of {@link ArtifactoryHome}.
 * Note: this is the JUnit 5 version. For the TestNG version see {@link ArtifactoryHomeBoundTest}.
 * <p>
 * Each concrete test class gets its own {@link ArtifactoryHomeStub} and {@link ConfigurationManager} so parallel
 * Surefire (multiple classes per fork) does not share mutable static state.
 *
 * @author Yossi Shaul
 */
public class ArtifactoryHomeBoundJUnitTest {
    private static final String ARTIFACTORY_PRODUCT_VERSION_PROPERTIES = "artifactory.product.version.properties";
    private static final String ARTIFACTORY_PRODUCT_VERSION_PROPERTIES_RESOURCE_PATH =
            "/config/system/valid.artifactory.product.version.properties";

    private static final ConcurrentHashMap<Class<?>, HomeEnv> HOME_ENV_BY_TEST_CLASS = new ConcurrentHashMap<>();

    protected static volatile MimeTypes mimeTypes;
    /**
     * Set in {@link #bindArtifactoryHome()} to this test instance's stub (per concrete test class, not per JVM).
     */
    protected ArtifactoryHomeStub homeStub;

    @RegisterExtension
    static final AfterAllCallback HOME_ENV_CLEANUP = extensionContext -> {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        HomeEnv removed = HOME_ENV_BY_TEST_CLASS.remove(testClass);
        if (removed != null) {
            removed.configurationManager().destroy();
        }
    };

    @BeforeAll
    static void readMimeTypes() {
        ensureMimeTypesLoaded();
    }

    /**
     * Loads shared {@link #mimeTypes} once per JVM. {@link #createHomeEnv(Class)} calls this so
     * {@link #bindArtifactoryHome()} works when invoked without this class's {@code @BeforeAll} (e.g. helper
     * subclasses used from a test class that does not extend {@link ArtifactoryHomeBoundJUnitTest}).
     */
    @SneakyThrows(JAXBException.class)
    private static void ensureMimeTypesLoaded() {
        if (mimeTypes != null) {
            return;
        }
        synchronized (ArtifactoryHomeBoundJUnitTest.class) {
            if (mimeTypes != null) {
                return;
            }
            InputStream mimeTypesFile = ResourceUtils.getResource(META_INF_DEFAULT_PATH + MIME_TYPES_FILE_NAME);
            mimeTypes = new MimeTypesReader().read(mimeTypesFile);
        }
    }

    @BeforeEach
    void bindArtifactoryHome() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        lc.getLogger(ArtifactoryHome.class.getName()).setLevel(Level.OFF);
        HomeEnv env = getOrCreateHomeEnv();
        this.homeStub = env.stub();
        ArtifactoryHomeStub home = env.stub();
        ConfigurationManager cm = env.configurationManager();
        home.initDBProperties();
        cm.initDefaultFiles();
        home.initArtifactorySystemProperties();
        home.initMimeTypes();
        ArtifactoryHome.bind(home);
    }

    /**
     * Lazily creates one {@link ArtifactoryHomeStub} (and matching configuration manager) per concrete test class.
     */
    private HomeEnv getOrCreateHomeEnv() {
        return HOME_ENV_BY_TEST_CLASS.computeIfAbsent(getClass(), ArtifactoryHomeBoundJUnitTest::createHomeEnv);
    }

    private static HomeEnv createHomeEnv(Class<?> testClass) {
        ensureMimeTypesLoaded();
        String homeIdentifier = testClass.getCanonicalName();
        if (homeIdentifier == null) {
            homeIdentifier = testClass.getName();
        }
        ArtifactoryHomeStub stub = new ArtifactoryHomeStub(homeIdentifier);
        stub.setMimeTypes(mimeTypes);
        loadAndBindArtifactoryProperties(stub);
        generateProductVersionFile(stub);
        ConfigurationManagerAdapter adapter = new MockConfigurationManagerAdapter(stub);
        FileWatcher fileWatchingManager = Mockito.mock(FileWatcher.class);
        ConfigurationManager configurationManager = ConfigurationManagerImpl.create(adapter, fileWatchingManager);
        return new HomeEnv(stub, configurationManager);
    }

    public ArtifactoryHomeStub getOrCreateArtifactoryHomeStub() {
        return getOrCreateHomeEnv().stub();
    }

    @SneakyThrows
    private static void generateProductVersionFile(ArtifactoryHomeStub stub) {
        String productVersionProperties =
                ResourceUtils.getResourceAsString(ARTIFACTORY_PRODUCT_VERSION_PROPERTIES_RESOURCE_PATH);
        Path productVersionPropsPath =
                Path.of(stub.getAppDir() + File.separator + ARTIFACTORY_PRODUCT_VERSION_PROPERTIES);
        Files.createDirectories(stub.getAppDir().toPath());
        Files.write(productVersionPropsPath, productVersionProperties.getBytes());
    }

    private static void loadAndBindArtifactoryProperties(ArtifactoryHomeStub artifactory) {
        artifactory.loadSystemProperties();
        artifactory.setProperty(ConstantValues.artifactoryServiceVersion,
                ArtifactoryServiceVersion.getCurrent().getVersion());
    }

    /**
     * Sets a property on this test class's {@link ArtifactoryHomeStub}, not necessarily on
     * {@link ArtifactoryHome#get()} (some tests bind a mock {@link ArtifactoryHome} for code under test while
     * keeping a real stub for config).
     */
    public void setStringSystemProperty(ConstantValues cv, String value) {
        getOrCreateArtifactoryHomeStub().setProperty(cv, value);
    }

    public void setLongSystemProperty(ConstantValues cv, long value) {
        getOrCreateArtifactoryHomeStub().setProperty(cv, String.valueOf(value));
    }

    @AfterEach
    public void unbindArtifactoryHome() {
        ArtifactoryHome.unbind();
    }

    protected ArtifactoryHomeStub getBound() {
        return (ArtifactoryHomeStub) ArtifactoryHomeStub.get();
    }

    /**
     * Removes and tears down the lazily created home env for the given class (for tests that use helper subclasses).
     */
    static void clearHomeEnvironmentFor(Class<?> testClass) {
        HomeEnv removed = HOME_ENV_BY_TEST_CLASS.remove(testClass);
        if (removed != null) {
            removed.configurationManager().destroy();
        }
    }

    private record HomeEnv(ArtifactoryHomeStub stub, ConfigurationManager configurationManager) {
    }
}
