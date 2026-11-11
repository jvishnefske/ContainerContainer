package org.artifactory.sorter.merge;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.result.Row;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author Shahar Palmor
 */
public class MergerImplTest extends ArtifactoryHomeBoundTest {
    private MergerImpl merger;
    private ObjectMapper objectMapper;
    private Comparator<Row> comparator;
    private String tempWorkDir;

    @BeforeEach
    public void setUp() throws IOException {
        objectMapper = spy(new ObjectMapper());
        comparator =
                Comparator.comparing(Row::getItemDepth).thenComparing(Row::getItemPath).thenComparing(Row::getItemName);
        File tempDir = Files.createTempDirectory("temp").toFile();
        tempWorkDir = tempDir.getAbsolutePath();
        merger = new MergerImpl(comparator);
        ReflectionTestUtils.setField(merger, "objectMapper", objectMapper);
    }

    @Test
    public void testMerge() throws IOException {
        List<File> files = buildFileList();
        MergerImpl mockMerge = spy(new MergerImpl(comparator));
        File mergedFile = new File(tempWorkDir + "/merge");
        FileOutputStream fileOutputStream = new FileOutputStream(mergedFile);
        FileUtils.touch(mergedFile);
        try {
            doNothing().when(mockMerge).initialize(anyList());
            doNothing().when(mockMerge).writeToMergedFile(any());
            mockMerge.merge(files, fileOutputStream);
        } finally {
            files.forEach(file -> assertTrue(file.delete()));
            assertTrue(mergedFile.delete());
        }
    }

    @Test
    public void testMergeWithOneFile() throws IOException {
        List<File> files = buildFileList().subList(0, 1);
        MergerImpl mockMerge = spy(new MergerImpl(comparator));
        File mergedFile = new File(tempWorkDir + "/merge");
        FileOutputStream fileOutputStream = new FileOutputStream(mergedFile);
        FileUtils.touch(mergedFile);
        try {
            mockMerge.merge(files, fileOutputStream);
        } finally {
            files.forEach(file -> assertTrue(file.delete()));
            assertTrue(mergedFile.delete());
        }
    }

    @Test
    public void testInitializeParsersAndQueue() throws IOException {
        List<File> files = buildFileList();
        try {
            merger.initialize(files);
            PriorityQueue priorityQueue = (PriorityQueue) ReflectionTestUtils.getField(merger, "priorityQueue");
            assertEquals(priorityQueue.size(), 2);
        } finally {
            files.forEach(file -> assertTrue(file.delete()));
        }
    }

    @Test
    public void testFileNotContainsAnArray() throws IOException {
        Random random = new Random();
        File file = new File(tempWorkDir + "/" + random.nextInt(Integer.MAX_VALUE) + ".json");
        objectMapper.writeValue(file, "{}");
        try {
            merger.initialize(List.of(file));
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Expected an array"));
            assertTrue(file.delete());
        }
    }

    @Test
    public void testErrorsWhileInitializeUsingParser() {
        Random random = new Random();
        File file = new File(tempWorkDir + "/" + random.nextInt(Integer.MAX_VALUE) + ".json");
        try {
            objectMapper.writeValue(file, "{}");
            JsonParser parser = mock(JsonParser.class);
            MappingJsonFactory jsonFactoryMock = mock(MappingJsonFactory.class);
            when(objectMapper.getFactory()).thenReturn(jsonFactoryMock);
            when(jsonFactoryMock.createParser((FileInputStream) any())).thenReturn(parser);
            when(parser.nextToken()).thenThrow(new IOException());
            merger.initialize(List.of(file));
            fail("should have thrown exception");
        } catch (Exception e) {
            assertTrue(e instanceof IOException);
            assertTrue(file.delete());
        }
    }

    @Test
    public void testGetOutputStream() throws IOException {
        List<File> files = buildFileList();
        File mergedFile = new File(tempWorkDir + "/merged.json");
        FileOutputStream fileOutputStream = new FileOutputStream(mergedFile);
        FileUtils.touch(mergedFile);
        try {
            merger.initialize(files);
            merger.writeToMergedFile(fileOutputStream);
        } finally {
            files.forEach(file -> assertTrue(file.delete()));
            assertTrue(mergedFile.delete());
        }
    }

    public List<File> buildFileList() throws IOException {
        List<File> files = new ArrayList<>();
        files.add(createContentFile(0, 2, 'a'));
        files.add(createContentFile(2, 4, 'c'));
        return files;
    }

    public File createContentFile(int start, int end, char str) throws IOException {
        Random random = new Random();
        List<Row> list = new ArrayList<>();
        File file = new File(tempWorkDir + "/" + random.nextInt(Integer.MAX_VALUE) + ".json");
        OutputStream outputStream = new FileOutputStream(file);
        for (int i = start; i < end; i++) {
            Row row = new Row();
            row.itemDepth = i;
            row.itemPath = str + "a";
            row.itemName = str + "a";
            row.itemRepo = "test";
            row.itemType = AqlItemTypeEnum.any;
            row.itemModified = "\"2023-01-10T14:57:23.620+02:00\"";
            row.itemSha2 = "0ff9dcc1cc3dba4ea96233fdc035572bc1f382227be2fdc93d087aa91492d610";
            list.add(row);
            str++;
        }
        outputStream.write(objectMapper.writeValueAsBytes(list));
        FileUtils.touch(file);
        return file;
    }
}