package org.artifactory.storage.db.aql.service.decorator;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.domain.sensitive.AqlApiProperty;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlApiToAqlAdapter;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.DerbyQueryBuilder;
import org.jfrog.storage.util.querybuilder.IQueryBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.aql.api.internal.AqlBase.or;
import static org.artifactory.mime.DebianNaming.DISTRIBUTION_PROP;
import static org.artifactory.mime.DebianNaming.ARCHITECTURE_PROP;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class TrashcanDecoratorTest {

    private final AqlApiToAqlAdapter apiToAqlAdapter = new AqlApiToAqlAdapter();
    private final TrashcanDecorator decorator = new TrashcanDecorator();
    private final SqlQueryBuilder sqlBuilder = new SqlQueryBuilder();

    @BeforeMethod
    public void before() {
        // Query syntax not important for test.
        // SQL builder required for comparing results without access to internal state of AqlQuery.
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(IQueryBuilder.class))
                .thenReturn(new DerbyQueryBuilder());

        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    public void after() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test(dataProvider = "queries")
    public void testSearch(String testName, AqlBase<?, ?> aql, String sql) {
        AqlQuery<?> aqlQuery = apiToAqlAdapter.toAqlModel(aql);
        decorator.decorate(aqlQuery, null);

        assertEquals(sqlBuilder.buildQuery(aqlQuery).getQueryString(), sql);
    }

    @DataProvider(name = "queries")
    public static Object[][] queries() {
        return new Object[][] {
                {
                        // Filter by repo. Trash items already not in results.
                        "propertiesByRepoAndProperty",
                        AqlApiProperty.create().filter(and(
                                AqlApiProperty.item().repo().equal("test-repo"),
                                AqlApiProperty.key().equal(DISTRIBUTION_PROP)
                        )),
                        "select distinct  np.prop_key as propertyKey,np.prop_value as propertyValue  " +
                                "from  node_props np " +
                                "left outer join nodes n on n.node_id = np.node_id " +
                                "left outer join node_props np100 on np100.node_id = np.node_id  " +
                                "where ( n.repo = ? and np100.prop_key = ?) "
                },
                {
                        // Filter by repo OR global search. Trash items should be excluded.
                        "propertiesByRepoOrProperty",
                        AqlApiProperty.create().filter(or(
                                AqlApiProperty.item().repo().equal("test-repo"),
                                AqlApiProperty.key().equal(DISTRIBUTION_PROP)
                        )),
                        "select distinct  np.prop_key as propertyKey,np.prop_value as propertyValue  " +
                                "from  node_props np " +
                                "left outer join nodes n on n.node_id = np.node_id " +
                                "left outer join node_props np100 on np100.node_id = np.node_id  " +
                                "where (( n.repo = ? or np100.prop_key = ?)) " +
                                "and(np.node_id is null or not exists (" +
                                "select 1 from node_props where np.node_id = node_id and prop_key = ?" +
                                ")) "
                },
                {
                        // Filter by nested criteria - AND(OR, OR). Trash items should be excluded.
                        "propertiesByNestedRepoFilterWithOr",
                        AqlApiProperty.create().filter(and(
                                or(
                                        AqlApiProperty.item().repo().equal("test-repo"),
                                        AqlApiProperty.key().equal(DISTRIBUTION_PROP)
                                ),
                                or(
                                        AqlApiProperty.key().equal(ARCHITECTURE_PROP),
                                        AqlApiProperty.key().equal(DISTRIBUTION_PROP)
                                )
                        )),
                        "select distinct  np.prop_key as propertyKey,np.prop_value as propertyValue  " +
                                "from  node_props np " +
                                "left outer join nodes n on n.node_id = np.node_id " +
                                "left outer join node_props np100 on np100.node_id = np.node_id " +
                                "left outer join node_props np101 on np101.node_id = np.node_id " +
                                "left outer join node_props np102 on np102.node_id = np.node_id  " +
                                "where ((( n.repo = ? or np100.prop_key = ?) " +
                                "and( np101.prop_key = ? or np102.prop_key = ?))) " +
                                "and(np.node_id is null or not exists (" +
                                "select 1 from node_props where np.node_id = node_id and prop_key = ?" +
                                ")) "
                },
                {
                        // Filter by nested criteria - OR(FILTER, FILTER). Trash items should be excluded.
                        "propertiesByNestedRepoFilterWithAnd",
                        AqlApiProperty.create().filter(or(
                                AqlApiProperty.item().repo().equal("test-repo2"),
                                and(
                                        AqlApiProperty.item().repo().equal("test-repo"),
                                        AqlApiProperty.key().equal(DISTRIBUTION_PROP)
                                )
                        )),
                        "select distinct  np.prop_key as propertyKey,np.prop_value as propertyValue  " +
                                "from  node_props np " +
                                "left outer join nodes n on n.node_id = np.node_id " +
                                "left outer join node_props np100 on np100.node_id = np.node_id  " +
                                "where ( n.repo = ? or( n.repo = ? and np100.prop_key = ?)) "
                },
                {
                        // Global search only. Trash items should be excluded.
                        "propertiesByProperty",
                        AqlApiProperty.create().filter(
                                AqlApiProperty.key().equal(DISTRIBUTION_PROP)
                        ),
                        "select distinct  np.prop_key as propertyKey,np.prop_value as propertyValue  " +
                                "from  node_props np " +
                                "inner join node_props np100 on np100.node_id = np.node_id  " +
                                "where ( np100.prop_key = ?) " +
                                "and(np.node_id is null or not exists (" +
                                "select 1 from node_props where np.node_id = node_id and prop_key = ?" +
                                ")) "
                },
                {
                        // Global items search by name. Trash items should be excluded.
                        "itemsByName",
                        AqlApiItem.createWithEmptyResults()
                                .include(AqlApiItem.itemId())
                                .filter(
                                        AqlApiItem.name().equal("test-item")
                                ),
                        "select distinct  n.node_id as itemId  " +
                                "from  nodes n  " +
                                "where ( n.node_name = ? and n.node_type = ?) and (n.repo != ?) "
                },
                {
                        // Items search by repo. Trash items already excluded.
                        "itemsByEqualRepo",
                        AqlApiItem.createWithEmptyResults()
                                .include(AqlApiItem.itemId())
                                .filter(
                                        AqlApiItem.repo().equal("test-repo")
                                ),
                        "select distinct  n.node_id as itemId  " +
                                "from  nodes n  " +
                                "where  n.repo = ? and n.node_type = ? "
                },
                {
                        // Items search by multiple repo. Trash items already excluded.
                        "itemsByMatchedRepo",
                        AqlApiItem.createWithEmptyResults()
                                .include(AqlApiItem.itemId())
                                .filter(
                                        AqlApiItem.repo().matches("test")
                                ),
                        "select distinct  n.node_id as itemId  " +
                                "from  nodes n  " +
                                "where ( n.repo like ? and n.node_type = ?) and (n.repo != ?) "
                },
                {
                        // Items search by 'special' repo. Trash items must be included.
                        "itemsBySpecialRepo",
                        AqlApiItem.createWithEmptyResults()
                                .include(AqlApiItem.itemId())
                                .filter(
                                        AqlApiItem.repo().equal(TrashService.TRASH_KEY)
                                ),
                        "select distinct  n.node_id as itemId  " +
                                "from  nodes n  " +
                                "where  n.repo = ? and n.node_type = ? "
                }
        };
    }
}
