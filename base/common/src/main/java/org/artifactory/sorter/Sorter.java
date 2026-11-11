package org.artifactory.sorter;

import org.artifactory.exception.InMemorySortException;

import java.io.InputStream;

/**
 * @author Shahar Palmor
 */
@FunctionalInterface
public interface Sorter {

    /**
     *
     * @param inputStream
     * @throws InMemorySortException
     */
    void sort(InputStream inputStream) throws InMemorySortException;
}
