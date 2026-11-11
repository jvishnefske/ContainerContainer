package com.jfrog.build.entity;

import lombok.Builder;
import lombok.Getter;

import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Getter
@Builder
public class BuildEntityRecord {

    private static final int MAX_ALLOWED_USERNAME_LENGTH = 64;

    protected final long buildId;
    protected final String buildName;
    protected final String buildNumber;
    protected final long buildDate;
    protected final String ciUrl;
    protected final long created;
    protected final String createdBy;
    protected final long modified;
    protected final String modifiedBy;
    protected final String repo;

    public BuildEntityRecord(
            long buildId,
            String buildName,
            String buildNumber,
            long buildDate,
            String ciUrl,
            long created,
            String createdBy,
            long modified,
            String modifiedBy,
            String repo) {
        this.buildName = buildName;
        this.created = created;
        this.modified = modified;
        this.buildNumber = buildNumber;
        this.ciUrl = ciUrl;
        this.modifiedBy = abbreviate(modifiedBy, MAX_ALLOWED_USERNAME_LENGTH);
        this.buildDate = buildDate;
        this.buildId = buildId;
        this.createdBy = abbreviate(createdBy, MAX_ALLOWED_USERNAME_LENGTH);
        this.repo = repo;
        if (isBlank(buildName) || isBlank(buildNumber) || buildDate <= 0L) {
            throw new IllegalArgumentException("Build name, number and date cannot be empty or null!");
        }
    }
}
