package org.artifactory.aql.result;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlQueryContext;
import org.artifactory.aql.model.AqlRepoProvider;
import org.jfrog.common.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 * This class goes through all of the received AqlJsonStreamers and returns the results
 * The order which is returned is local/cache first, then remote
 *
 * @author Tomer Slutsky
 */
public class AqlJsonMultiStreamer extends AqlRestResult implements AqlJsonStreamer {
    private static final Logger log = LoggerFactory.getLogger(AqlJsonMultiStreamer.class);

    private final List<AqlJsonStreamer> streamers;
    private final long limit;
    private final long offset;
    private final Row[] rows;
    private long rowsCount;
    private boolean isFirstElement = true;
    private boolean firstRead = true;
    private boolean finished = false;
    private long limitCounter;
    private int currIndex = 0;
    private Function<Row, Object> aqlRowMapper;

    public AqlJsonMultiStreamer(List<AqlJsonStreamer> streamers,
            AqlPermissionProvider permissionProvider, AqlRepoProvider repoProvider, long limit, long offset,
            AqlQueryContext aqlQueryContext) {
        super(permissionProvider, repoProvider, aqlQueryContext);
        this.streamers = streamers;
        this.rows = new Row[this.streamers.size()];
        this.limit = limit;
        this.limitCounter = limit;
        this.offset = offset;
    }

    public AqlJsonMultiStreamer(List<AqlJsonStreamer> streamers, AqlPermissionProvider permissionProvider,
            AqlRepoProvider repoProvider, long limit, long offset, Function<Row, Object> aqlRowMapper,
            AqlQueryContext aqlQueryContext) {
        super(permissionProvider, repoProvider, aqlQueryContext);
        this.streamers = streamers;
        this.rows = new Row[this.streamers.size()];
        this.limit = limit;
        this.limitCounter = limit;
        this.offset = offset;
        this.aqlRowMapper = aqlRowMapper;
    }

    @Override
    public byte[] read() {
        return read(true);
    }

    @Override
    public byte[] readCompact() {
        return read(false);
    }

    @Override
    public long getRowsCount() {
        return rowsCount;
    }

    private byte[] read(boolean prettyPrint) {
        if (this.firstRead) {
            log.trace("First read from the AqlJsonMultiStreamer, returning the wrapper");
            this.firstRead = false;
            return QUERY_PREFIX.getBytes();
        }

        // Initiates the null cells in the array if possible
        log.trace("Initiating the first object of each stream");
        this.initRows(this.rows);
        log.trace("Finding first non-null stream");
        this.currIndex = this.findFirstNonNullIndex();
        log.trace("Found first non-null stream in index: {}", this.currIndex);

        // Every index is null so we finished
        if (this.currIndex == this.rows.length + 1 || limitCounter == 0) {
            log.trace("All streams are null or limit counter reached");
            if (!this.finished) {
                log.trace("Returning range wrapper");
                this.finished = true;
                return this.appendEndSection(prettyPrint).getBytes();
            }

            log.trace("Ending stream reading");
            return null;
        }

        log.trace("Transforming current Row object to json string");
        Row currentRow = this.rows[currIndex];
        Object mappedRow = aqlRowMapper != null ? aqlRowMapper.apply(currentRow) : currentRow;
        String json = valueToString(mappedRow, prettyPrint);
        json = this.isFirstElement ? "" + json : "," + json;
        this.rows[currIndex] = null;
        if (isFirstElement) {
            isFirstElement = false;
        }
        this.rowsCount++;
        this.limitCounter--;

        log.trace("Returning the json string: {}", json);
        return json.getBytes();
    }

    private int findFirstNonNullIndex() {
        for (int i = this.currIndex; i < this.rows.length; i++) {
            if (this.rows[i] != null) {
                return i;
            }
        }

        return this.rows.length + 1;
    }

    private void initRows(Row[] rows) {
        for (int i = this.currIndex; i < this.streamers.size(); i++) {
            AqlJsonStreamer currStreamer = this.streamers.get(i);
            if (rows[i] == null) {
                Row readResult = currStreamer.readNext();
                if (readResult != null) {
                    rows[i] = readResult;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        for (AqlJsonStreamer streamer : this.streamers) {
            IOUtils.closeQuietly(streamer);
        }
    }

    public List<AqlJsonStreamer> getStreamers() {
        return this.streamers;
    }

    private String appendEndSection(boolean prettyPrint) {
        String range = valueToString(new Range(this.offset, this.rowsCount, this.rowsCount, this.limit), prettyPrint);
        return StringUtils.replace(QUERY_POSTFIX, NUMBER_OF_ROWS, "" + range);
    }

    private String valueToString(Object value, boolean prettyPrint) {
        return JsonUtils.getInstance().valueToString(value, prettyPrint);
    }

}
