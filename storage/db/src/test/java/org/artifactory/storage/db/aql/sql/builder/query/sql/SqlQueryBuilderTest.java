package org.artifactory.storage.db.aql.sql.builder.query.sql;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlFunctionFieldEnum;
import org.artifactory.aql.model.AqlFunctionTableEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.itest.service.AqlAbstractServiceTest;
import org.artifactory.storage.db.aql.service.AqlQueryOptimizer;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlApiToAqlAdapter;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.querybuilder.DerbyQueryBuilder;
import org.jfrog.storage.util.querybuilder.IQueryBuilder;
import org.jfrog.storage.util.querybuilder.MysqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.OracleQueryBuilder;
import org.jfrog.storage.util.querybuilder.PostgresqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.SqlServerQueryBuilder;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.aql.api.internal.AqlBase.or;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class SqlQueryBuilderTest extends AqlAbstractServiceTest {

    @DataProvider
    public static Object[][] provide() {
        return new Object[][] {
                {"select * from (select distinct *  from  nodes n  where (( ( exists ( select 1 from node_props npsub" +
                        " where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value = ? )) and ( " +
                        "exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and npsub.prop_key =" +
                        " ? and npsub.prop_value = ? )))) and n.node_type = ?  union select distinct *  from  nodes n" +
                        "  where ( ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                        "npsub.prop_key = ? and npsub.prop_value = ? )) and ( exists ( select 1 from node_props npsub" +
                        " where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value = ? ))) and n" +
                        ".node_type = ? ) inner_query", AqlApiItem.createWithEmptyResults()
                        .distinct(true)
                        .filter(
                        or(
                                and(
                                        AqlApiItem.property().property("yossis", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("jungle", AqlComparatorEnum.equals, "value2")
                                ),
                                and(
                                        AqlApiItem.property().property("bla", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("bla2", AqlComparatorEnum.equals, "value2")
                                )
                        )
                )},
                {
                        "select * from (select *  from  nodes n  where (( ( exists ( select 1 from node_props npsub" +
                                " where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value = ? )) " +
                                "and ( " +
                                "exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and npsub" +
                                ".prop_key =" +
                                " ? and npsub.prop_value = ? )))) and n.node_type = ?  union all select *  from  " +
                                "nodes n" +
                                "  where ( ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id " +
                                "and " +
                                "npsub.prop_key = ? and npsub.prop_value = ? )) and ( exists ( select 1 from " +
                                "node_props npsub" +
                                " where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value = ? )))" +
                                " and n" +
                                ".node_type = ? ) inner_query", AqlApiItem.createWithEmptyResults()
                        .distinct(false)
                        .filter(
                        or(
                                and(
                                        AqlApiItem.property().property("yossis", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("jungle", AqlComparatorEnum.equals, "value2")
                                ),
                                and(
                                        AqlApiItem.property().property("bla", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("bla2", AqlComparatorEnum.equals, "value2")
                                )
                        ))
                },
                {
                        "select distinct *  from  nodes n  where  n.node_type = ? ", AqlApiItem.createWithEmptyResults()
                },
                {
                        "select count(*) from (select  distinct n.node_id  from  nodes n  where ( ( exists ( select 1" +
                                " from node_props npsub where npsub.node_id = n.node_id and npsub.prop_value like ? )" +
                                ")) and n.node_type = ?  union all select  distinct n.node_id  from  nodes n  where (" +
                                " ( " +
                                "exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and npsub" +
                                ".prop_key = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id =" +
                                " n.node_id and npsub.prop_key like ? ))) and n.node_type = ? ) inner_query",
                        AqlApiItem.createWithEmptyResults()
                                .sqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID)
                                .filter(
                                or(
                                        and(
                                                AqlApiItem.property().key().equal("yossia"),
                                                AqlApiItem.property().key().matches("*d")
                                        ),
                                        AqlApiItem.property().value().matches("ant")
                                )
                        )
                },
                {
                        "select  count(distinct n.node_id)  from  nodes n  where (( ( exists ( select 1 from " +
                                "node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? )) and ( " +
                                "exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and npsub" +
                                ".prop_key like ? ))) and ( exists ( select 1 from node_props npsub where npsub" +
                                ".node_id = n.node_id and npsub.prop_value like ? ))) and n.node_type = ? ",
                        AqlApiItem.createWithEmptyResults()
                                .sqlFunction(AqlFunctionFieldEnum.COUNT, AqlFunctionTableEnum.ARTIFACT_NODE_ID)
                                .filter(
                                and(
                                        and(
                                                AqlApiItem.property().key().equal("yossia"),
                                                AqlApiItem.property().key().matches("*d")
                                        ),
                                        AqlApiItem.property().value().matches("ant")
                                )
                        )
                },
                {
                        "select distinct *  from  nodes n  where (( ( exists ( select 1 from node_props npsub where " +
                                "npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value = ? )) and ( " +
                                "exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and npsub" +
                                ".prop_key = ? and npsub.prop_value = ? ))) and( ( exists ( select 1 from node_props " +
                                "npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value = " +
                                "? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id " +
                                "and npsub.prop_key = ? and npsub.prop_value = ? )))) and n.node_type = ? ",
                        AqlApiItem.createWithEmptyResults()
                                .filter(
                                and(
                                        and(
                                                AqlApiItem.property()
                                                        .property("yossis", AqlComparatorEnum.equals, "value1"),
                                                AqlApiItem.property()
                                                        .property("jungle", AqlComparatorEnum.equals, "value2")
                                        ),
                                        and(
                                                AqlApiItem.property()
                                                        .property("bla", AqlComparatorEnum.equals, "value1"),
                                                AqlApiItem.property()
                                                        .property("bla2", AqlComparatorEnum.equals, "value2")
                                        )
                                ))
                }
        };
    }

    @DataProvider
    public static Object[][] types() {
        return new Object[][] {
                {"select * from( select rownum rnum, inner_query.* from ( select  n.node_id as itemId  from " +
                        " nodes n  where  n.node_type = ?  order by itemId DESC ) inner_query where ROWNUM <= 2) " +
                        "where rnum > 1 ", DbType.ORACLE, false},
                {"select  n.node_id as itemId  from  nodes n  where  n.node_type = ?  order by itemId DESC  " +
                        "limit 1,1 ", DbType.MYSQL, false},
                {"select  n.node_id as itemId  from  nodes n  where  n.node_type = ?  order by itemId DESC  " +
                        "offset  1  limit  1 ", DbType.POSTGRESQL, false},
                {"select  n.node_id as itemId  from  nodes n  where  n.node_type = ?  order by itemId DESC  " +
                        "offset 1 rows  fetch first 1 rows only ", DbType.DERBY, false},
                {"Select bb.* From (Select aa.*  ,ROW_NUMBER() OVER ( order by aa.itemId DESC  ) AS RN  from ( select" +
                        "  n.node_id as itemId  from  nodes n  where  n.node_type = ?  ) aa ) bb Where bb.RN" +
                        " >= 2 AND bb.RN < 3 ", DbType.MSSQL, false},
                {"select * from (select * from( select rownum rnum, inner_query.* from ( select distinct  n.node_id " +
                        "as itemId  from  nodes n  where (( ( exists ( select 1 from node_props npsub where npsub" +
                        ".node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value = ? )) and ( exists ( " +
                        "select 1 from node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and " +
                        "npsub.prop_value = ? )))) and n.node_type = ?  union select distinct  n.node_id as itemId  " +
                        "from  nodes n  where ( ( exists ( select 1 from node_props npsub where npsub.node_id = n" +
                        ".node_id and npsub.prop_key = ? and npsub.prop_value = ? )) and ( exists ( select 1 from " +
                        "node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value" +
                        " = ? ))) and n.node_type = ? ) inner_query order by itemId DESC ) inner_query where ROWNUM " +
                        "<= 2) where rnum > 1 ", DbType.ORACLE, true},
                {"select * from (select distinct  n.node_id as itemId  from  nodes n  where (( ( exists ( select 1 " +
                        "from node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub" +
                        ".prop_value = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n" +
                        ".node_id and npsub.prop_key = ? and npsub.prop_value = ? )))) and n.node_type = ?  union " +
                        "select distinct  n.node_id as itemId  from  nodes n  where ( ( exists ( select 1 from " +
                        "node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value" +
                        " = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                        "npsub.prop_key = ? and npsub.prop_value = ? ))) and n.node_type = ? ) inner_query order by " +
                        "itemId DESC  offset  1  limit  1 ", DbType.POSTGRESQL, true},
                {"select * from (select distinct  n.node_id as itemId  from  nodes n  where (( ( exists ( select 1 " +
                        "from node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub" +
                        ".prop_value = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n" +
                        ".node_id and npsub.prop_key = ? and npsub.prop_value = ? )))) and n.node_type = ?  union " +
                        "select distinct  n.node_id as itemId  from  nodes n  where ( ( exists ( select 1 from " +
                        "node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value" +
                        " = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                        "npsub.prop_key = ? and npsub.prop_value = ? ))) and n.node_type = ? ) inner_query order by " +
                        "itemId DESC  offset 1 rows  fetch first 1 rows only ", DbType.DERBY, true},
                {"select * from (select distinct  n.node_id as itemId  from  nodes n  where (( ( exists ( select 1 " +
                        "from node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub" +
                        ".prop_value = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n" +
                        ".node_id and npsub.prop_key = ? and npsub.prop_value = ? )))) and n.node_type = ?  union " +
                        "select distinct  n.node_id as itemId  from  nodes n  where ( ( exists ( select 1 from " +
                        "node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value" +
                        " = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                        "npsub.prop_key = ? and npsub.prop_value = ? ))) and n.node_type = ? ) inner_query order by " +
                        "itemId DESC  limit 1,1 ", DbType.MYSQL, true},
                {"select * from (Select bb.* From (Select aa.*  ,ROW_NUMBER() OVER ( order by aa.itemId DESC  ) AS RN" +
                        "  from ( select distinct  n.node_id as itemId  from  nodes n  where (( ( exists ( select 1 " +
                        "from node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub" +
                        ".prop_value = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n" +
                        ".node_id and npsub.prop_key = ? and npsub.prop_value = ? )))) and n.node_type = ?  union " +
                        "select distinct  n.node_id as itemId  from  nodes n  where ( ( exists ( select 1 from " +
                        "node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value" +
                        " = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                        "npsub.prop_key = ? and npsub.prop_value = ? ))) and n.node_type = ?  ) aa ) bb Where bb.RN " +
                        ">= 2 AND bb.RN < 3 ) inner_query", DbType.MSSQL, true}
        };
    }

    @DataProvider
    public static Object[][] distinct() {
        return new Object[][] {{
                "select * from (select * from( select rownum rnum, inner_query.* from ( select  n.node_id " +
                        "as itemId  from  nodes n  where (( ( exists ( select 1 from node_props npsub where npsub" +
                        ".node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value = ? )) and ( exists ( " +
                        "select 1 from node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and " +
                        "npsub.prop_value = ? )))) and n.node_type = ?  union all select  n.node_id as itemId  " +
                        "from  nodes n  where ( ( exists ( select 1 from node_props npsub where npsub.node_id = n" +
                        ".node_id and npsub.prop_key = ? and npsub.prop_value = ? )) and ( exists ( select 1 from " +
                        "node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value" +
                        " = ? ))) and n.node_type = ? ) inner_query order by itemId DESC ) inner_query where ROWNUM " +
                        "<= 2) where rnum > 1 ", DbType.ORACLE},
                {"select * from (select  n.node_id as itemId  from  nodes n  where (( ( exists ( select 1 " +
                        "from node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub" +
                        ".prop_value = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n" +
                        ".node_id and npsub.prop_key = ? and npsub.prop_value = ? )))) and n.node_type = ?  union all" +
                        " " +
                        "select  n.node_id as itemId  from  nodes n  where ( ( exists ( select 1 from " +
                        "node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value" +
                        " = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                        "npsub.prop_key = ? and npsub.prop_value = ? ))) and n.node_type = ? ) inner_query order by " +
                        "itemId DESC  offset  1  limit  1 ", DbType.POSTGRESQL},
                {"select * from (select  n.node_id as itemId  from  nodes n  where (( ( exists ( select 1 " +
                        "from node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub" +
                        ".prop_value = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n" +
                        ".node_id and npsub.prop_key = ? and npsub.prop_value = ? )))) and n.node_type = ?  union all" +
                        " " +
                        "select  n.node_id as itemId  from  nodes n  where ( ( exists ( select 1 from " +
                        "node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value" +
                        " = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                        "npsub.prop_key = ? and npsub.prop_value = ? ))) and n.node_type = ? ) inner_query order by " +
                        "itemId DESC  offset 1 rows  fetch first 1 rows only ", DbType.DERBY},
                {"select * from (select  n.node_id as itemId  from  nodes n  where (( ( exists ( select 1 " +
                        "from node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub" +
                        ".prop_value = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n" +
                        ".node_id and npsub.prop_key = ? and npsub.prop_value = ? )))) and n.node_type = ?  union all" +
                        " " +
                        "select  n.node_id as itemId  from  nodes n  where ( ( exists ( select 1 from " +
                        "node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value" +
                        " = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                        "npsub.prop_key = ? and npsub.prop_value = ? ))) and n.node_type = ? ) inner_query order by " +
                        "itemId DESC  limit 1,1 ", DbType.MYSQL},
                {"select * from (Select bb.* From (Select aa.*  ,ROW_NUMBER() OVER ( order by aa.itemId DESC  ) AS RN" +
                        "  from ( select  n.node_id as itemId  from  nodes n  where (( ( exists ( select 1 " +
                        "from node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub" +
                        ".prop_value = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n" +
                        ".node_id and npsub.prop_key = ? and npsub.prop_value = ? )))) and n.node_type = ?  union all" +
                        " " +
                        "select  n.node_id as itemId  from  nodes n  where ( ( exists ( select 1 from " +
                        "node_props npsub where npsub.node_id = n.node_id and npsub.prop_key = ? and npsub.prop_value" +
                        " = ? )) and ( exists ( select 1 from node_props npsub where npsub.node_id = n.node_id and " +
                        "npsub.prop_key = ? and npsub.prop_value = ? ))) and n.node_type = ?  ) aa ) bb Where bb.RN " +
                        ">= 2 AND bb.RN < 3 ) inner_query", DbType.MSSQL}};
    }

    @BeforeMethod
    public void setup() {
        bindDummyContext();
        bindArtifactoryHome();
    }

    @AfterMethod
    public void teardown() {
        unbindDummyContext();
        unbindArtifactoryHome();
    }

    @Test(dataProvider = "provide")
    public void testBuildQuery(String expected, AqlBase base) {
        AqlApiToAqlAdapter aqlApiToAqlAdapter = new AqlApiToAqlAdapter();
        AqlQueryOptimizer optimizer = new AqlQueryOptimizer(dbProperties.getDbType());
        AqlQuery<AqlRowResult> query = aqlApiToAqlAdapter.toAqlModel(base);
        optimizer.optimize(query);
        SqlQueryBuilder builder = new SqlQueryBuilder();
        SqlQuery<AqlRowResult> aqlRowResultSqlQuery = builder.buildQuery(query);
        String queryFinal = aqlRowResultSqlQuery.getQuery();
        assertEquals(queryFinal, expected);
    }

    @Test(dataProvider = "types")
    public void testBuildQueryCommonFields(String expected, DbType type, boolean isUnion) {
        ArtifactoryContext dummyContext = Mockito.mock(ArtifactoryContext.class);
        unbindDummyContext();
        when(dummyContext.beanForType(IQueryBuilder.class)).thenReturn(getQueryBuilder(type));
        ArtifactoryContextThreadBinder.bind(dummyContext);
        AqlQueryOptimizer optimizer = new AqlQueryOptimizer(type);
        AqlQuery query = getQuery(isUnion);
        optimizer.optimize(query);
        SqlQueryBuilder builder = new SqlQueryBuilder();
        SqlQuery<AqlRowResult> aqlRowResultSqlQuery = builder.buildQuery(query);
        String queryFinal = aqlRowResultSqlQuery.getQuery();
        assertEquals(queryFinal, expected);
    }

    @Test(dataProvider = "distinct")
    public void testBuildQueryCommonFieldsNotDistinct(String expected, DbType type) {
        ArtifactoryContext dummyContext = Mockito.mock(ArtifactoryContext.class);
        unbindDummyContext();
        when(dummyContext.beanForType(IQueryBuilder.class)).thenReturn(getQueryBuilder(type));
        ArtifactoryContextThreadBinder.bind(dummyContext);
        AqlQueryOptimizer optimizer = new AqlQueryOptimizer(type);
        AqlQuery query = getQueryWithoutDistinct();
        optimizer.optimize(query);
        SqlQueryBuilder builder = new SqlQueryBuilder();
        SqlQuery<AqlRowResult> aqlRowResultSqlQuery = builder.buildQuery(query);
        String queryFinal = aqlRowResultSqlQuery.getQuery();
        assertEquals(queryFinal, expected);
    }

    private AqlQuery getQueryWithoutDistinct() {
        AqlApiToAqlAdapter aqlApiToAqlAdapter = new AqlApiToAqlAdapter();
        return aqlApiToAqlAdapter.toAqlModel(
                AqlApiItem.createWithEmptyResults().filter(
                        or(
                                and(
                                        AqlApiItem.property()
                                                .property("yossis", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("jungle", AqlComparatorEnum.equals, "value2")
                                ),
                                and(
                                        AqlApiItem.property().property("bla", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("bla2", AqlComparatorEnum.equals, "value2")
                                )
                        )
                ).distinct(false).addSortElement(AqlApiItem.itemId()).limit(1).offset(1));
    }

    private AqlQuery getQuery(boolean isUnion) {
        AqlApiToAqlAdapter aqlApiToAqlAdapter = new AqlApiToAqlAdapter();
        return isUnion ? aqlApiToAqlAdapter.toAqlModel(
                AqlApiItem.createWithEmptyResults().filter(
                        or(
                                and(
                                        AqlApiItem.property()
                                                .property("yossis", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("jungle", AqlComparatorEnum.equals, "value2")
                                ),
                                and(
                                        AqlApiItem.property().property("bla", AqlComparatorEnum.equals, "value1"),
                                        AqlApiItem.property().property("bla2", AqlComparatorEnum.equals, "value2")
                                )
                        )
                ).addSortElement(AqlApiItem.itemId()).limit(1).offset(1))
                :
                aqlApiToAqlAdapter.toAqlModel(
                        AqlApiItem.createWithEmptyResults().addSortElement(AqlApiItem.itemId()).limit(1).offset(1));
    }

    private IQueryBuilder getQueryBuilder(DbType type) {
        return switch (type) {
            case DERBY -> new DerbyQueryBuilder();
            case MARIADB, MYSQL -> new MysqlQueryBuilder();
            case ORACLE -> new OracleQueryBuilder();
            case MSSQL -> new SqlServerQueryBuilder();
            case POSTGRESQL -> new PostgresqlQueryBuilder();
        };
    }
}