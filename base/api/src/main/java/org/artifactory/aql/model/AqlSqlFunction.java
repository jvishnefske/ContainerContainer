package org.artifactory.aql.model;

/**
 * An AQL API representation of a SQL function applied on some physical column
 *
 * @author Uriah Levy
 */
public record AqlSqlFunction(AqlFunctionFieldEnum functionFieldEnum,
                             AqlFunctionTableEnum functionTableEnum, boolean distinct) {
}
