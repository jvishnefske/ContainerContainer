package org.artifactory.storage.db.aql.service.decorator;


import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

/**
 * @author Tomer Slutsky
 */
class SpecialReposDecoratorTest extends BaseDecorator {
    private static Stream<Arguments> repoCriteriaDataProvide() {
        return Stream.of(
                Arguments.of(new InTransitRepoDecorator(), false, "repo1", "item.repo $eq repo1",
                        "item.repo $eq repo1"),
                Arguments.of(new InTransitRepoDecorator(), false, "_intransit",
                        "( item.repo $eq _intransit ) $and item.repo $ne _intransit",
                        "( item.repo $eq _intransit ) $and item.repo $ne _intransit"),
                Arguments.of(new TrashcanDecorator(), false, "repo1", "item.repo $eq repo1", "item.repo $eq repo1"),
                Arguments.of(new SupportBundlesRepoDecorator(), false, "repo1", "item.repo $eq repo1",
                        "item.repo $eq repo1"),
                Arguments.of(new InTransitRepoDecorator(), true, "repo1", "item.path $eq path", "item.repo $eq repo1"),
                Arguments.of(new InTransitRepoDecorator(), true, "_intransit",
                        "( item.path $eq path ) $and item.repo $ne _intransit",
                        "( item.repo $eq _intransit ) $and item.repo $ne _intransit"),
                Arguments.of(new TrashcanDecorator(), true, "repo1",
                        "( item.path $eq path ) $and item.repo $ne auto-trashcan",
                        "( item.repo $eq repo1 ) $and item.repo $ne auto-trashcan"),
                Arguments.of(new SupportBundlesRepoDecorator(), true, "repo1",
                        "( item.path $eq path ) $and item.repo $ne jfrog-support-bundle",
                        "( item.repo $eq repo1 ) $and item.repo $ne jfrog-support-bundle"));
    }

    private static Stream<Arguments> pathCriteriaDataProvide() {
        return Stream.of(
                Arguments.of(new InTransitRepoDecorator(), false,
                        "( item.path $eq folderA/FolderB ) $and item.repo $ne _intransit",
                        "( item.path $eq folderA/FolderB ) $and item.repo $ne _intransit"),
                Arguments.of(new TrashcanDecorator(), false,
                        "( item.path $eq folderA/FolderB ) $and item.repo $ne auto-trashcan",
                        "( item.path $eq folderA/FolderB ) $and item.repo $ne auto-trashcan"),
                Arguments.of(new SupportBundlesRepoDecorator(), false,
                        "( item.path $eq folderA/FolderB ) $and item.repo $ne jfrog-support-bundle",
                        "( item.path $eq folderA/FolderB ) $and item.repo $ne jfrog-support-bundle"),
                Arguments.of(new InTransitRepoDecorator(), true,
                        "( item.repo $eq folderA/FolderB ) $and item.repo $ne _intransit",
                        "( item.path $eq folderA/FolderB ) $and item.repo $ne _intransit"),
                Arguments.of(new TrashcanDecorator(), true,
                        "( item.repo $eq folderA/FolderB ) $and item.repo $ne auto-trashcan",
                        "( item.path $eq folderA/FolderB ) $and item.repo $ne auto-trashcan"),
                Arguments.of(new SupportBundlesRepoDecorator(), true,
                        "( item.repo $eq folderA/FolderB ) $and item.repo $ne jfrog-support-bundle",
                        "( item.path $eq folderA/FolderB ) $and item.repo $ne jfrog-support-bundle"));
    }

    @ParameterizedTest
    @MethodSource("repoCriteriaDataProvide")
    void testDecorateItemsSearch_repoCriteria(SpecialReposDecorator decorator, boolean union, String repoName,
            String expectedUnion, String expected) {
        AqlQueryDecoratorContext context = mock(AqlQueryDecoratorContext.class);
        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        Criterion originalCriterion = createRepoCriteria(AqlComparatorEnum.equals, repoName);
        aqlQuery.getAqlElements().add(originalCriterion);
        if (union) {
            List<AqlQueryElement> sub = new ArrayList<>();
            sub.add(createEqualsPathCriteria("path"));
            aqlQuery.putSubQuery(sub);
        } else {
            aqlQuery.putSubQuery(new ArrayList<>(aqlQuery.getAqlElements()));
        }
        aqlQuery.setDomain(AqlDomainEnum.items);
        decorator.decorate(aqlQuery, context);
        assertEquals(toString(aqlQuery.getAqlElements()), expected);
        assertEquals(toString(aqlQuery.getUnionSubQueries().get(0).getAqlElements()), expectedUnion);
    }

    @ParameterizedTest
    @MethodSource("pathCriteriaDataProvide")
    void testDecorateItemsSearch_pathCriteria(SpecialReposDecorator decorator, boolean union, String expectedUnion,
            String expected) {
        AqlQueryDecoratorContext context = mock(AqlQueryDecoratorContext.class);

        AqlQuery<AqlRowResult> aqlQuery = new AqlQuery<>();
        Criterion originalCriterion = createEqualsPathCriteria("folderA/FolderB");
        aqlQuery.getAqlElements().add(originalCriterion);
        if (union) {
            List<AqlQueryElement> sub = new ArrayList<>();
            sub.add(createRepoCriteria(AqlComparatorEnum.equals, "folderA/FolderB"));
            aqlQuery.putSubQuery(sub);
        } else {
            aqlQuery.putSubQuery(new ArrayList<>(aqlQuery.getAqlElements()));
        }
        aqlQuery.setDomain(AqlDomainEnum.items);

        decorator.decorate(aqlQuery, context);
        assertEquals(toString(aqlQuery.getAqlElements()), expected);
        assertEquals(toString(aqlQuery.getUnionSubQueries().get(0).getAqlElements()), expectedUnion);
    }
}