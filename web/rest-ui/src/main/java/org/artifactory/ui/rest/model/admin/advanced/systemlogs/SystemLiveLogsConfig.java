package org.artifactory.ui.rest.model.admin.advanced.systemlogs;

import org.artifactory.rest.common.model.BaseModel;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author Hezi Cohen
 */
public class SystemLiveLogsConfig extends BaseModel {
    @JsonProperty("refresh_rate_millis")
    private long refreshRateMillis;

    private List<String> logs;

    public List<String> getLogs() {
        return logs;
    }

    public void setLogs(List<String> logs) {
        this.logs = logs;
    }

    public long getRefreshRateMillis() {
        return refreshRateMillis;
    }

    public void setRefreshRateMillis(long refreshRateMillis) {
        this.refreshRateMillis = refreshRateMillis;
    }
}