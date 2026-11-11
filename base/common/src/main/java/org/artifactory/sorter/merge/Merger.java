package org.artifactory.sorter.merge;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * @author Shahar Palmor
 */
@FunctionalInterface
public interface Merger {
    /**
     * merges to the output stream the artifacts from all the files provided
     * @param files contains sorted bulks of artifacts
     * @param mergedFile is the merged output stream to be written.
     */
    void merge(List<File> files, OutputStream mergedFile) throws IOException;
}
