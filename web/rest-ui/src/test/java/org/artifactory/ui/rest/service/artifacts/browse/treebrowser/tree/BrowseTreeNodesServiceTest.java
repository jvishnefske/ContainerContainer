package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tree;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nonnull;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.SneakyThrows;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.artifactory.api.jackson.JacksonReader;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.JunctionNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.archive.ArchiveEntriesTree;
import org.artifactory.ui.utils.MockArtifactoryArchiveEntriesTree;
import org.artifactory.util.SevenZInputStreamWrapper;
import org.jfrog.common.ResourceUtils;
import org.jfrog.commons.continues.ContinueResult;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.common.ConstantValues.lifecycleManifestVisible;
import static org.artifactory.repo.RepoDetailsType.FEDERATED_REPO;
import static org.artifactory.repo.RepoDetailsType.LOCAL_REPO;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_NAVIGATION_ENABLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class BrowseTreeNodesServiceTest extends MockArtifactoryArchiveEntriesTree {

    private static final String ZIP_FILE_NAME = "test.zip";
    private static final String GZIP_FILE_NAME = "Test.tar.gz";
    private static final String Z7_FILE_NAME = "Test.7z";
    private static final String TEST_ROOT = "/browse/treebrowser/";
    private static final String TEST_ZIP = TEST_ROOT + ZIP_FILE_NAME;
    private static final String TEST_GZIP = TEST_ROOT + GZIP_FILE_NAME;
    private static final String TEST_7ZIP = TEST_ROOT + Z7_FILE_NAME;

    @Test
    public void testExecuteAsArchive() throws IOException {
        File archive = ResourceUtils.getResourceAsFile(TEST_ZIP);
        prepareMock(ZIP_FILE_NAME, LOCAL_REPO);
        InputStream ARCHIVE_STREAM = new FileInputStream(archive);
        when(repoService.archiveInputStream(any())).thenReturn(new ZipArchiveInputStream(ARCHIVE_STREAM));
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        BrowseTreeNodesService browseTreeNodesService = new BrowseTreeNodesService();
        assertTrue(browseTreeNodesService.isArchiveRequest(artifactoryRestRequest));
        browseTreeNodesService.execute(artifactoryRestRequest, response);
        assertTrue(response.getIModel() instanceof StreamingOutput);
        assertEquals(response.buildResponse().getStatus(), SC_OK);
    }

    @Test
    public void testExecuteArchiveRequestForFederatedRepo() throws IOException {
        File archive = ResourceUtils.getResourceAsFile(TEST_ZIP);
        prepareMock(ZIP_FILE_NAME, FEDERATED_REPO);
        InputStream ARCHIVE_STREAM = new FileInputStream(archive);
        when(repoService.archiveInputStream(any())).thenReturn(new ZipArchiveInputStream(ARCHIVE_STREAM));
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        BrowseTreeNodesService browseTreeNodesService = new BrowseTreeNodesService();
        assertTrue(browseTreeNodesService.isArchiveRequest(artifactoryRestRequest));
        browseTreeNodesService.execute(artifactoryRestRequest, response);
        assertTrue(response.getIModel() instanceof StreamingOutput);
        assertEquals(response.buildResponse().getStatus(), SC_OK);
    }

    @Test
    public void testExecuteNonArchiveRequest() {
        LocalRepoDescriptor repoDescriptor = new LocalRepoDescriptor();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        when(context.beanForType(AuthorizationService.class)).thenReturn(authorizationService);
        when(authorizationService.canManage(any(RepoPath.class))).thenReturn(true);
        repoDescriptor.setType(RepoType.Generic);
        prepareMock("test.txt", LOCAL_REPO);
        when(context.getRepositoryService()).thenReturn(repoService);
        when(repoService.repoDescriptorByKey(any())).thenReturn(repoDescriptor);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        BrowseTreeNodesService browseTreeNodesService = new BrowseTreeNodesService();
        ArtifactoryHome home = ArtifactoryHome.get();
        ArtifactorySystemProperties properties = Mockito.mock(ArtifactorySystemProperties.class);
        when(properties.getBooleanProperty(lifecycleManifestVisible)).thenReturn(false);
        when(home.getArtifactoryProperties()).thenReturn(properties);
        try (MockedStatic<ArtifactoryHome> artifactoryHome = mockStatic(ArtifactoryHome.class)) {
            artifactoryHome.when(ArtifactoryHome::get).thenReturn(home);
            assertFalse(browseTreeNodesService.isArchiveRequest(artifactoryRestRequest));
            browseTreeNodesService.execute(artifactoryRestRequest, response);
            assertTrue(response.getIModel() instanceof ContinueResult);
            assertEquals(response.buildResponse().getStatus(), SC_OK);
        }
    }

    private void testArchiveExtracted(String archiveFilePath, String archiveFileName,
            Class<? extends ArchiveInputStream> archiveInputStreamClass)
            throws IOException, NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {

        when(context.getArtifactoryHome()).thenReturn(artifactoryHomeMock);

        ArchiveInputStream archiveInputStream = createArchiveInputStream(archiveInputStreamClass, archiveFilePath);
        when(repoService.archiveInputStream(any())).thenReturn(archiveInputStream);

        BrowseTreeNodesService browseTreeNodesService = new BrowseTreeNodesService();
        ContinueResult<? extends RestModel> continueResult =
                new ArchiveEntriesTree().buildChildren("test", archiveFileName);

        OutputStream output = getOutputStream();
        browseTreeNodesService.write(continueResult, output);
        String streamResult = output.toString();

        JsonNode jsonNode = JacksonReader.bytesAsTree(streamResult.getBytes());

        assertEquals(jsonNode.size(), 2);
        JsonNode data = jsonNode.get("data");
        assertEquals(data.size(), 4);
        assertEquals(data.get(0).get("children").size(), 2);
        assertTrue(data.get(0).get("folder").asBoolean());
    }

    @Nonnull
    private ArchiveInputStream createArchiveInputStream(Class<? extends ArchiveInputStream> archiveInputStreamClass,
            String archiveFilePath)
            throws NoSuchMethodException, InstantiationException,
            IllegalAccessException, InvocationTargetException, IOException {
        File archive = ResourceUtils.getResourceAsFile(archiveFilePath);

        InputStream ARCHIVE_STREAM = new FileInputStream(archive);
        Constructor<? extends ArchiveInputStream> ctor = archiveInputStreamClass.getConstructor(InputStream.class);
        return archiveInputStreamClass.equals(TarArchiveInputStream.class) ?
                ctor.newInstance(new GzipCompressorInputStream(ARCHIVE_STREAM)) : ctor.newInstance(ARCHIVE_STREAM);
    }

    @Nonnull
    private OutputStream getOutputStream() {
        return new OutputStream() {
            private final StringBuilder string = new StringBuilder();

            @Override
            public void write(int x) {
                this.string.append((char) x);
            }

            public String toString() {
                return this.string.toString();
            }
        };
    }

    @SneakyThrows
    @Test(enabled = true)
    public void testGzipArchiveExtracted() {
        testArchiveExtracted(TEST_GZIP, GZIP_FILE_NAME, TarArchiveInputStream.class);
    }

    /**
     * ├── B2
     * │   ├── Hello.java
     * │   └── test.json
     * ├── JavaTest.java
     * ├── test.txt
     * └── test2.txt
     */
    @SneakyThrows
    @Test
    public void test7zArchiveExtracted() {
        testArchiveExtracted(TEST_7ZIP, Z7_FILE_NAME, SevenZInputStreamWrapper.class);
    }

    @SneakyThrows
    @Test
    public void testZipArchiveExtracted() {
        when(sysConfig.getBoolean(EVIDENCE_NAVIGATION_ENABLED, false)).thenReturn(false);
        testArchiveExtracted(TEST_ZIP, ZIP_FILE_NAME, ZipArchiveInputStream.class);
    }

    private void prepareMock(String fileName, String repoType) {
        ItemInfo fileInfo = new FileInfoImpl(new RepoPathImpl("test", fileName));
        JunctionNode junctionNode = new JunctionNode();
        junctionNode.setRepoKey("test");
        junctionNode.setRepoType(repoType);
        junctionNode.setPath(fileName);
        when(repoService.getItemInfo(any())).thenReturn(fileInfo);
        when(artifactoryRestRequest.getImodel()).thenReturn(junctionNode);
        Request request = Mockito.mock(Request.class);
        when(artifactoryRestRequest.getRequest()).thenReturn(request);
        when(request.getMethod()).thenReturn(HttpMethod.POST);
    }

}