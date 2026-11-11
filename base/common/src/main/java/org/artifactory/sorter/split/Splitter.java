package org.artifactory.sorter.split;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author Shahar Palmor
 */
@FunctionalInterface
public interface Splitter {

    /**
     * This method splits the input stream using json parser to list of files that contains
     * list of sorted Row objects
     * @param inputStream is the stream the that the json parser is parsing
     * @return list of sorted files, each containing bulk of Row objects of size that
     * is configured
     * @throws IOException in case of parser is failing
     */
    List<File> split(InputStream inputStream) throws IOException;
}
