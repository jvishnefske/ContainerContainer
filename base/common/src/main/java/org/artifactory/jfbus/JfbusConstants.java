package org.artifactory.jfbus;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author Tomer Nir
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JfbusConstants {
    public static final String HEADER_SPEC_VERSION = "specversion";
    public static final String HEADER_ID = "id";
    public static final String HEADER_TYPE = "type";
    public static final String HEADER_SOURCE = "source";
    public static final String HEADER_TIME = "time";
    public static final String HEADER_SUBJECT = "subject";
    public static final String HEADER_PROJECT = "project";
    public static final String HEADER_CONTENT_TYPE = "datacontenttype";
    public static final String HEADER_DATA_SCHEMA = "dataschema";
    public static final String HEADER_PACKAGE_TYPE = "packagetype";
    public static final String PACKAGE_CREATED_EVENT_TYPE = "com.jfrog.package.packagecreated";
    public static final String PACKAGE_DELETED_EVENT_TYPE = "com.jfrog.package.packagedeleted";
    public static final String PACKAGE_UPDATED_EVENT_TYPE = "com.jfrog.package.packageupdated";
    public static final String STATS_UPDATED_EVENT_TYPE = "com.jfrog.artifactory.stats.updated";
    public static final String STATS_EVENT_DATA_SCHEMA = "https://jfrog.com/artifactory/schemas/stats/v1";
    public static final String VIRTUAL_REPO_REF_REMOVAL_EVENT_TYPE =
            "com.jfrog.artifactory.virtual.repoRefRemoval";
    public static final String HEADER_VIRTUAL_FEDERATED = "virtualFederated";
}
