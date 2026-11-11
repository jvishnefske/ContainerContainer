package org.artifactory.storage.db.search.model;

import org.artifactory.sapi.search.VfsBoolType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Yevdo Abramov
 * Created on 30/12/2019
 */
class VfsQueryCriterionDbImplTest {

    @Test
    void testCriteriaBuilderForSingleValue() {
        VfsQueryCriterionDbImpl criterion = new VfsQueryCriterionDbImpl("node_name");
        criterion.setValue("MyArt");
        DbSqlQueryBuilder queryBuilder = new DbSqlQueryBuilder();
        queryBuilder.append("SELECT node_name FROM node WHERE ");
        criterion.fill(queryBuilder);
        assertEquals(queryBuilder.toString(), "SELECT node_name FROM node WHERE nodes.node_name = 'MyArt' ");
    }

    @Test
    void testCaseInsensitiveCriteriaBuilderForSingleValue() {
        VfsQueryCriterionDbImpl criterion = new VfsQueryCriterionDbImpl("node_name");
        criterion.setCaseInsensitive(true);
        criterion.setValue("MyArt");
        DbSqlQueryBuilder queryBuilder = new DbSqlQueryBuilder();
        queryBuilder.append("SELECT node_name FROM node WHERE ");
        criterion.fill(queryBuilder);
        assertEquals(queryBuilder.toString(), "SELECT node_name FROM node WHERE lower(nodes.node_name) = 'myart' ");
    }

    @Test
    void testCaseInsensitiveCriteriaBuilderForIterableValue() {
        VfsQueryCriterionDbImpl criterion = new VfsQueryCriterionDbImpl("node_name");
        criterion.setCaseInsensitive(true);
        criterion.setValue(Arrays.asList("MyArt", "TheArt"));
        DbSqlQueryBuilder queryBuilder = new DbSqlQueryBuilder();
        queryBuilder.append("SELECT node_name FROM node WHERE ");
        criterion.fill(queryBuilder);
        assertEquals(queryBuilder.sqlQuery.toString(),
                "SELECT node_name FROM node WHERE lower(nodes.node_name) = (#) ");

        List<?> params = (List<?>) queryBuilder.params.get(0);
        assertEquals(params.size(), 2);
        assertTrue(params.contains("myart"));
        assertTrue(params.contains("theart"));
    }

    @ParameterizedTest
    @MethodSource("getBoolAndResult")
    void testPropertyFieldType(VfsBoolType nextBool, String expected) {
        VfsQueryCriterionDbImpl criterion = new VfsQueryCriterionDbImpl("test");
        criterion.setValue("MyArt");
        DbSqlQueryBuilder queryBuilder = new DbSqlQueryBuilder();
        queryBuilder.append("SELECT node_name FROM node WHERE ");
        criterion.nextBool = nextBool;
        criterion.fill(queryBuilder);
        criterion.fill(queryBuilder);
        assertEquals(queryBuilder.toString(), expected);
    }

    private static Stream<Arguments> getBoolAndResult() {
        return Stream.of(Arguments.of(VfsBoolType.OR,
                        "SELECT node_name FROM node WHERE (node_props.prop_key = 'test' AND node_props.prop_value = " +
                                "'MyArt' ) (node_props.prop_key = 'test' AND node_props.prop_value = 'MyArt' )"),
                Arguments.of(VfsBoolType.AND,
                        "SELECT node_name FROM node WHERE (node_props.prop_key = 'test' AND node_props.prop_value = " +
                                "'MyArt' ) EXISTS (SELECT node_props2.node_id FROM node_props node_props2 WHERE nodes" +
                                ".node_id = node_props2.node_id AND node_props2.prop_key = 'test' AND node_props2" +
                                ".prop_value = 'MyArt' )"));
    }
}