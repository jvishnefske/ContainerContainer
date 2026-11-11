package org.artifactory.aql.api.domain.sensitive;

import com.google.common.collect.Lists;
import org.artifactory.aql.api.internal.AqlApiDynamicFieldsDomains;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.result.rows.AqlItemInfo;

import java.util.ArrayList;

public class AqlApiItemInfo extends AqlBase<AqlApiItemInfo, AqlItemInfo> {
    public AqlApiItemInfo(boolean useDefaultResults) {
        super(AqlItemInfo.class, useDefaultResults);
    }

    /**
     * Creates a new base query with default all the fields included.
     */
    public static AqlApiItemInfo create() {
        return new AqlApiItemInfo(true);
    }

    /**
     * Creates a new base query with no fields included. User must call org.artifactory.aql.api.internal.AqlBase#include
     * to choose explicit fields.
     */
    public static AqlApiItemInfo createWithEmptyResults() {
        return new AqlApiItemInfo(false);
    }

    public static AqlApiDynamicFieldsDomains.AqlApiComparator<AqlApiItemInfo> nodeId() {
        ArrayList<AqlDomainEnum> subDomains = Lists.newArrayList(AqlDomainEnum.itemInfos);
        return new AqlApiDynamicFieldsDomains.AqlApiComparator<>(AqlPhysicalFieldEnum.itemInfosNodeId, subDomains);
    }

    public static AqlApiDynamicFieldsDomains.AqlApiComparator<AqlApiItemInfo> propsModifiedBy() {
        ArrayList<AqlDomainEnum> subDomains = Lists.newArrayList(AqlDomainEnum.itemInfos);
        return new AqlApiDynamicFieldsDomains.AqlApiComparator<>(AqlPhysicalFieldEnum.itemInfosPropsModifiedBy,
                subDomains);
    }

    public static AqlApiDynamicFieldsDomains.AqlApiComparator<AqlApiItemInfo> propsModified() {
        ArrayList<AqlDomainEnum> subDomains = Lists.newArrayList(AqlDomainEnum.itemInfos);
        return new AqlApiDynamicFieldsDomains.AqlApiComparator<>(AqlPhysicalFieldEnum.itemInfosPropsModified,
                subDomains);
    }

    public static AqlApiDynamicFieldsDomains.AqlApiComparator<AqlApiItemInfo> propsMd5() {
        ArrayList<AqlDomainEnum> subDomains = Lists.newArrayList(AqlDomainEnum.itemInfos);
        return new AqlApiDynamicFieldsDomains.AqlApiComparator<>(AqlPhysicalFieldEnum.itemInfosPropsMd5, subDomains);
    }

    public static AqlApiDynamicFieldsDomains.AqlApiItemDynamicFieldsDomains<AqlApiItemInfo> item() {
        ArrayList<AqlDomainEnum> subDomains = Lists.newArrayList(AqlDomainEnum.itemInfos, AqlDomainEnum.items);
        return new AqlApiDynamicFieldsDomains.AqlApiItemDynamicFieldsDomains(subDomains);
    }
}
