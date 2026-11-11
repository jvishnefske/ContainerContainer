package org.artifactory.api.callhome;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class CallHomeMetricInfo {
    @Builder.Default
    private String category = "api_usage";
    private List<CallHomeMetricFilterModel> filters;
    private String metric;
    private String type;
    private String value;

}