package org.artifactory.sorter.merge;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.aql.result.Row;
import org.jfrog.support.rest.model.utils.JsonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * @author Shahar Palmor
 */
@Slf4j
public class MergerImpl implements Merger {
    @VisibleForTesting
    private final ObjectMapper objectMapper;
    private final Map<Row, JsonParser> mapObjectToParser;
    private final PriorityQueue<Row> priorityQueue;

    private final List<JsonParser> parsers;

    public MergerImpl(Comparator<Row> comparator) {
        this.priorityQueue = new PriorityQueue<>(comparator);
        this.objectMapper = JsonUtils.getDefaultObjectMapper();
        this.mapObjectToParser = new HashMap<>();
        this.parsers = new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void merge(List<File> files, OutputStream mergedOutput) throws IOException {
        if (files.size() == 1) {
            handleSingleFile(files.get(0), mergedOutput);
            return;
        }
        try {
            initialize(files);
            writeToMergedFile(mergedOutput);
        } finally {
            for (JsonParser parser : parsers) {
                if (parser != null) {
                    parser.close();
                }
            }
        }
    }

    /**
     * If the splitter returns only one file then it is allready sorted and we need to write it to the output stream
     *
     * @param file       is the single file returned from the splitter.
     * @param mergedFile is the merged output stream to be written.
     */
    private void handleSingleFile(File file, OutputStream mergedFile) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            addResultsPrefixToFile(mergedFile);
            mergedFile.write(inputStream.readAllBytes());
            mergedFile.write("}".getBytes());
        } catch (IOException e) {
            log.error("Failed to handle single sorted file due to: {}", e.getMessage());
            log.debug("Failed to handle single sorted file due to:", e);
            throw new IOException("Failed to handle single sorted file due to:  ", e);
        }
    }

    /**
     * This method writes to the merged output stream the row results from the sorted files.
     * Using the priority queue: each time we pool the item from the queue, write it to the output stream and
     * gets the parser related to the written object and find the next one to be pushed to the queue.
     *
     * @param mergedOutput is the merged output stream.
     */
    void writeToMergedFile(OutputStream mergedOutput) throws IOException {
        boolean isFirst = true;
        try {
            mergedOutput.write(AqlRestResult.QUERY_PREFIX.getBytes());
            while (!priorityQueue.isEmpty()) {
                if (!isFirst) {
                    mergedOutput.write(",".getBytes());
                }
                Row itemOnPriority = priorityQueue.poll();
                JsonParser parser = mapObjectToParser.remove(itemOnPriority);
                mergedOutput.write(objectMapper.writeValueAsBytes(itemOnPriority));
                if (isFirst) {
                    isFirst = false;
                }
                getNextRow(parser);
            }
            mergedOutput.write("]}".getBytes());
        } catch (IOException e) {
            log.error("Failed to merge sorted files due to: {}", e.getMessage());
            log.debug("Failed to merge sorted files due to:", e);
            throw new IOException("Failed to merge sorted files due to:  ", e);
        }
    }

    /**
     * This method initialise the priority queue with the first object in each file, and also initialises the map
     * that will enable to run on each file
     *
     * @param files are the list of sorted files returned from the splitter
     */
    void initialize(List<File> files) throws IOException {
        for (File file : files) {
            try {
                FileInputStream fileInputStream = new FileInputStream(file);
                JsonParser parser = objectMapper.getFactory().createParser(fileInputStream);
                parsers.add(parser);
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                    throw new IllegalStateException("Expected an array");
                }
                // get first object
                getNextRow(parser);
            } catch (IOException e) {
                log.error("Failed to initialize during in memory merge due to: {}", e.getMessage());
                log.debug("Failed to initialize during in memory merge due to:", e);
                throw new IOException("Failed to initialize during in memory merge due to: ", e);
            }
        }
    }

    /**
     * @param parser is the pointer to the current file that we want to get the next row from.
     *               it enters to the priority queue the next row that the parser is pointing on.
     * @throws IOException
     */
    private void getNextRow(JsonParser parser) throws IOException {
        Row nextObjToPush;
        if (parser.nextToken() != JsonToken.END_ARRAY) {
            nextObjToPush = objectMapper.readValue(parser, Row.class);
            if (nextObjToPush != null) {
                mapObjectToParser.put(nextObjToPush, parser);
                priorityQueue.add(nextObjToPush);
            }
        }
    }

    /**
     * add the prefix of results to the start of the file
     *
     * @throws IOException
     */
    public void addResultsPrefixToFile(OutputStream outputStream) throws IOException {
        String res = AqlRestResult.QUERY_PREFIX.substring(0, AqlRestResult.QUERY_PREFIX.indexOf("["));
        outputStream.write(res.getBytes());
    }
}

