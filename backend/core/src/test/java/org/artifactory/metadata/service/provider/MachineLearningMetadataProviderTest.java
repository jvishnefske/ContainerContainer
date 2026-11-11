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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Set;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.artifactory.descriptor.repo.RepoType.MachineLearning;
import static org.artifactory.metadata.service.provider.MachineLearningMetadataProvider.MODEL_LEAD_FILE_NAME;
import static org.artifactory.metadata.service.provider.MachineLearningMetadataProvider.DATASET_LEAD_FILE_NAME;
import static org.artifactory.metadata.service.provider.MachineLearningMetadataProvider.PROP_ENTITY_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author avivbr
 */
class MachineLearningMetadataProviderTest {

    @Mock
    private RepoPath repoPath;
    private MachineLearningMetadataProvider metadataProvider;
    private MachineLearningMetadataProvider.MachineLearningTypeSpecificMetadataTemplate metadataTemplate;
    private AutoCloseable closeable;
    private static final String ML_ID = "machinelearning.name";

    @BeforeEach
    void setUp() {
        metadataProvider = new MachineLearningMetadataProvider();
        metadataTemplate = metadataProvider.new MachineLearningTypeSpecificMetadataTemplate();
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {MODEL_LEAD_FILE_NAME, DATASET_LEAD_FILE_NAME})
    void isLeadArtifact(String leadFileName) {
        when(repoPath.getPath()).thenReturn("machinelearning");
        assertFalse(metadataProvider.isLeadArtifact(repoPath));
        when(repoPath.getPath()).thenReturn("machinelearning.json");
        assertFalse(metadataProvider.isLeadArtifact(repoPath));
        when(repoPath.getPath()).thenReturn("machinelearning.txt");
        assertFalse(metadataProvider.isLeadArtifact(repoPath));
        when(repoPath.getPath()).thenReturn(leadFileName);
        assertTrue(metadataProvider.isLeadArtifact(repoPath));
    }

    @ParameterizedTest
    @MethodSource("repoPathProvider")
    void isPackageConsumptionLead(String leadFileName, boolean expectedResult) {
        when(repoPath.getPath()).thenReturn(leadFileName);
        boolean actualResult = metadataProvider.isPackageConsumptionLead(repoPath);

        assertEquals(expectedResult, actualResult);
    }

    public static Stream<Arguments> repoPathProvider() {
        return Stream.of(
                Arguments.of("/models/org/name/version/model/artifacts/bla.txt", false),
                Arguments.of("/models/org/name/version/artifacts/model/bla.txt", true),
                Arguments.of("machinelearning", false),
                Arguments.of("machinelearning.json", false),
                Arguments.of("machinelearning.txt", false),
                Arguments.of(MODEL_LEAD_FILE_NAME, true),
                Arguments.of(DATASET_LEAD_FILE_NAME, true)
        );
    }

    @Test
    void getPkgidSuffix() {
        when(repoPath.getPath()).thenReturn("machinelearning");
        Properties properties = new PropertiesImpl();
        assertEquals(EMPTY, metadataTemplate.getPkgidSuffix(repoPath, properties));
        properties.put("machinelearning.name", "test-name");
        assertEquals("test-name", metadataTemplate.getPkgidSuffix(repoPath, properties));
    }

    @Test
    void getVersion() {
        when(repoPath.getPath()).thenReturn("machinelearning");
        Properties properties = new PropertiesImpl();
        assertEquals(EMPTY, metadataTemplate.getVersion(repoPath, properties));
        properties.put("machinelearning.version", "test-version");
        assertEquals("test-version", metadataTemplate.getVersion(repoPath, properties));
    }

    @Test
    void getName() {
        when(repoPath.getPath()).thenReturn("machinelearning");
        Properties properties = new PropertiesImpl();
        assertEquals(EMPTY, metadataTemplate.getName(properties, repoPath));
        properties.put("machinelearning.name", "test-name");
        assertEquals("test-name", metadataTemplate.getName(properties, repoPath));
    }

    @Test
    void getLeadFileExtensions() {
        final Set<String> leadFileExtensions = metadataTemplate.getLeadFileExtensions();
        assertEquals(2, leadFileExtensions.size());
        assertTrue(
                leadFileExtensions.contains(MODEL_LEAD_FILE_NAME) &&
                        leadFileExtensions.contains(DATASET_LEAD_FILE_NAME)
        );
    }

    @Test
    void getExcludedProps() {
        final Set<String> excludedProps = metadataTemplate.getExcludedUserProperties();
        Set<String> expectedProps = Set.of(
                "machinelearning.name",
                "machinelearning.version",
                "machinelearning.create.date",
                "machinelearning.entity.type"
        );
        assertEquals(4, excludedProps.size());
        assertEquals(expectedProps, excludedProps);
    }

    @Test
    void getDefaultType() {
        Properties mockProperties = Mockito.mock(Properties.class);
        when(mockProperties.getFirst(MachineLearning.getType() + PROP_ENTITY_TYPE))
                .thenReturn(null);
        String result = metadataProvider.getType(mockProperties);

        assertEquals("machinelearning", result);
    }

    @Test
    void getTypeDataset() {
        Properties mockProperties = Mockito.mock(Properties.class);

        when(mockProperties.getFirst(MachineLearning.getType() + PROP_ENTITY_TYPE))
                .thenReturn("dataset");
        String result = metadataProvider.getType(mockProperties);

        assertEquals(MachineLearningMetadataProvider.MACHINELEARNING_DATASET_TYPE, result);
    }

    @Test
    void getTypeModel() {
        Properties mockProperties = Mockito.mock(Properties.class);

        when(mockProperties.getFirst(MachineLearning.getType() + PROP_ENTITY_TYPE))
                .thenReturn(MachineLearning.getType());
        String result = metadataProvider.getType(mockProperties);

        assertEquals("machinelearning", result);
    }

    @Test
    void testConstants() {
        assertEquals("model-manifest.json", MODEL_LEAD_FILE_NAME);
        assertEquals("dataset-manifest.json", MachineLearningMetadataProvider.DATASET_LEAD_FILE_NAME);
        assertEquals(".entity.type", PROP_ENTITY_TYPE);
        assertEquals("machinelearningdataset", MachineLearningMetadataProvider.MACHINELEARNING_DATASET_TYPE);
    }

    @ParameterizedTest
    @MethodSource("pkgIdResolverParams")
    void pkgIdResolverTest(String modelTypeName, String modelName, String expectedResult) {
        MutableMetadataEntityBOM mutableMetadataEntityBOM = new MutableMetadataEntityBOM();
        Properties prop = new PropertiesImpl();
        prop.put("machinelearning" + PROP_ENTITY_TYPE, modelTypeName);
        prop.put(ML_ID, modelName);
        FileInfo fileInfo = Mockito.mock(FileInfo.class);
        MetadataResolver metadataResolver = metadataProvider.pkgIdResolver();
        metadataResolver.apply(mutableMetadataEntityBOM, prop, fileInfo, null);
        assertEquals(expectedResult, mutableMetadataEntityBOM.getPkgid());
    }

    public static Stream<Arguments> pkgIdResolverParams() {
        return Stream.of(
                Arguments.of("MODEL", "modelname", "machinelearning://modelname"),
                Arguments.of("model", "modelname", "machinelearning://modelname"),
                Arguments.of("DATASET", "datasetname", "machinelearningdataset://datasetname"),
                Arguments.of("dataset", "datasetname", "machinelearningdataset://datasetname"),
                Arguments.of("dataset", "machinelearningdataset", "machinelearningdataset://machinelearningdataset"),
                Arguments.of(null, "modelname", "machinelearning://modelname")
        );
    }
}