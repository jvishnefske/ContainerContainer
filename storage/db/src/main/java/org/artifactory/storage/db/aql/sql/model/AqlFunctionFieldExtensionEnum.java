package org.artifactory.storage.db.aql.sql.model;

import org.artifactory.aql.model.AqlFunctionFieldEnum;
import org.artifactory.aql.model.AqlSqlFunction;

import java.util.function.Function;

/**
 * @author Uriah Levy
 */
public enum AqlFunctionFieldExtensionEnum {
    COUNT(AqlFunctionFieldEnum.COUNT, AqlFunctionFieldExtensionEnum::countMapper);

    private final AqlFunctionFieldEnum aqlFunctionFieldEnum;
    private final Function<AqlSqlFunction, String> canonicalFormMapper;

    AqlFunctionFieldExtensionEnum(AqlFunctionFieldEnum aqlFunctionFieldEnum,
            Function<AqlSqlFunction, String> canonicalFormMapper) {
        this.aqlFunctionFieldEnum = aqlFunctionFieldEnum;
        this.canonicalFormMapper = canonicalFormMapper;
    }

    public static AqlFunctionFieldExtensionEnum getExtensionFor(AqlFunctionFieldEnum field) {
        for (AqlFunctionFieldExtensionEnum fieldExtensionEnum : values()) {
            if (fieldExtensionEnum.aqlFunctionFieldEnum == field) {
                return fieldExtensionEnum;
            }
        }
        return null;
    }

    public String toCanonicalSqlForm(AqlSqlFunction aqlSqlFunction) {
        return canonicalFormMapper.apply(aqlSqlFunction);
    }

    private static String countMapper(AqlSqlFunction aqlSqlFunction) {
        AqlFunctionTableExtensionEnum functionTableExtensionEnum =
                AqlFunctionTableExtensionEnum.getExtensionFor(aqlSqlFunction.functionTableEnum());
        String columnTableString =
                functionTableExtensionEnum.getTable().alias + "." + functionTableExtensionEnum.getTableField();
        if (aqlSqlFunction.distinct()) {
            return String.format("count(distinct %s)", columnTableString);
        }
        return String.format("count(%s)", columnTableString);
    }
}
