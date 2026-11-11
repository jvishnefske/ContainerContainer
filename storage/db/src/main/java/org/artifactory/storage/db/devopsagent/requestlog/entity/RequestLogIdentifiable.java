package org.artifactory.storage.db.devopsagent.requestlog.entity;

/**
 * Minimal accessor contract shared by all request-log row types (inbound, outbound, …).
 * Consumed by {@link org.artifactory.storage.db.devopsagent.requestlog.service.AbstractRequestLogServiceImpl}
 * to build keyset-pagination {@link org.artifactory.storage.db.devopsagent.requestlog.model.Cursor}s
 * without knowing the concrete row type.
 */
public interface RequestLogIdentifiable {

    long id();

    long requestTime();

    int statusCode();

    String httpMethod();
}
