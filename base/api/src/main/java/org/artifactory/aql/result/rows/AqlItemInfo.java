package org.artifactory.aql.result.rows;

import java.util.Date;

import static org.artifactory.aql.model.AqlDomainEnum.itemInfos;

@QueryTypes(itemInfos)
public interface AqlItemInfo extends AqlRowResult {
    Long getItemInfoNodeId();

    Date getItemInfoModified();

    String getItemInfoModifiedBy();
}
