package org.artifactory.sorter;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.aql.result.Row;
import org.artifactory.exception.InMemorySortException;
import org.artifactory.sorter.merge.Merger;
import org.artifactory.sorter.merge.MergerImpl;
import org.artifactory.sorter.split.Splitter;
import org.artifactory.sorter.split.SplitterImpl;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Shahar Palmor
 */
@Slf4j
public class SorterImpl implements Sorter {
    private final Splitter splitter;
    private final Merger merger;
    private final OutputStream mergedOutput;

    public SorterImpl(OutputStream mergedOutput) {
        Comparator<Row> comparatorRow = Comparator.comparing(Row::getItemDepth)
                .thenComparing(Row::getItemPath).thenComparing(Row::getItemName);
        this.splitter = new SplitterImpl(comparatorRow);
        this.merger = new MergerImpl(comparatorRow);
        this.mergedOutput = mergedOutput;
    }

    @Override
    public void sort(InputStream inputStream) throws InMemorySortException {
        List<File> files = new ArrayList<>();
        try {
            log.trace("Start splitting");
            files = splitter.split(inputStream);
            log.trace("Start merging {} files", files.size());
            merger.merge(files, mergedOutput);
            log.trace("Finish merging files to the main stream");
        } catch (Throwable e) {
            log.error("Failed during In memory sort due to: {}", e.getMessage());
            log.debug("Failed during In memory sort due to:", e);
            throw new InMemorySortException("Failed during In memory sort due to:  ", e);
        } finally {
            files.forEach(File::delete);
        }
    }
}
