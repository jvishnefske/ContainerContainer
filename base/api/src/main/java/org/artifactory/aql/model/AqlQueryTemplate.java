package org.artifactory.aql.model;

/**
 * Normalized AQL query template and its checksum (used as map key in the AQL stats manager
 * and as the {@code aql_checksum} field in {@code artifactory-request.log}).
 *
 * @author Amir Avidan
 */
public record AqlQueryTemplate(String template, String checksum) {

    public static final String REQ_ATTR_KEY = "org.artifactory.aql.statistics.template";
}
