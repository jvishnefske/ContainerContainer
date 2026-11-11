package org.artifactory.livelogs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @author Andrey Tashlitsky
 */
@Data
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class LiveLogsData {

    @JsonProperty("last_update_modified")
    private Date lastUpdateModified;
    @JsonProperty("last_update_label")
    private Date lastUpdateLabel;
    @JsonProperty("log_content")
    private String logContent;
    @JsonProperty("file_size")
    private long fileSize;
}
