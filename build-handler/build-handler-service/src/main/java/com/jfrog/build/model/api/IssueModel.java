package com.jfrog.build.model.api;

import lombok.Builder;
import lombok.Data;
import org.jfrog.build.api.Issue;

@Data
@Builder
public class IssueModel {

    private String key;
    private String url;
    private String summary;
    private boolean aggregated;

    public static IssueModel from(Issue issue) {
        return IssueModel.builder()
                .key(issue.getKey())
                .url(issue.getUrl())
                .summary(issue.getSummary())
                .aggregated(issue.isAggregated())
                .build();
    }
}
