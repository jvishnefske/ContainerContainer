package com.jfrog.build.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class BuildNumberInfo {

    private String buildRepo;
    private String buildName;
    private String buildNumber;
    private String ciUrl;
    private String buildStat;
    private String buildTime;
    private long time;
    private boolean canDelete;
}
