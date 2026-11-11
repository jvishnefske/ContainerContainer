package org.artifactory.rest.util;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryBrowsingService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.artifact.RestFileInfo;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class StorageInfoHelperTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private HttpServletRequest request;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RepositoryBrowsingService repoBrowsingService;
    @Mock
    private PropertiesService propertiesService;
    @Mock
    private ModuleInfo moduleInfo;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private CentralConfigDescriptor centralConfigDescriptor;
    private ItemInfo itemInfo;

    private static final String EXT = ".jar";
    private static final String CLASSIFIER = "sources";
    private static final String VERSION = "1.20.0";
    private static final String REPO_KEY = "repoKey";
    private static final String GROUP_ID = "groupId";
    private static final String ARTIFACT_ID = "artifactId";
    private static final String FOLDER_REV = "SNAPSHOT";
    private static final String BASE_URL = "localhost:8082/artifactory";
    private static final String REL_PATH = String.join("/", GROUP_ID, ARTIFACT_ID, VERSION + "-" + FOLDER_REV,
            ARTIFACT_ID + EXT);
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        this.itemInfo = mock(FileInfo.class);
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testStorageInfoSpecificSearch() {
        StorageInfoHelper storageInfoHelper = new StorageInfoHelper(request, repositoryService, propertiesService,
                repoBrowsingService, itemInfo, moduleInfo, true);

        when(centralConfigDescriptor.getUrlBase()).thenReturn(BASE_URL);
        when(moduleInfo.getExt()).thenReturn(EXT);
        when(moduleInfo.getClassifier()).thenReturn(CLASSIFIER);
        when(moduleInfo.getBaseRevision()).thenReturn(VERSION);
        when(itemInfo.getRepoKey()).thenReturn(REPO_KEY);
        when(itemInfo.getRelPath()).thenReturn(REL_PATH);
        when(request.getScheme()).thenReturn("http");

        RestFileInfo fileInfo = (RestFileInfo) storageInfoHelper.createStorageInfo();

        // should be included in the remote + virtual search
        assertEquals(EXT, fileInfo.ext);
        assertEquals(CLASSIFIER, fileInfo.classifier);
        assertEquals(VERSION, fileInfo.version);
        assertEquals("http://localhost:8082/artifactory/repoKey/groupId/artifactId/1.20.0-SNAPSHOT/artifactId.jar",
                fileInfo.downloadUri);

        // should not be included in the remote + virtual search
        assertNull(fileInfo.created);
        assertNull(fileInfo.createdBy);
        assertNull(fileInfo.modifiedBy);
        assertNull(fileInfo.lastUpdated);
        assertNull(fileInfo.lastModified);
        assertNull(fileInfo.size);
        assertNull(fileInfo.path);
        assertNull(fileInfo.repo);
        assertNull(fileInfo.properties);
        assertNull(fileInfo.mimeType);
        assertNull(fileInfo.remoteUrl);
        assertNull(fileInfo.checksums);
        assertNull(fileInfo.originalChecksums);
    }

    @Test
    void testStorageInfoNormalSearch() {
        StorageInfoHelper storageInfoHelper = new StorageInfoHelper(request, repositoryService, propertiesService,
                repoBrowsingService, itemInfo, moduleInfo, false);
        when(centralConfigDescriptor.getUrlBase()).thenReturn(BASE_URL);
        when(moduleInfo.getExt()).thenReturn(EXT);
        when(moduleInfo.getClassifier()).thenReturn(CLASSIFIER);
        when(moduleInfo.getBaseRevision()).thenReturn(VERSION);
        when(request.getHeader(ArtifactoryRequest.RESULT_DETAIL)).thenReturn("info");
        when(request.getScheme()).thenReturn("http");
        when(itemInfo.getRepoKey()).thenReturn(REPO_KEY);
        when(itemInfo.getRelPath()).thenReturn(REL_PATH);
        when(((FileInfo) itemInfo).getSize()).thenReturn(123456L);

        ChecksumsInfo checksumsInfo = mock(ChecksumsInfo.class);
        when(((FileInfo) itemInfo).getChecksumsInfo()).thenReturn(checksumsInfo);
        when(checksumsInfo.getChecksumInfo(ChecksumType.sha1)).thenReturn(mock(ChecksumInfo.class));
        when(checksumsInfo.getChecksumInfo(ChecksumType.md5)).thenReturn(mock(ChecksumInfo.class));
        when(checksumsInfo.getChecksumInfo(ChecksumType.sha256)).thenReturn(mock(ChecksumInfo.class));

        RestFileInfo fileInfo = (RestFileInfo) storageInfoHelper.createStorageInfo();

        assertEquals("http://localhost:8082/artifactory/repoKey/groupId/artifactId/1.20.0-SNAPSHOT/artifactId.jar",
                fileInfo.downloadUri);
        assertEquals(EXT, fileInfo.ext);
        assertEquals(CLASSIFIER, fileInfo.classifier);
        assertEquals(VERSION, fileInfo.version);
        assertEquals("123456", fileInfo.size);
        assertEquals("/groupId/artifactId/1.20.0-SNAPSHOT/artifactId.jar", fileInfo.path);
        assertEquals(REPO_KEY, fileInfo.repo);
        assertEquals("application/java-archive", fileInfo.mimeType);
        assertNotNull(fileInfo.created);
        assertNotNull(fileInfo.lastUpdated);
        assertNotNull(fileInfo.lastModified);
        assertNotNull(fileInfo.checksums);
        assertNotNull(fileInfo.originalChecksums);
    }

    @Test
    void testFileInfoVersionWhenFolderIntegrationRevNotNull() {
        StorageInfoHelper storageInfoHelper = new StorageInfoHelper(request, repositoryService, propertiesService,
                repoBrowsingService, itemInfo, moduleInfo, true);

        when(moduleInfo.getBaseRevision()).thenReturn(VERSION);
        when(moduleInfo.getFolderIntegrationRevision()).thenReturn(FOLDER_REV);

        RestFileInfo fileInfo = (RestFileInfo) storageInfoHelper.createStorageInfo();
        assertEquals("1.20.0-SNAPSHOT", fileInfo.version);
    }

    @Test
    void testFileInfoVersionWhenFolderIntegrationRevEmpty() {
        StorageInfoHelper storageInfoHelper = new StorageInfoHelper(request, repositoryService, propertiesService,
                repoBrowsingService, itemInfo, moduleInfo, true);

        when(moduleInfo.getBaseRevision()).thenReturn(VERSION);
        when(moduleInfo.getFolderIntegrationRevision()).thenReturn("");

        RestFileInfo fileInfo = (RestFileInfo) storageInfoHelper.createStorageInfo();
        assertEquals("1.20.0", fileInfo.version);
    }

    @Test
    void testFileInfoVersionWhenFolderIntegrationRevNull() {
        StorageInfoHelper storageInfoHelper = new StorageInfoHelper(request, repositoryService, propertiesService,
                repoBrowsingService, itemInfo, moduleInfo, true);

        when(moduleInfo.getBaseRevision()).thenReturn(VERSION);
        when(moduleInfo.getFolderIntegrationRevision()).thenReturn(null);

        RestFileInfo fileInfo = (RestFileInfo) storageInfoHelper.createStorageInfo();
        assertEquals("1.20.0", fileInfo.version);
    }
}