package com.jfrog.build.acl.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

@Builder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeneralBuild {

    private long buildId;
    private String buildName;
    private String buildNumber;
    private long buildDate;
    private String ciUrl;
    private long created;
    private String createdBy;
    private long modified;
    private String modifiedBy;
    private String numOfModules;
    private String numOfArtifacts;
    private String numOfDependencies;
    private String status;
    private String buildRepo;

    public GeneralBuild(
            String buildName,
            String buildNumber,
            long buildDate,
            String ciUrl,
            String status,
            String buildRepo) {
        this.buildName = buildName;
        this.buildNumber = buildNumber;
        this.buildDate = buildDate;
        this.ciUrl = ciUrl;
        this.status = status;
        this.buildRepo = buildRepo;
    }

    public GeneralBuild(
            long buildId,
            String buildName,
            String buildNumber,
            long buildDate,
            String ciUrl,
            long created,
            String createdBy,
            long modified,
            String modifiedBy,
            String buildRepo) {
        if (buildId <= 0L) {
            throw new IllegalArgumentException("Build id cannot be zero or negative!");
        }
        if (StringUtils.isBlank(buildName) || StringUtils.isBlank(buildNumber) || buildDate <= 0L) {
            throw new IllegalArgumentException("Build name, number and date cannot be empty or null!");
        }
        if (created <= 0L) {
            throw new IllegalArgumentException("Created date cannot be zero or negative!");
        }
        this.buildId = buildId;
        this.buildName = buildName;
        this.buildNumber = buildNumber;
        this.buildDate = buildDate;
        this.ciUrl = ciUrl;
        this.created = created;
        this.createdBy = createdBy;
        this.modified = modified;
        this.modifiedBy = modifiedBy;
        this.buildRepo = buildRepo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GeneralBuild that = (GeneralBuild) o;
        return buildDate == that.buildDate &&
                buildName.equals(that.buildName) &&
                buildNumber.equals(that.buildNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildName, buildNumber, buildDate);
    }
}
