package org.artifactory.storage.db.fs.service;

import jakarta.annotation.Nonnull;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.md.Properties;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.storage.EmojiNotAllowedInDbException;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.dao.PropertiesDao;
import org.artifactory.storage.db.fs.entity.NodeProperty;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.service.NodeMetaInfoService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.properties.PropertiesUtil.calcPropertiesDiff;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Tamar Rosen
 */
class DbPropertiesServiceImplTest extends ArtifactoryHomeBoundJUnitTest {
    private static final long NODE_ID = 1L;
    String REPO_KEY = "repoKey";
    String PATH = "path";
    RepoPath REPO_PATH = RepoPathFactory.create(REPO_KEY, PATH);
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    FileService fileService;
    @Mock
    NodeMetaInfoService nodeMetaInfoService;
    @Mock
    CacheExpiry cacheExpiry;
    @Mock
    PropertiesDao propertiesDao;
    @Mock
    NodesDao nodesDao;
    @Mock
    PropertiesService propertiesService;
    @Mock
    DbService dbService;
    @Mock
    SecurityService securityService;
    @Mock
    ArtifactorySystemProperties artifactProperties;
    @InjectMocks
    DbPropertiesServiceImpl dbPropertiesService = new DbPropertiesServiceImpl();
    private AutoCloseable autoCloseable;


    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactProperties);
        ArtifactoryHome.bind(artifactoryHome);
        when(artifactoryContext.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(artifactoryContext.beanForType(CacheExpiry.class)).thenReturn(cacheExpiry);
        when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(securityService);
    }

    @AfterMethod
    public void teardown() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @ParameterizedTest
    @MethodSource("provideProperties")
    void testsUpdateNodeMetaInfoIfNeeded(PropertiesImpl newProperties, PropertiesImpl oldProperties, boolean userProps,
            boolean systemProps, boolean replacePropsMode, boolean shouldUpdateNodeMetaInfo,
            boolean shouldDeleteNodeMetaInfo, boolean shouldBeModified)
            throws SQLException {
        when(fileService.getNodeId(REPO_PATH)).thenReturn(NODE_ID);
        when(cacheExpiry.shouldExcludePropertyFromMirroring(REPO_KEY, "prop1")).thenReturn(!userProps);
        when(cacheExpiry.shouldExcludePropertyFromMirroring(REPO_KEY, "prop2")).thenReturn(systemProps);
        when(propertiesDao.deleteNodeProperties(NODE_ID)).thenReturn(oldProperties == null ? 0 : oldProperties.size());
        when(artifactProperties.getBooleanProperty(ConstantValues.nodePropertiesReplaceAll)).thenReturn(
                replacePropsMode);

        doAnswer((invocation) -> calcPropertiesDiff(newProperties, oldProperties)).when(
                propertiesService).calcPropertiesDiff(newProperties, REPO_PATH);

        boolean modified = dbPropertiesService.setProperties(REPO_PATH, newProperties);

        assertEquals(shouldBeModified, modified);
        verify(nodeMetaInfoService, times(shouldUpdateNodeMetaInfo ? 1 : 0)).createOrUpdateNodeMetaInfo(eq(NODE_ID),
                any());
        verify(nodeMetaInfoService, times(shouldDeleteNodeMetaInfo ? 1 : 0)).deleteMetaInfo(eq(NODE_ID));
    }

    @Test
    void testsSetPropertiesNegativeNodeId() {
        when(fileService.getNodeId(REPO_PATH)).thenReturn(-1L);
        boolean modified = dbPropertiesService.setProperties(REPO_PATH, new PropertiesImpl());
        Assertions.assertFalse(modified);
    }

    @Test
    void testsGetPropertiesZeroNodeId() {
        when(fileService.getNodeId(REPO_PATH)).thenReturn(0L);
        Properties properties = dbPropertiesService.getProperties(REPO_PATH);
        assertTrue(properties.isEmpty());
    }

    @Test
    void throwsEmojiNotAllowedInDbExceptionWhenSQLExceptionMessageContainsEmoji() throws Exception {
        Properties properties = new PropertiesImpl();
        SQLException sqlException = new SQLException("Error: invalid character 😊");
        when(fileService.getNodeId(REPO_PATH)).thenReturn(NODE_ID);
        when(propertiesDao.getNodeProperties(NODE_ID)).thenReturn(List.of());
        // Simulate the DAO throwing on createBulk
        doAnswer(invocation -> {throw sqlException;}).when(propertiesDao).createBulk(any());

        assertThrows(EmojiNotAllowedInDbException.class,
                () -> dbPropertiesService.setProperties(REPO_PATH, properties));
    }

    public static Stream<Arguments> provideProperties() {
        PropertiesImpl newProps = createProps(true);
        PropertiesImpl existingProps = createProps(false);
        PropertiesImpl emptyProps = new PropertiesImpl();
        return Stream.of(
                // *** replace props ***
                //      empty props
                Arguments.of(emptyProps, emptyProps, true, true, true, false, false, false),
                //      null props
                Arguments.of(null, null, true, true, true, false, false, false),
                //      create user prop
                Arguments.of(newProps, emptyProps, true, false, true, true, false, true),
                //      create system prop
                Arguments.of(newProps, emptyProps, false, true, true, false, false, true),
                //      create both
                Arguments.of(newProps, emptyProps, true, true, true, true, false, true),
                //      delete user prop
                Arguments.of(emptyProps, newProps, true, false, true, false, true, true),
                //      delete system prop
                Arguments.of(emptyProps, newProps, false, true, false, false, false, true),
                //      delete both
                Arguments.of(emptyProps, newProps, true, true, true, false, true, true),
                //      create same user properties
                Arguments.of(newProps, newProps, true, false, true, true, false, true),
                //      create same system properties
                Arguments.of(newProps, newProps, false, true, true, false, false, true),
                // *** update props ***
                //      create user prop
                Arguments.of(newProps, emptyProps, true, false, false, true, false, true),
                //      create system prop
                Arguments.of(newProps, emptyProps, false, true, false, false, false, true),
                //      create both
                Arguments.of(newProps, emptyProps, true, true, false, true, false, true),
                //      delete user prop
                Arguments.of(emptyProps, newProps, true, false, false, false, true, true),
                //      delete system prop
                Arguments.of(emptyProps, newProps, false, true, false, false, false, true),
                //      delete both
                Arguments.of(emptyProps, newProps, true, true, false, false, true, true),
                //      create same user properties
                Arguments.of(existingProps, existingProps, true, false, false, false, false, false),
                //      create same system properties
                Arguments.of(existingProps, existingProps, false, true, false, false, false, false)
        );
    }

    @Nonnull
    private static PropertiesImpl createProps(boolean newProps) {
        PropertiesImpl props = new PropertiesImpl(NODE_ID, List.of());
        props.put("prop1", "value1");
        props.put("prop2", "value2");
        if (newProps) {
            return props;
        } else {
            return new PropertiesImpl(NODE_ID, List.of(new NodeProperty(1l, NODE_ID, "prop1", "value1"),
                    new NodeProperty(2L, NODE_ID, "prop2", "value2")));
        }
    }

    @Test
    void testGetPropertiesForFiles_success() throws SQLException {
        RepoPath folderPath = RepoPathFactory.create("docker-local", "library/nginx");
        List<String> fileNames = List.of("manifest.json", "list.manifest.json");

        Map<String, List<NodeProperty>> mockResult = new HashMap<>();
        List<NodeProperty> fileProperties = new ArrayList<>();
        fileProperties.add(new NodeProperty(1L, 100L, "docker.manifest", "v2"));
        fileProperties.add(
                new NodeProperty(2L, 100L, "docker.mediaType", "application/vnd.docker.distribution.manifest.v2+json"));
        mockResult.put("manifest.json", fileProperties);

        when(nodesDao.getPropertiesForFiles(folderPath, fileNames)).thenReturn(mockResult);

        Map<String, Properties> result = dbPropertiesService.getPropertiesForFiles(folderPath, fileNames);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("manifest.json"));
        Properties properties = result.get("manifest.json");
        assertEquals("v2", properties.getFirst("docker.manifest"));
        assertEquals("application/vnd.docker.distribution.manifest.v2+json", properties.getFirst("docker.mediaType"));
    }

    @Test
    void testGetPropertiesForFiles_emptyResult() throws SQLException {
        RepoPath folderPath = RepoPathFactory.create("docker-local", "library/nginx");
        List<String> fileNames = List.of("manifest.json");

        when(nodesDao.getPropertiesForFiles(folderPath, fileNames)).thenReturn(new HashMap<>());

        Map<String, Properties> result = dbPropertiesService.getPropertiesForFiles(folderPath, fileNames);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetPropertiesForFiles_sqlException() throws SQLException {
        RepoPath folderPath = RepoPathFactory.create("docker-local", "library/nginx");
        List<String> fileNames = List.of("manifest.json");

        when(nodesDao.getPropertiesForFiles(folderPath, fileNames)).thenThrow(new SQLException("Database error"));

        assertThrows(StorageException.class, () -> dbPropertiesService.getPropertiesForFiles(folderPath, fileNames));
    }
}