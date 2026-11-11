package com.jfrog.build.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jfrog.build.api.Vcs;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralBuildInfo {

    private String buildName;
    private String buildNumber;
    private String ciUrl;
    private String releaseStatus;
    private String agent;
    private String buildAgent;
    private String lastBuildTime;
    private String duration;
    private String principal;
    private String artifactoryPrincipal;
    private String url;
    private String buildRepo;
    private Long time;
    private String buildStat;
    private Boolean buildFullView;
    private Boolean canManage;
    private Boolean canDelete;
    private String sha256;
    private List<Vcs> vcs;
}
