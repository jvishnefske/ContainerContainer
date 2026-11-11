package org.artifactory.addon.retention;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/*
 * @author punyap
 */

@Data
public class EmptyRetentionSearchResult {
    @JsonProperty("results")
    private List<String> results;
    @JsonProperty("range")
    private EmptyRange range;

    public EmptyRetentionSearchResult() {
        this.results = Collections.emptyList();
        this.range = new EmptyRange();
    }

    @Data
    @AllArgsConstructor
    public static class EmptyRange {
        @JsonProperty("start_pos")
        private long startPos;
        @JsonProperty("end_pos")
        private long endPos;
        @JsonProperty("total")
        private long total;

        public EmptyRange() {
            this.startPos = 0;
            this.endPos = 0;
            this.total = 0;
        }
    }
}