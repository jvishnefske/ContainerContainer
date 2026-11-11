package org.artifactory.sorter.split;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.aql.result.Row;
import org.artifactory.common.ConstantValues;
import org.artifactory.sorter.SortedFileTree;
import org.jfrog.support.rest.model.utils.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * @author Shahar Palmor
 */
@Slf4j
public class SplitterImpl implements Splitter {

    long numberOfEntries = 0;
    private final ObjectMapper objectMapper;
    private final TreeSet<Row> elements;

    public SplitterImpl(Comparator<Row> comparator) {
        this.objectMapper = JsonUtils.getDefaultObjectMapper();
        this.elements = new TreeSet<>(comparator);
        this.numberOfEntries = ConstantValues.federatedRepoFullSyncLimitEntriesForSplit.getLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<File> split(InputStream inputStream) throws IOException {
        long currentEntriesInCurrentFile = 0;
        SortedFileTree sortedFileTree = new SortedFileTree();
        try (JsonParser parser = objectMapper.getFactory().createParser(inputStream)) {
            if (isObjectStart(parser)) {
                assertValidResponse(parser);
                log.trace("Started handling first file");
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (currentEntriesInCurrentFile == numberOfEntries) {
                        currentEntriesInCurrentFile = 0;
                        sortedFileTree.addSortedFileTree(objectMapper, elements);
                        log.debug("Finished handling file");
                        log.debug("Started handling next file");
                    } else {
                        Row obj = objectMapper.readValue(parser, Row.class);
                        elements.add(obj);
                        currentEntriesInCurrentFile++;
                    }
                }
                if (!elements.isEmpty()) {
                    // Place last batch in file
                    log.debug("Creating one last file, amount of entries: {}", elements.size());
                    sortedFileTree.addSortedFileTree(objectMapper, elements);
                }
            }
        }
        return sortedFileTree.getFiles();
    }

    /**
     * This method checks that we are handling an array.
     *
     * @param parser that scans the stream
     * @throws IOException
     */
    private void assertValidResponse(JsonParser parser) throws IOException {
        if (parser.nextToken() != JsonToken.START_ARRAY) {
            throw new IllegalStateException("The response from inputStream is invalid, expected an array");
        }
    }

    /**
     * This method checks that the stream returned from the aql started properly
     *
     * @param parser that scans the stream
     * @throws IOException
     */
    private boolean isObjectStart(JsonParser parser) throws IOException {
        return parser.nextToken().equals(JsonToken.START_OBJECT) &&
                parser.nextToken().equals(JsonToken.FIELD_NAME);
    }
}
