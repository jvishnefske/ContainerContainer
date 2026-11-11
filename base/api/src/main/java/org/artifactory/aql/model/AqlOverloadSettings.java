package org.artifactory.aql.model;

import lombok.Builder;
import lombok.Data;
import org.artifactory.aql.api.AqlApiElement;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.result.AqlJsonStreamer;
import org.artifactory.aql.result.Row;

import java.util.List;
import java.util.function.Function;

/**
 * @author Omri Ziv
 */
@Data
@Builder
public class AqlOverloadSettings {
    private Long limit;
    private AqlSortTypeEnum sortType;
    @Builder.Default
    private int fetchSize = 0;
    private AqlPhysicalFieldEnum sortField;
    private AqlStreamFunc streamFunc;
    private List<DomainSensitiveField> resultFields;
    private boolean clearExistingResultFields;
    private List<AqlApiElement<AqlApiItem>> queryElementsExtension;
    private AqlOperatorEnum queryElementsExtensionOperatorEnum;
    private AqlSqlFunction sqlFunction;
    private Function<Row, Object> aqlRowMapper;

    @FunctionalInterface
    public interface AqlStreamFunc {
        List<AqlJsonStreamer> apply(String skip, AqlPermissionProvider aqlPermissionProvider,
                AqlRepoProvider aqlRepoProvider);
    }
}
