package org.artifactory.storage.db.aql.sql.model;

import org.artifactory.aql.model.AqlFunctionTableEnum;
import org.artifactory.aql.model.AqlTableFieldsEnum;

import static org.artifactory.aql.model.AqlTableFieldsEnum.node_id;

/**
 * This enum represents an enumeration of table->node_id tuples that can be chosen to apply some SQL function on. We
 * limit the list of options and specify them explicitly in this enum because AQL assigns dynamic aliases to some
 * table names when translating AQL into a SQL query. Essentially this list will only contain tables to which AQL
 * will never assign a dynamic ID on the query thus giving us the option to determine the table/column that we will
 * apply the function on.
 *
 * @author Tamar Rosen
 */
public enum AqlFunctionTableExtensionEnum {
    NODES_NODE_ID(AqlFunctionTableEnum.ARTIFACT_NODE_ID, SqlTableEnum.nodes, node_id);

    private final AqlFunctionTableEnum functionTableEnum;
    private final SqlTableEnum table;
    private final AqlTableFieldsEnum tableField;

    AqlFunctionTableExtensionEnum(AqlFunctionTableEnum functionTableEnum, SqlTableEnum table,
            AqlTableFieldsEnum tableField) {
        this.functionTableEnum = functionTableEnum;
        this.table = table;
        this.tableField = tableField;
    }

    public static AqlFunctionTableExtensionEnum getExtensionFor(AqlFunctionTableEnum functionTable) {
        for (AqlFunctionTableExtensionEnum functionTableExtensions : values()) {
            if (functionTableExtensions.functionTableEnum == functionTable) {
                return functionTableExtensions;
            }
        }
        return null;
    }

    public SqlTableEnum getTable() {
        return table;
    }

    public AqlTableFieldsEnum getTableField() {
        return tableField;
    }
}
