package com.jfrog.build.acl.model.api;

import lombok.Builder;
import lombok.Data;
import org.jfrog.build.api.Vcs;

import java.util.List;

/**
 * Transition Model for the UI since getting A build object requires full-read permission while getting the info
 * depicted here only requires basic-read.
 *
 * @author Dan Feldman
 */
@Builder
@Data
public class BuildGeneralInfo {

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
    private Long time;
    private String buildStat;
    private Boolean userCanDistribute;
    private Boolean isBuildFullView;
    private Boolean canManage;
    private Boolean canDelete;
    private String buildRepo;
    private String sha256;
    private List<Vcs> vcs;
}
