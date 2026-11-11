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

package com.jfrog.build.model.api.diff;

/**
 * @author Chen Keinan
 */
public class BuildParams {

    private String currBuildNum;
    private String buildName;
    private String comperedBuildNum;
    private Long currBuildDate;
    private Long comperedBuildDate;
    private String buildModuleId;
    boolean isEnvProps = false;
    private String orderBy;
    private String direction;
    private String groupBy;
    private String offset;
    private String limit;
    private String buildRepo;

    public BuildParams(String moduleId, String buildNumber, String comparedBuildNum,
            String comparedDate, String buildStarted, String buildName, String buildRepo) {
        this.currBuildNum = buildNumber;
        this.comperedBuildNum = comparedBuildNum;
        if (buildStarted != null) {
            this.currBuildDate = Long.parseLong(buildStarted);
        }
        if (comparedBuildNum != null) {
            this.comperedBuildDate = Long.parseLong(comparedDate);
        }
        this.buildModuleId = moduleId;
        this.buildName = buildName;
        this.buildRepo = buildRepo;
    }

    public String getCurrBuildNum() {
        return currBuildNum;
    }

    public void setCurrBuildNum(String currBuildNum) {
        this.currBuildNum = currBuildNum;
    }

    public String getComperedBuildNum() {
        return comperedBuildNum;
    }

    public void setComperedBuildNum(String comperedBuildNum) {
        this.comperedBuildNum = comperedBuildNum;
    }

    public Long getCurrBuildDate() {
        return currBuildDate;
    }

    public void setCurrBuildDate(Long currBuildDate) {
        this.currBuildDate = currBuildDate;
    }

    public Long getComperedBuildDate() {
        return comperedBuildDate;
    }

    public void setComperedBuildDate(Long comperedBuildDate) {
        this.comperedBuildDate = comperedBuildDate;
    }

    public String getBuildModuleId() {
        return buildModuleId;
    }

    public void setBuildModuleId(String buildModuleId) {
        this.buildModuleId = buildModuleId;
    }

    public String getBuildName() {
        return buildName;
    }

    public void setBuildName(String buildName) {
        this.buildName = buildName;
    }

    public boolean isEnvProps() {
        return isEnvProps;
    }

    public void setIsEnvProps(boolean isEnvProps) {
        this.isEnvProps = isEnvProps;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(String groupBy) {
        this.groupBy = groupBy;
    }

    public String getOffset() {
        return offset;
    }

    public void setOffset(String offset) {
        this.offset = offset;
    }

    public String getLimit() {
        return limit;
    }

    public void setLimit(String limit) {
        this.limit = limit;
    }

    public void setEnvProps(boolean envProps) {
        isEnvProps = envProps;
    }

    public String getBuildRepo() {
        return buildRepo;
    }

    public void setBuildRepo(String buildRepo) {
        this.buildRepo = buildRepo;
    }
}
