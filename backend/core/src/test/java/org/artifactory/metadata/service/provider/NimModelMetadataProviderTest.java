package org.artifactory.metadata.service.provider;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Set;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Einas Ashkar
 */
public class NimModelMetadataProviderTest {
    @Mock
    private RepoPath repoPath;
    private NimModelMetadataProvider metadataProvider;
    private NimModelMetadataProvider.NimModelTypeSpecificMetadataTemplate metadataTemplate;
    private AutoCloseable closeable;
    String NIM_MODEL_NAME_ID = "nimmodel.name";

    @BeforeEach
    void setUp() {
        metadataProvider = new NimModelMetadataProvider();
        metadataTemplate = metadataProvider.new NimModelTypeSpecificMetadataTemplate();
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void isLeadArtifact() {
        when(repoPath.getPath()).thenReturn("nimmodel");
        assertFalse(metadataProvider.isLeadArtifact(repoPath));

        when(repoPath.getPath()).thenReturn(".jfrog_nim_model_info.json");
        assertTrue(metadataProvider.isLeadArtifact(repoPath));

        when(repoPath.getPath()).thenReturn(".jfrog_nim_model_info.txt");
        assertFalse(metadataProvider.isLeadArtifact(repoPath));
    }

    @ParameterizedTest
    @MethodSource("pathParams")
    void isPackageConsumptionLead(String path, boolean expectedResult) {
        RepoPath repoPath = Mockito.mock(RepoPath.class);
        when(repoPath.getPath()).thenReturn(path);
        assertEquals(expectedResult, metadataProvider.isPackageConsumptionLead(repoPath));
    }

    public static Stream<Arguments> pathParams() {
        return Stream.of(
                Arguments.of("hellow.json", false),
                Arguments.of(NimModelMetadataProvider.MODEL_LEAD_FILE_NAME, true));
    }

    @Test
    void getExcludedProps() {
        final Set<String> excludedProps = metadataTemplate.getExcludedUserProperties();
        assertEquals(4, excludedProps.size());
        assertTrue(excludedProps.contains("nimmodel.versionId"));
        assertTrue(excludedProps.contains("nimmodel.shortDescription"));
        assertTrue(excludedProps.contains("nimmodel.labels"));
        assertTrue(excludedProps.contains("nimmodel.name"));
    }

    @Test
    void getTypTest() {
        Properties prop = new PropertiesImpl();
        assertEquals(RepoType.NimModel.getType(), metadataProvider.getType(prop));
    }

    @Test
    void pkgIdResolverTest() {
        MutableMetadataEntityBOM mutableMetadataEntityBOM = new MutableMetadataEntityBOM();
        Properties prop = new PropertiesImpl();
        String modelName = "llama3-8b-instruct";
        prop.put(NIM_MODEL_NAME_ID, modelName);
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        MetadataResolver metadataResolver = metadataProvider.pkgIdResolver();
        metadataResolver.apply(mutableMetadataEntityBOM, prop, fileInfo, null);
        assertEquals("nimmodel://llama3-8b-instruct", mutableMetadataEntityBOM.getPkgid());
    }

    @Test
    void getPkgidSuffix() {
        when(repoPath.getPath()).thenReturn("nimmodel");
        Properties properties = new PropertiesImpl();
        assertEquals(EMPTY, metadataTemplate.getPkgidSuffix(repoPath, properties));
        properties.put(NIM_MODEL_NAME_ID, "test-id");
        assertEquals("test-id", metadataTemplate.getPkgidSuffix(repoPath, properties));
    }

    @Test
    void getVersion() {
        when(repoPath.getPath()).thenReturn("nimmodel");
        Properties properties = new PropertiesImpl();
        assertEquals(EMPTY, metadataTemplate.getVersion(repoPath, properties));
        properties.put("nimmodel.versionId", "test-version");
        assertEquals("test-version", metadataTemplate.getVersion(repoPath, properties));
    }

    @Test
    void getName() {
        when(repoPath.getPath()).thenReturn("huggingface");
        Properties properties = new PropertiesImpl();
        assertEquals(EMPTY, metadataTemplate.getName(properties, repoPath));
        properties.put(NIM_MODEL_NAME_ID, "test-id");
        assertEquals("test-id", metadataTemplate.getName(properties, repoPath));
    }

    @Test
    void getTags() {
        final Set<String> tags = metadataTemplate.getTagValueResolver().apply("one,two,three");
        assertEquals(3, tags.size());
        assertTrue(tags.contains("one"));
        assertTrue(tags.contains("two"));
        assertTrue(tags.contains("three"));
        final Set<String> emptyTags = metadataTemplate.getTagValueResolver().apply("");
        assertEquals(0, emptyTags.size());
    }

    @Test
    void getLeadFileExtensions() {
        final Set<String> leadFileExtensions = metadataTemplate.getLeadFileExtensions();
        assertEquals(1, leadFileExtensions.size());
        assertFalse(leadFileExtensions.contains("json"));
        assertFalse(leadFileExtensions.contains("anyFile.json"));
        assertTrue(leadFileExtensions.contains(".jfrog_nim_model_info.json"));
    }
}
