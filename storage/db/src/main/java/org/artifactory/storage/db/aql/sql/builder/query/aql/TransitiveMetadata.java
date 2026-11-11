package org.artifactory.storage.db.aql.sql.builder.query.aql;

import lombok.Data;

/**
 * @author Tomer Slutsky
 */
@Data
public class TransitiveMetadata {
    private int transitiveDepth;
    private boolean isTransitive = false;
    private String baseRepoName;
}
