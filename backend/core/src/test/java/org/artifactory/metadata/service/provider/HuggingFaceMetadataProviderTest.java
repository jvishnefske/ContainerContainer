package org.artifactory.metadata.service.provider;

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
import static org.artifactory.metadata.service.provider.HuggingFaceMetadataProvider.HF_ENTITY_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Volodymyr Shulga
 */
class HuggingFaceMetadataProviderTest {
    @Mock
    private RepoPath repoPath;

    private HuggingFaceMetadataProvider metadataProvider;
    private HuggingFaceMetadataProvider.HuggingFaceTypeSpecificMetadataTemplate metadataTemplate;
    private AutoCloseable closeable;
    private static final String HF_ID = "huggingfaceml.id";

    @BeforeEach
    void setUp() {
        metadataProvider = new HuggingFaceMetadataProvider();
        metadataTemplate = metadataProvider.new HuggingFaceTypeSpecificMetadataTemplate();
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void isLeadArtifact() {
        when(repoPath.getPath()).thenReturn("huggingface");
        assertFalse(metadataProvider.isLeadArtifact(repoPath));
        when(repoPath.getPath()).thenReturn("huggingface.json");
        assertFalse(metadataProvider.isLeadArtifact(repoPath));
        when(repoPath.getPath()).thenReturn("huggingface.txt");
        assertFalse(metadataProvider.isLeadArtifact(repoPath));
        when(repoPath.getPath()).thenReturn(HuggingFaceMetadataProvider.MODEL_LEAD_FILE_NAME);
        assertTrue(metadataProvider.isLeadArtifact(repoPath));
        when(repoPath.getPath()).thenReturn(HuggingFaceMetadataProvider.DATASET_LEAD_FILE_NAME);
        assertTrue(metadataProvider.isLeadArtifact(repoPath));
    }

    @ParameterizedTest
    @MethodSource("pathParams")
    void isPackageConsumptionLead(String path, boolean expectedResult) {
        RepoPath repoPath = Mockito.mock(RepoPath.class);
        when(repoPath.getPath()).thenReturn(path);
        assertEquals(expectedResult, metadataProvider.isPackageConsumptionLead(repoPath));
    }

    @ParameterizedTest
    @MethodSource("typeParams")
    void getType(String modelTypeName, String typeResult) {
        Properties prop = new PropertiesImpl();
        prop.put(HF_ENTITY_TYPE, modelTypeName);
        assertEquals(typeResult, metadataProvider.getType(prop));
    }

    @ParameterizedTest
    @MethodSource("pkgIdResolverParams")
    void pkgIdResolverTest(String modelTypeName, String modelName, String expectedResult) {
        MutableMetadataEntityBOM mutableMetadataEntityBOM = new MutableMetadataEntityBOM();
        Properties prop = new PropertiesImpl();
        prop.put(HF_ENTITY_TYPE, modelTypeName);
        prop.put(HF_ID, modelName);
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        MetadataResolver metadataResolver = metadataProvider.pkgIdResolver();
        metadataResolver.apply(mutableMetadataEntityBOM, prop, fileInfo, null);
        assertEquals(expectedResult, mutableMetadataEntityBOM.getPkgid());
    }

    @Test
    void getPkgidSuffix() {
        when(repoPath.getPath()).thenReturn("huggingface");
        Properties properties = new PropertiesImpl();
        assertEquals(EMPTY, metadataTemplate.getPkgidSuffix(repoPath, properties));
        properties.put(HF_ID, "test-id");
        assertEquals("test-id", metadataTemplate.getPkgidSuffix(repoPath, properties));
    }

    @Test
    void getVersion() {
        when(repoPath.getPath()).thenReturn("huggingface");
        Properties properties = new PropertiesImpl();
        assertEquals(EMPTY, metadataTemplate.getVersion(repoPath, properties));
        properties.put("huggingfaceml.version", "test-version");
        assertEquals("test-version", metadataTemplate.getVersion(repoPath, properties));
    }

    @Test
    void getName() {
        when(repoPath.getPath()).thenReturn("huggingface");
        Properties properties = new PropertiesImpl();
        assertEquals(EMPTY, metadataTemplate.getName(properties, repoPath));
        properties.put(HF_ID, "test-id");
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
        assertEquals(2, leadFileExtensions.size());
        assertFalse(leadFileExtensions.contains("json"));
        assertFalse(leadFileExtensions.contains("anyFile.json"));
        assertTrue(leadFileExtensions.contains(".jfrog_huggingface_model_info.json"));
        assertTrue(leadFileExtensions.contains(".jfrog_huggingface_dataset_info.json"));
    }

    @Test
    void getExcludedProps() {
        final Set<String> excludedProps = metadataTemplate.getExcludedUserProperties();
        assertEquals(4, excludedProps.size());
        assertTrue(excludedProps.contains(HF_ID));
        assertTrue(excludedProps.contains("huggingfaceml.version"));
        assertTrue(excludedProps.contains("huggingfaceml.description"));
        assertTrue(excludedProps.contains("huggingfaceml.tags"));
    }

    public static Stream<Arguments> typeParams() {
        return Stream.of(
                Arguments.of("MODEL", "huggingfaceml"),
                Arguments.of("model", "huggingfaceml"),
                Arguments.of("DATASET", "hfdataset"),
                Arguments.of("dataset", "hfdataset"),
                Arguments.of(null, "huggingfaceml"));
    }

    public static Stream<Arguments> pkgIdResolverParams() {
        return Stream.of(
                Arguments.of("MODEL", "modelname", "huggingfaceml://modelname"),
                Arguments.of("model", "modelname", "huggingfaceml://modelname"),
                Arguments.of("DATASET", "modelname", "hfdataset://modelname"),
                Arguments.of("dataset", "modelname", "hfdataset://modelname"),
                Arguments.of("dataset", "huggingfaceml", "hfdataset://huggingfaceml"),
                Arguments.of(null, "modelname", "huggingfaceml://modelname")
        );
    }

    public static Stream<Arguments> pathParams() {
        return Stream.of(
                Arguments.of("hellow.json", true),
                Arguments.of(HuggingFaceMetadataProvider.DATASET_LEAD_FILE_NAME, false),
                Arguments.of(HuggingFaceMetadataProvider.MODEL_LEAD_FILE_NAME, false));
    }
}