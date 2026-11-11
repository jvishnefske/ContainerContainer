/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.aql.result;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.action.AqlAction;
import org.artifactory.aql.action.AqlActionException;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlQueryContext;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.aql.result.rows.InflatableRow;
import org.artifactory.aql.result.rows.populate.PhysicalFieldResultPopulators;
import org.artifactory.aql.result.rows.populate.ResultPopulationContext;
import org.artifactory.aql.result.rows.populate.RowPopulation;
import org.artifactory.aql.result.rows.populate.RowPopulationContext;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.common.ConstantValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.artifactory.aql.util.AqlSecUtils.hideUserRelatedInfoIfNeeded;

/**
 * @author Gidi Shabat
 * The class converts the AqlLazyResult to in-memory Aql Json result
 * The Max number of rows allowed by this result is the actually artifactory searchUserQueryLimit:
 * (ConstantValues.searchUserQueryLimit)
 */
public class AqlJsonLocalStreamer extends AqlRestResult implements Cloneable, AqlJsonStreamer {
    private static final Logger log = LoggerFactory.getLogger(AqlJsonLocalStreamer.class);

    private final ResultSet resultSet;
    private final List<DomainSensitiveField> fields;
    private final long limit;
    private final long offset;
    private final AqlRepoProvider repoProvider;
    private final AqlDomainEnum domain;
    //used for the user plugin where we don't want the wrappers as a part of the JSON
    private boolean skipResultWrapper;
    private final AqlAction action;
    private long rowsCount;
    private long singleRowsCount;
    private Buffer buffer = new Buffer();
    private boolean ended;
    private String mainId = null;
    private Row mainRow;
    private boolean aqlLimitByConfiguration;

    public AqlJsonLocalStreamer(AqlLazyResult<? extends AqlRowResult> lazyResult, AqlQueryContext aqlQueryContext,
            boolean aqlLimitByConfiguration) {
        this(lazyResult, false, aqlQueryContext);
        this.aqlLimitByConfiguration = aqlLimitByConfiguration;
    }

    public AqlJsonLocalStreamer(AqlLazyResult<? extends AqlRowResult> lazyResult, boolean skipResultWrapper,
            AqlQueryContext aqlQueryContext) {
        super(lazyResult.getPermissionProvider(), lazyResult.getRepoProvider(), aqlQueryContext);
        repoProvider = lazyResult.getRepoProvider();
        resultSet = lazyResult.getResultSet();
        fields = lazyResult.getFields();
        limit = lazyResult.getLimit();
        offset = lazyResult.getOffset();
        domain = lazyResult.getDomain();
        action = lazyResult.getAction();
        this.skipResultWrapper = skipResultWrapper;
        if (!skipResultWrapper) {
            buffer.push(QUERY_PREFIX.getBytes());
        }
    }

    public AqlRepoProvider getRepoProvider() {
        return this.repoProvider;
    }

    public List<DomainSensitiveField> getFields() {
        return this.fields;
    }

    public ResultSet getResultSet() {
        return this.resultSet;
    }

    public AqlAction getAction() {
        return this.action;
    }

    public void setSkipResultWrapperTrue() {
        this.buffer = new Buffer();
        this.skipResultWrapper = true;
    }

    /**
     * Read the ResultSet from db:
     * 1. In case of multi domain result the method merge multi rows into Json multi layer json result
     * 2. In case of single domain result the class convert single row into flat json.
     */
    private Row inflateRow() {
        if (hasReachedLimit()) {
            return null;
        }
        try {
            ResultPopulationContext resultContext = new ResultPopulationContext(resultSet, fields,
                    null, repoProvider);
            while (resultSet.next()) {
                InflatableRow row = new InflatableRow();
                RowPopulationContext populationContext = new RowPopulationContext(resultContext, row);
                RowPopulation.populatePhysicalFields(populationContext, PhysicalFieldResultPopulators.forJson);
                RowPopulation.populateLogicalFields(populationContext);
                singleRowsCount++;
                if (!canRead(domain, resultSet)) {
                    continue;
                }
                Map<String, Row> map = row.inflate();
                String newId = map.keySet().iterator().next();
                Row newRow = map.values().iterator().next();
                Row result = processRowWithMerging(newId, newRow);
                if (result != null) {
                    return result;
                }
            }
        } catch (Exception e) {
            throw new AqlException("Failed to fetch Aql result", e);
        }
        return handleFinalRows();
    }

    /**
     * Processes a row with merging logic based on whether it has the same ID as the current main row.
     * Returns a Row if it should be returned immediately, null if processing should continue.
     */
    private Row processRowWithMerging(String newId, Row newRow) {
        if (mainId == null) {
            mainId = newId;
            mainRow = newRow;
            return null;
        }
        if (!mainId.equals(newId)) {
            Row temp = mainRow;
            mainRow = newRow;
            mainId = newId;
            Row currentRow = actOnRow(temp.build());
            if (currentRow != null) {
                return hideUserRelatedInfoIfNeeded(currentRow);
            }
        } else {
            mainRow.merge(newRow);
        }
        return null;
    }

    private boolean hasReachedLimit() {
        return getAqlQueryContext() != null
                && getAqlQueryContext().limit() > 0
                && rowsCount >= getAqlQueryContext().limit();
    }

    private Row handleFinalRows() {
        if (mainRow != null && !isRowExceededMax()) {
            Row row = mainRow;
            mainRow = null;
            return hideUserRelatedInfoIfNeeded(actOnRow(row.build()));
        } else {
            return null;
        }
    }

    private boolean isRowExceededMax() {
        return ConstantValues.aqlSearchVerifyMergedEntities.getBoolean()
                && aqlLimitByConfiguration
                && singleRowsCount > ConstantValues.aqlSearchQueryMaxLimit.getInt();
    }

    /**
     * Performs the {@link AqlAction} this streamer was inited with.
     *
     * @return the current row if the action was successful, null otherwise.
     */
    @Nullable
    private Row actOnRow(Row row) {
        try {
            return action.doAction(row);
        } catch (AqlActionException aae) {
            String warn = (action.isDryRun() ? "** AQL dry run mode ** " : "") + aae.getReason().code() + " : " +
                    aae.getMessage();
            log.warn(warn);
            log.debug("", aae);
        } catch (Exception e) {
            log.warn(String.format("Failed to execute action %s : {}", action.getName()), e.getMessage());
            log.debug("", e);
        }
        // Don't return rows that failed the action.
        return null;
    }

    @Override
    public byte[] read() {
        return read(true);
    }

    @Override
    public byte[] readCompact() {
        return read(false);
    }

    /**
     * Reads Single row from The Json result
     * The method return null to signal end of stream
     *
     * @return Json row in byte array
     */
    private byte[] read(boolean prettyPrint) {
        // Use the data in the buffer, reloading  the buffer is allowed only if it is empty
        if (!buffer.isEmpty()) {
            return buffer.getData();
        }
        // Fill the buffer from result-set
        byte[] data;
        if ((data = getNewRowFromDb(prettyPrint)) != null) {
            rowsCount++;
            buffer.push(data);
            return buffer.getData();
        }
        // Fill the buffer from post fix
        if (!ended && !skipResultWrapper) {
            appendEndSection(prettyPrint);
            return buffer.getData();
        }
        return null;
    }

    private void appendEndSection(boolean prettyPrint) {
        try {
            if (!ended) {
                String range = generateRangeJson(prettyPrint);
                String summary = StringUtils.replace(QUERY_POSTFIX, NUMBER_OF_ROWS, "" + range);
                buffer.push(summary.getBytes());
                ended = true;
            }
        } catch (IOException e) {
            log.error("Failed to generate Aql result summery.", e);
        }
    }

    public long getLimit() {
        return limit;
    }

    public long getOffset() {
        return offset;
    }

    @Override
    public long getRowsCount() {
        return rowsCount;
    }

    private String writeValueAsString(Object value, boolean prettyPrint) throws JsonProcessingException {
        return prettyPrint ? mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
                : mapper.writeValueAsString(value);
    }

    private String generateRangeJson(boolean prettyPrint) throws IOException {
        Range range = new Range(offset, rowsCount, rowsCount, getLimitForRange());
        return writeValueAsString(range, prettyPrint);
    }

    private long getLimitForRange() {
        if (aqlLimitByConfiguration) {
            return ConstantValues.aqlSearchQueryMaxLimit.getInt();
        }
        AqlQueryContext context = getAqlQueryContext();
        return context != null ? context.limit() : limit;
    }

    private byte[] getNewRowFromDb(boolean prettyPrint) {
        boolean isFirstElement = mainId == null;
        Row row = inflateRow();
        if (row != null) {
            try {
                String json = writeValueAsString(row, prettyPrint);
                json = isFirstElement || skipResultWrapper ? "" + json : "," + json;
                return json.getBytes();
            } catch (Exception e) {
                throw new AqlException("Failed to convert Aql Result to JSON", e);
            }
        }
        return null;
    }

    @Override
    public Row readNext() {
        log.trace("Inflating Row object for local streamer");
        return inflateRow();
    }

    @Override
    public void close() {
        AqlUtils.closeResultSet(resultSet);
    }

    /**
     * Simplify the work with the stream during the read
     */
    private class Buffer {
        private byte[] buffer;

        public void push(byte[] bytes) {
            buffer = bytes;
        }

        public byte[] getData() {
            byte[] temp = buffer;
            buffer = null;
            return temp;
        }

        public boolean isEmpty() {
            return buffer == null;
        }
    }

}