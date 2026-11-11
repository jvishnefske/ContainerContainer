package org.artifactory.sorter.split;


import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.aql.result.Row;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author Shahar Palmor
 */
public class SplitterImplTest extends ArtifactoryHomeBoundJUnitTest {
    private SplitterImpl splitter;
    private ObjectMapper objectMapper;
    @Mock
    private ArtifactoryContext context;
    private AutoCloseable mocks;

    private File tempFullSyncDir;

    @BeforeEach
    public void setUp() throws IOException {
        mocks = MockitoAnnotations.openMocks(this);
        objectMapper = spy(new ObjectMapper());
        Comparator<Row> comparator =
                Comparator.comparing(Row::getItemDepth).thenComparing(Row::getItemPath).thenComparing(Row::getItemName);
        splitter = new SplitterImpl(comparator);
        ArtifactoryHomeStub artifactoryHomeStub = getOrCreateArtifactoryHomeStub();
        when(context.getArtifactoryHome()).thenReturn(artifactoryHomeStub);
        ArtifactoryContextThreadBinder.bind(context);
        ReflectionTestUtils.setField(splitter, "objectMapper", objectMapper);
        tempFullSyncDir = new File(ArtifactoryHomeStub.get().getTempWorkDir(), "fullSync");
        FileUtils.forceMkdir(tempFullSyncDir);
    }

    @AfterEach
    public void finish() throws Exception {
        tempFullSyncDir.delete();
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testSplit() throws IOException {
        InputStream json = buildInputStream(1000);
        splitter.numberOfEntries = 10;
        List<File> files = splitter.split(json);
        assertEquals(files.size(), 100);
        files.forEach(File::delete);
    }

    @Test
    public void testSplitIsSortingBeforeInsertToFile() throws IOException {
        InputStream json = buildInputStream(2);
        splitter.numberOfEntries = 2;
        List<File> files = splitter.split(json);
        InputStream inputStream = new FileInputStream(files.get(0));

        List<Row> sortedList = new ArrayList<>();
        try (JsonParser parser = objectMapper.getFactory().createParser(inputStream)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("Expected an array");
            }
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                Row obj = objectMapper.readValue(parser, Row.class);
                sortedList.add(obj);
            }
        }
        List<Integer> depthValues = sortedList.stream().map(Row::getItemDepth).toList();
        List<String> pathValues = sortedList.stream().map(Row::getItemPath).toList();

        assertEquals(2, sortedList.size());
        assertTrue(depthValues.get(0) <= depthValues.get(1));
        if (depthValues.get(0).equals(depthValues.get(1))) {
            assertTrue(pathValues.get(0).compareTo(pathValues.get(1)) < 0);
        }
        files.forEach(File::delete);
    }

    @Test
    public void testSplitWithAmountOfEntriesThatNotDivideToNumberOfEntries() throws IOException {
        InputStream json = buildInputStream(19);
        splitter.numberOfEntries = 10;
        List<File> files = splitter.split(json);
        files.forEach(File::delete);
        assertEquals(files.size(), 2);
    }

    @Test
    public void testSplitOnYamlFile() throws IOException {
        StringBuilder yamlBody = new StringBuilder("test:\n");
        yamlBody.append("    x: 5\n")
                .append("    y: 'a'\n");
        InputStream inputStream = new ByteArrayInputStream(yamlBody.toString().getBytes());
        List<File> files = new ArrayList<>();
        try {
            files = splitter.split(inputStream);
            fail("should have thrown exception");
        } catch (JsonParseException e) {
            assertEquals(files.size(), 0);
        }
    }

    @Test
    public void testSplitOnNotJsonFile() throws IOException {
        String notJson = "{\"repo\"-\"mirror\"}";
        InputStream inputStream = new ByteArrayInputStream(notJson.getBytes());
        List<File> files = new ArrayList<>();
        try {
            files = splitter.split(inputStream);
            fail("should have thrown exception");
        } catch (JsonParseException e) {
            assertEquals(files.size(), 0);
        }
    }

    @Test
    public void testSplitNotJsonArray() throws IOException {
        String notJson = "{\"repo\":\"stam\"}";
        InputStream inputStream = new ByteArrayInputStream(notJson.getBytes());
        List<File> files = new ArrayList<>();
        try {
            files = splitter.split(inputStream);
            fail("should have thrown exception");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().equals("The response from inputStream is invalid, expected an array"));
            assertEquals(files.size(), 0);
        }
    }


    public InputStream buildInputStream(int number) throws JsonProcessingException {
        Random r = new Random();
        String letters = "abcdefghijklmnopqrstuvwxyz";
        List<Row> list = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            Row row = new Row();
            row.itemDepth = r.nextInt(10);
            row.itemPath = randomString(3, letters);
            row.itemName = randomString(3, letters);
            row.itemRepo = "test";
            row.itemType = AqlItemTypeEnum.any;
            row.itemModified = "\"2023-01-10T14:57:23.620+02:00\"";
            row.itemSha2 = "0ff9dcc1cc3dba4ea96233fdc035572bc1f382227be2fdc93d087aa91492d610";
            list.add(row);
        }
        String json = objectMapper.writeValueAsString(list);
        String jsonWithResult =
                AqlRestResult.QUERY_PREFIX.substring(0, AqlRestResult.QUERY_PREFIX.length() - 2).concat(json)
                        .concat("}");
        InputStream inputStream = new ByteArrayInputStream(jsonWithResult.getBytes());
        return inputStream;
    }

    public static String randomString(int len, String pattern) {
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(pattern.charAt(rnd.nextInt(pattern.length())));
        }
        return sb.toString();
    }
}