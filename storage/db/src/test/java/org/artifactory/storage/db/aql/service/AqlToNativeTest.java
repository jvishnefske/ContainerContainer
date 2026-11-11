package org.artifactory.storage.db.aql.service;

import org.artifactory.storage.db.aql.parser.AqlParser;
import org.artifactory.storage.db.aql.parser.ParserElementResultContainer;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ParserToAqlAdapter;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

/**
 * @author Tomer Slutsky
 */
public class AqlToNativeTest extends ArtifactoryHomeBoundTest {
    private final AqlParser parser = new AqlParser();
    private final ParserToAqlAdapter aqlAdapter = new ParserToAqlAdapter();

    @BeforeMethod
    public void before() {
        bindArtifactoryHome();
    }

    @AfterMethod
    public void after() {
        unbindArtifactoryHome();
    }

    @Test
    public void queryToNative() {
        this.assertValid("items.find({\"repo\": \"docker-local-repo\"})",
                "items.find({\"repo\":{\"$eq\":\"docker-local-repo\"}})");
        this.assertValid("items.find({\"$rf\":[{\"@a\":\"jcentral\"},{\"@c\":\"file\"}]})",
                "items.find({\"$rf\":[{\"@a\":{\"$eq\":\"jcentral\"}},{\"@c\":{\"$eq\":\"file\"}}]})");
        this.assertValid("items.find({\"$msp\":[{\"$msp\":[{\"@license\":{\"$match\": \"*GPL*\"}}," +
                        "{\"@license\":{\"$nmatch\": \"LGPL-2.1*\"}}]},{\"@license\":{\"$match\": \"*GPL*\"}}]}," +
                        "{\"$msp\": " +
                        "[{\"@license\":{\"$match\":\"*GPL*\"}},{\"@license\":{\"$nmatch\":\"LGPL-2.1*\"}}]})",
                "items.find({\"$msp\":[{\"$msp\":[{\"@license\":{\"$match\":\"*GPL*\"}}," +
                        "{\"@license\":{\"$nmatch\":\"LGPL-2.1*\"}}]},{\"@license\":{\"$match\":\"*GPL*\"}}]}," +
                        "{\"$msp\":" +
                        "[{\"@license\":{\"$match\":\"*GPL*\"}},{\"@license\":{\"$nmatch\":\"LGPL-2.1*\"}}]})");
        this.assertValid("items.find({\"repo\":\"docker-local-repo\"}).transitive().sort({\"$asc\":[\"repo\"]})",
                "items.find({\"repo\":{\"$eq\":\"docker-local-repo\"}}).transitive().sort({\"$asc\":[\"repo\"]})");
        this.assertValid(
                "items.find({\"repo\":\"docker-local-repo\"}).transitive().sort({\"$asc\":[\"repo\"]}).limit(1)",
                "items.find({\"repo\":{\"$eq\":\"docker-local-repo\"}}).transitive().sort({\"$asc\":[\"repo\"]})" +
                        ".limit(1)");
        this.assertValid(
                "items.find({\"repo\":\"docker-local-repo\"}).transitive().sort({\"$asc\":[\"repo\"]}).offset(1996)" +
                        ".limit(1)",
                "items.find({\"repo\":{\"$eq\":\"docker-local-repo\"}}).transitive().sort({\"$asc\":[\"repo\"]})" +
                        ".offset(1996).limit(1)");
        this.assertValid("items.find({\"$and\":[{\"repo\":{\"$eq\":\"docker-local-repo\"}}," +
                        "{\"type\":{\"$eq\":\"file\"}}]}).include(\"artifact.module.build\", \"artifact.module.build" +
                        ".@bla\")",
                "items.find({\"$and\":[{\"repo\":{\"$eq\":\"docker-local-repo\"}},{\"type\":{\"$eq\":\"file\"}}]})" +
                        ".include(\"artifact.module.build\", \"artifact.module.build.@bla\")");
        this.assertValid("items.find({\"$and\":[{\"repo\":{\"$eq\":\"docker-local-repo\"}}," +
                        "{\"type\":{\"$eq\":\"file\"}}]}).include(\"repo\", \"@bla\")",
                "items.find({\"$and\":[{\"repo\":{\"$eq\":\"docker-local-repo\"}}," +
                        "{\"type\":{\"$eq\":\"file\"}}]}).include(\"repo\", \"@bla\")");
        this.assertValid("items.find({\"$and\":[{\"repo\":{\"$eq\":\"docker-local-repo\"}}," +
                        "{\"type\":{\"$eq\":\"file\"}}]}).include(\"property.*\")",
                "items.find({\"$and\":[{\"repo\":{\"$eq\":\"docker-local-repo\"}}," +
                        "{\"type\":{\"$eq\":\"file\"}}]}).include(\"property.*\")");
    }

    private void assertValid(String original, String expected) {
        ParserElementResultContainer container = this.parser.parse(original);
        AqlQuery query = this.aqlAdapter.toAqlModel(container);
        assertEquals(query.toNative(), expected);
    }
}
