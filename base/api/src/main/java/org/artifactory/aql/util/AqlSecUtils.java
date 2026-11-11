package org.artifactory.aql.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.aql.result.Row;

import static org.artifactory.utils.ObfuscationUtils.obfuscateIfNeeded;

/**
 * @author Andrey Tashlitsky
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AqlSecUtils {

    public static Row hideUserRelatedInfoIfNeeded(Row row) {
        if (row != null) {
            row.itemCreatedBy = obfuscateIfNeeded(row.itemCreatedBy);
            row.itemModifiedBy = obfuscateIfNeeded(row.itemModifiedBy);
            row.itemInfosPropsModifiedBy = obfuscateIfNeeded(row.itemInfosPropsModifiedBy);
            row.statDownloadedBy = obfuscateIfNeeded(row.statDownloadedBy);
            row.statRemoteDownloadedBy = obfuscateIfNeeded(row.statRemoteDownloadedBy);
            row.buildCreatedBy = obfuscateIfNeeded(row.buildCreatedBy);
            row.buildModifiedBy = obfuscateIfNeeded(row.buildModifiedBy);
            row.buildPromotionCreatedBy = obfuscateIfNeeded(row.buildPromotionCreatedBy);
        }
        return row;
    }
}
