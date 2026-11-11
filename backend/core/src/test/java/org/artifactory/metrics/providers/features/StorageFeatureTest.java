package org.artifactory.metrics.providers.features;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.jfrog.test.LogTestUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.artifactory.api.callhome.FeatureGroup;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.jfrog.storage.binstore.BinariesInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryTreeElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;
import static org.jfrog.common.FileUtils.bytesToMB;

public class StorageFeatureTest {

    private static final Logger log = LoggerFactory.getLogger(StorageFeatureTest.class);

    @Mock
    private StorageService storageService;
    @Mock
    private StorageSummaryService storageSummaryService;
    @Mock
    private StorageSummaryInfo storageSummaryInfo;
    @Mock
    private BinariesInfo binariesInfo;
    @Mock
    private Map<String, String> nextData;
    @Mock
    private Map<String, String> nextSubData;
    @Mock
    private BinaryTreeElement<Map<String, String>> rootTreeElement;
    @Mock
    private BinaryTreeElement<Map<String, String>> nextSubElement;

    private Map<String, String> fullData;

    private AutoCloseable closeable;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    public static void beforeAll() {
        LogTestUtils.setLoggingLevel(StorageFeatureTest.class, Level.INFO);
    }

    @BeforeEach
    public void setup() throws Exception {
        closeable = openMocks(this);
        fullData = Map.of("type", "file-system",
                "id", "file-system",
                "toRemove", "notWhitelisted",
                "falseValue", "false",
                "trueValue", "true",
                "intValue", "1",
                "region", "needToStay");

        when(rootTreeElement.getData()).thenReturn(nextData);

        when(nextData.get("type")).thenReturn("file-system");
        when(nextData.get("id")).thenReturn("file-system");
        when(nextData.get("usageSpace")).thenReturn(String.valueOf(Long.MAX_VALUE));
        when(nextData.get("freeSpace")).thenReturn(String.valueOf(Long.MAX_VALUE));
        when(nextData.get("totalSpace")).thenReturn(String.valueOf(Long.MAX_VALUE));

        BinaryProvidersInfo<Map<String, String>> binaryProvidersInfo =
                new BinaryProvidersInfo<>("", rootTreeElement);
        when(storageService.getBinaryProviderInfo()).thenReturn(binaryProvidersInfo);
        when(storageService.getFullBinaryProviderInfo()).thenReturn(binaryProvidersInfo);
        when(storageSummaryInfo.getBinariesInfo()).thenReturn(binariesInfo);
        when(storageSummaryService.getLocalCache()).thenReturn(storageSummaryInfo);

        when(nextSubElement.getData()).thenReturn(nextSubData);
        when(nextSubData.get("type")).thenReturn("sub-file-system");
        when(nextSubData.get("id")).thenReturn("sub-file-system");
        when(nextSubData.get("usageSpace")).thenReturn(String.valueOf(Long.MAX_VALUE));
        when(nextSubData.get("freeSpace")).thenReturn(String.valueOf(Long.MAX_VALUE));
        when(nextSubData.get("totalSpace")).thenReturn(String.valueOf(Long.MAX_VALUE));
    }

    @Test
    void testWhitelistData() throws Exception {
        when(rootTreeElement.getData()).thenReturn(fullData);
        Map<String, String> expectedFeature = Map.of("type", "file-system",
                "parentId", "",
                "label", "file-system",
                "falseValue", "false",
                "trueValue", "true",
                "intValue", "1",
                "region", "needToStay");

        StorageFeature storageFeature = new StorageFeature();
        FieldUtils.writeField(storageFeature, "storageService", storageService, true);
        FieldUtils.writeField(storageFeature, "storageSummaryService", storageSummaryService, true);
        FeatureGroup chainFeature = storageFeature.getFeature().getFeatures().get(2);

        Map<String, Object> data = chainFeature.getFeatures().get(0).getAttributes();

        assertEquals(data, expectedFeature);
    }

    @Test
    void testGetChainFeatureWithMounts() {
        when(rootTreeElement.getSubBinaryTreeElements()).thenReturn(Lists.newArrayList(nextSubElement));

        StorageFeature storageFeature = new StorageFeature();
        FeatureGroup chainFeature = new FeatureGroup("chain");
        storageFeature.addStorageChainSummary(chainFeature, rootTreeElement);

        Map<String, Object> data = chainFeature.getFeatures().get(0).getAttributes();
        assertEquals(data.get("label"), "file-system");
        Map<String, Object> subData = chainFeature.getFeatures().get(1).getAttributes();
        assertEquals(subData.get("label"), "sub-file-system");
    }

    @Test
    void getFeatureFederatedRepoBinaryTreeElementNoMirrors() throws Exception {
        StorageFeature storageFeature = new StorageFeature();
        FieldUtils.writeField(storageFeature, "storageService", storageService, true);
        FieldUtils.writeField(storageFeature, "storageSummaryService", storageSummaryService, true);
        FeatureGroup featureGroup = storageFeature.getFeature();

        verify(featureGroup, "file-system", "file-system");
    }

    @Test
    void getFeatureFederatedRepoBinaryTreeElementWithMirrors() throws Exception {
        when(rootTreeElement.getSubBinaryTreeElements()).thenReturn(Lists.newArrayList(nextSubElement));

        StorageFeature storageFeature = new StorageFeature();
        FieldUtils.writeField(storageFeature, "storageService", storageService, true);
        FieldUtils.writeField(storageFeature, "storageSummaryService", storageSummaryService, true);
        FeatureGroup featureGroup = storageFeature.getFeature();

        verify(featureGroup, "file-system", "sub-file-system");
    }

    @ParameterizedTest
    @MethodSource("storageValuesProvider")
    void getAttributeSizeInMbForLongValue(String sizeAsText, Long expectedSize) {
        StorageFeature storageFeature = new StorageFeature();
        Map<String, String> testData = new HashMap<>();
        testData.put("size", sizeAsText);
        long actual = storageFeature.getAttributeSizeInMb(testData, "size");

        assertEquals(actual, bytesToMB(expectedSize));
    }

    private static Stream<Arguments> storageValuesProvider() {
        return Stream.of(
                Arguments.of("infinite", -1L),
                Arguments.of(null, -1L),
                Arguments.of("", -1L),
                Arguments.of("unsupported", -1L),
                Arguments.of(String.valueOf(Long.MAX_VALUE), Long.MAX_VALUE),
                Arguments.of("1111111111", 1111111111L)
        );
    }

    private void verify(FeatureGroup featureGroup, String root, String sub) throws Exception {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(featureGroup);
        log.info(json);

        JsonNode node = objectMapper.readTree(json);
        JsonNode features = node.get("features");
        assertEquals(features.get(0).get("attributes").get("provider").asText(), root);
        assertEquals(features.get(1).get("name").asText(), "mount " + sub);
        assertEquals(features.get(1).get("attributes").get("label").asText(), sub);
        assertEquals(features.get(1).get("attributes").get("type").asText(), sub);
    }

    @AfterEach
    public void tearDown() throws Exception {
        closeable.close();
    }
}

