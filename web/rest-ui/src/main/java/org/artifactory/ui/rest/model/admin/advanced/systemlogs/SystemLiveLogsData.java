package org.artifactory.ui.rest.model.admin.advanced.systemlogs;

import org.artifactory.rest.common.model.BaseModel;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

/**
 * @author Hezi Cohen
 */
public class SystemLiveLogsData extends BaseModel {
    @JsonProperty("last_update_modified")
    private Date lastUpdateModified;

    @JsonProperty("last_update_label")
    private Date lastUpdateLabel;

    @JsonProperty("log_content")
    private String logContent;

    @JsonProperty("file_size")
    private long fileSize;

    public long getFileSize() {
        return fileSize;
    }

    public Date getLastUpdateModified() {
        return lastUpdateModified;
    }

    public Date getLastUpdateLabel() {
        return lastUpdateLabel;
    }

    public String getLogContent() {
        return logContent;
    }

    public void setLastUpdateModified(Date lastUpdateModified) {
        this.lastUpdateModified = lastUpdateModified;
    }

    public void setLastUpdateLabel(Date lastUpdateLabel) {
        this.lastUpdateLabel = lastUpdateLabel;
    }

    public void setLogContent(String logContent) {
        this.logContent = logContent;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
}
