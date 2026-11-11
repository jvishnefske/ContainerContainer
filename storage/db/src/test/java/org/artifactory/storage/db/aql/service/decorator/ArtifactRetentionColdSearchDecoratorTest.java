package org.artifactory.storage.db.aql.service.decorator;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.model.AqlOverloadSettings;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.parser.AqlParser;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ParserToAqlAdapter;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Omri Ziv
 */
@Test
public class ArtifactRetentionColdSearchDecoratorTest extends ArtifactoryHomeBoundTest {

    private static final String NAMESPACE = "abcdefgh";
    @Mock
    private AqlQueryDecoratorContext aqlQueryDecoratorContext;

    @Mock
    private ArtifactoryContext appContext;

    private final ArtifactRetentionColdSearchDecorator decorator = new ArtifactRetentionColdSearchDecorator();
    private AqlParser parser;
    private ParserToAqlAdapter aqlAdapter;
    private AutoCloseable mocks;


    @BeforeMethod
    public void before() {
        mocks = MockitoAnnotations.openMocks(this);
        bindArtifactoryHome();
        ArtifactoryContextThreadBinder.bind(appContext);

        AddonsManager addonsManager = mock(AddonsManager.class);
        ArtifactRetentionAddon artifactRetentionAddon = mock(ArtifactRetentionAddon.class);
        when(addonsManager.addonByType(ArtifactRetentionAddon.class)).thenReturn(artifactRetentionAddon);
        when(artifactRetentionAddon.getJfrogColdStorageArchivingNamespace()).thenReturn(NAMESPACE);
        when(appContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);

        AqlOverloadSettings aqlOverloadSettings = AqlOverloadSettings.builder()
                .streamFunc((x, y, z) -> null).build();
        when(aqlQueryDecoratorContext.getAqlOverloadSettings()).thenReturn(aqlOverloadSettings);
        parser = new AqlParser();
        aqlAdapter = new ParserToAqlAdapter();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testReplacingARepoName() {
        String aql = "items.find({\"repo\": \"repo1\"}).include(\"property\")";
        AqlQuery<AqlRowResult> aqlQuery = toAqlQuery(aql);
        decorator.decorate(aqlQuery, aqlQueryDecoratorContext);
        assertThat(aqlQuery.toNative()).contains("{\"repo\":{\"$eq\":\"" + NAMESPACE + "_repo1\"}}");
    }

    @Test
    public void testReplacingMultiRepoNames() {
        String aql = "items.find({\"$or\":[{\"repo\" : \"repo1\", \"repo\" : \"repo2\" }]})" +
                ".include(\"property\")";
        AqlQuery<AqlRowResult> aqlQuery = toAqlQuery(aql);
        decorator.decorate(aqlQuery, aqlQueryDecoratorContext);
        assertThat(aqlQuery.toNative()).contains(NAMESPACE + "_repo1");
        assertThat(aqlQuery.toNative()).contains(NAMESPACE + "_repo2");
    }

    @Test(dataProvider = "aqlOverloads")
    public void testNonColdSearch(AqlOverloadSettings aqlOverloadSettings) {
        String aql = "items.find({\"repo\": \"repo1\"}).include(\"property\")";

        when(aqlQueryDecoratorContext.getAqlOverloadSettings()).thenReturn(aqlOverloadSettings);
        AqlQuery<AqlRowResult> aqlQuery = toAqlQuery(aql);
        decorator.decorate(aqlQuery, aqlQueryDecoratorContext);
        assertThat(aqlQuery.toNative()).doesNotContain(NAMESPACE + "_repo1");
        assertThat(aqlQuery.toNative()).contains("repo1");
    }

    @Test(expectedExceptions = AqlException.class,
            expectedExceptionsMessageRegExp = ".*Retention Aql search is not supported non items search.*")
    public void testNonItemQuery() {
        String aql = "builds.find({\"module.dependency.item.name\":{\"$match\":\"*SNAPSHOT*\"}})";
        AqlQuery<AqlRowResult> aqlQuery = toAqlQuery(aql);
        decorator.decorate(aqlQuery, aqlQueryDecoratorContext);
    }

    @DataProvider
    private Object[][] aqlOverloads() {
        return new Object[][] {
                {AqlOverloadSettings.builder().streamFunc(null).build()},
                {AqlOverloadSettings.builder().build()},
                {null}
        };
    }

    private AqlQuery<AqlRowResult> toAqlQuery(String aql) {
        return aqlAdapter.toAqlModel(parser.parse(aql));
    }

}
