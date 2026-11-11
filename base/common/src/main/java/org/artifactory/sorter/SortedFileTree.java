package org.artifactory.sorter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.aql.result.Row;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.SortedSet;

/**
 * @author Shahar Palmor
 */
@NoArgsConstructor
@Slf4j
public class SortedFileTree {
    private final Random random = new Random();
    @Getter
    private final List<File> files = new ArrayList<>();

    /**
     * This method adds a file to the list
     * @param mapper is the object mapper that maps the elements
     * @param elements is the tree set that contains the current bulk of rows to be written to the file
     * @throws IOException
     */
    public void addSortedFileTree(ObjectMapper mapper, SortedSet<Row> elements) throws IOException {
        File tempWorkFullSyncDir = ContextHelper.get().getArtifactoryHome().getTempWorkFullSyncDir();
        File file = new File(tempWorkFullSyncDir + "/" + random.nextInt(Integer.MAX_VALUE) + ".json");
        mapper.writeValue(file, elements);
        files.add(file);
        elements.clear();
    }
}
