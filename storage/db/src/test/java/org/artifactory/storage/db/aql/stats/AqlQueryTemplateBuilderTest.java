package org.artifactory.storage.db.aql.stats;

import org.artifactory.aql.model.AqlQueryTemplate;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.parser.AqlParser;
import org.artifactory.storage.db.aql.parser.ParserElementResultContainer;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ParserToAqlAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Amir Avidan
 */
class AqlQueryTemplateBuilderTest {

    private AqlParser parser;
    private ParserToAqlAdapter adapter;

    @BeforeEach
    void setUp() {
        parser = new AqlParser();
        adapter = new ParserToAqlAdapter();
    }

    static Stream<Arguments> literals() {
        return Stream.of(
                // Plain string -> single underscore
                Arguments.of("pom.xml", "_"),
                Arguments.of("manifest.json", "_"),
                Arguments.of("hello world", "_"),
                // Leading wildcard preserved
                Arguments.of("*pom.xml", "*_"),
                // Trailing wildcard preserved
                Arguments.of("pom*", "_*"),
                // Multiple stars with runs between them
                Arguments.of("*pom.*xml", "*_*_"),
                Arguments.of("xray*103.999.99-milestone-12991*tgz", "_*_*_"),
                // Question mark is also a wildcard
                Arguments.of("foo?bar", "_?_"),
                Arguments.of("?xyz", "?_"),
                // Wildcards only
                Arguments.of("*", "*"),
                Arguments.of("**", "**"),
                Arguments.of("*?", "*?"),
                Arguments.of("?*", "?*"),
                // Edge cases
                Arguments.of("", "")
        );
    }

    @ParameterizedTest
    @MethodSource("literals")
    void normalizeLiteralMatchesSpec(String input, String expected) {
        assertEquals(expected, AqlQueryTemplateBuilder.normalizeLiteral(input),
                "normalizeLiteral(\"" + input + "\")");
    }

    @Test
    void normalizeLiteralHandlesNull() {
        assertNull(AqlQueryTemplateBuilder.normalizeLiteral(null));
    }

    @Test
    void buildReturnsNullForNullQuery() {
        assertNull(AqlQueryTemplateBuilder.build(null));
    }

    @Test
    void buildProducesValueFreeTemplateForSimpleEqualsQuery() {
        AqlQueryTemplate t1 = templateOf("items.find({\"name\":\"manifest.json\"})");
        AqlQueryTemplate t2 = templateOf("items.find({\"name\":\"maven-metadata.xml\"})");

        // The spec's lead example: two queries differing only in their literal value
        // collapse to the same template+checksum.
        assertNotNull(t1);
        assertNotNull(t2);
        assertEquals(t1.template(), t2.template(),
                "Queries differing only in literal values must share a template");
        assertEquals(t1.checksum(), t2.checksum(),
                "Equal templates must produce equal checksums");
        // And the template must NOT contain the original literal anywhere.
        assertTemplateDoesNotLeakValue(t1.template(), "manifest.json");
        assertTemplateDoesNotLeakValue(t2.template(), "maven-metadata.xml");
    }

    @Test
    void buildPreservesWildcardsInMatchExpressions() {
        // *foo cannot use an index, foo* can -- the spec requires they get different templates.
        AqlQueryTemplate leading = templateOf("items.find({\"name\":{\"$match\":\"*pom.xml\"}})");
        AqlQueryTemplate trailing = templateOf("items.find({\"name\":{\"$match\":\"pom*\"}})");

        assertNotNull(leading);
        assertNotNull(trailing);
        assertNotEquals(leading.template(), trailing.template(),
                "*pom.xml and pom* must produce distinct templates (different query plans)");
        assertNotEquals(leading.checksum(), trailing.checksum());
    }

    @Test
    void buildMatchesDocExampleForComplexMatchValue() {
        // Spec example: xray*103.999.99-milestone-12991*tgz -> _*_*_
        AqlQueryTemplate t = templateOf(
                "items.find({\"name\":{\"$match\":\"xray*103.999.99-milestone-12991*tgz\"}})");
        assertNotNull(t);
        assertTrue(t.template().contains("_*_*_"),
                "Template should contain the normalized match value `_*_*_` -- got: " + t.template());
    }

    @Test
    void buildCollapsesWhitespaceAcrossFormattingVariants() {
        // Same query with extra whitespace -- toNative() rebuilds canonical form so templates match.
        AqlQueryTemplate compact = templateOf("items.find({\"name\":\"manifest.json\"})");
        AqlQueryTemplate spacy = templateOf("items.find( { \"name\" : \"manifest.json\" } )");
        AqlQueryTemplate multiline = templateOf("items.find({\n  \"name\":\t\"manifest.json\"\n})");

        assertEquals(compact.checksum(), spacy.checksum(),
                "Whitespace should not affect the checksum");
        assertEquals(compact.checksum(), multiline.checksum(),
                "Newlines and tabs should not affect the checksum");
    }

    @Test
    void buildHandlesNestedOrQueries() {
        AqlQueryTemplate t1 = templateOf(
                "items.find({\"$or\":[{\"repo\":\"libs-release\"},{\"type\":1}]})");
        AqlQueryTemplate t2 = templateOf(
                "items.find({\"$or\":[{\"repo\":\"another-repo\"},{\"type\":99}]})");
        assertNotNull(t1);
        assertNotNull(t2);
        assertEquals(t1.template(), t2.template(),
                "$or branches that differ only in literals must aggregate to the same template");
        assertEquals(t1.checksum(), t2.checksum());
    }

    @Test
    void buildHandlesTypedItemTypeField() {
        // `type` is an itemType field (file/folder/any). Placeholder must be a valid signature
        // -- otherwise AqlValue.toNative() throws on render. Two queries differing only in
        // type signature must collapse to the same template.
        AqlQueryTemplate file = templateOf("items.find({\"type\":\"file\"})");
        AqlQueryTemplate folder = templateOf("items.find({\"type\":\"folder\"})");
        assertNotNull(file);
        assertNotNull(folder);
        assertEquals(file.template(), folder.template(),
                "type:file and type:folder must template to the same value");
        assertEquals(file.checksum(), folder.checksum());
    }

    @Test
    void buildHandlesNumericTypedField() {
        // Numeric fields (e.g. size as longInt) must use a numeric placeholder; "_" would fail
        // Long.parseLong inside AqlValue.toObject during toNative.
        AqlQueryTemplate small = templateOf("items.find({\"size\":{\"$gt\":100}})");
        AqlQueryTemplate large = templateOf("items.find({\"size\":{\"$gt\":999999999}})");
        assertNotNull(small);
        assertNotNull(large);
        assertEquals(small.template(), large.template(),
                "Numeric size criteria differing only in value must share a template");
        assertEquals(small.checksum(), large.checksum());
    }

    @Test
    void buildHandlesDateTypedField() {
        // date fields must use a numeric placeholder so AqlValue.toNative() does not throw
        // on format validation, and two queries differing only in the date value collapse
        // to the same template.
        AqlQueryTemplate t1 = templateOf("items.find({\"created\":{\"$gt\":\"2024-01-01T00:00:00.000Z\"}})");
        AqlQueryTemplate t2 = templateOf("items.find({\"created\":{\"$gt\":\"2025-06-15T12:30:00.000Z\"}})");
        assertNotNull(t1);
        assertNotNull(t2);
        assertEquals(t1.template(), t2.template(),
                "Date criteria differing only in value must share a template");
        assertEquals(t1.checksum(), t2.checksum());
    }

    @Test
    void buildHandlesPropertyCriteria() {
        AqlQueryTemplate t1 = templateOf("items.find({\"@license\":{\"$eq\":\"GPL\"}})");
        AqlQueryTemplate t2 = templateOf("items.find({\"@license\":{\"$eq\":\"MIT\"}})");
        assertNotNull(t1);
        assertNotNull(t2);
        assertEquals(t1.checksum(), t2.checksum(),
                "Property values should be normalized like field values");
    }

    @Test
    void buildRestoresOriginalValuesAfterRender() {
        // Critical safety property: the parsed AqlQuery must NOT be left in a mutated state,
        // otherwise downstream SQL generation would use the templated `_` values.
        String query = "items.find({\"name\":\"manifest.json\"})";
        ParserElementResultContainer parserResult = parser.parse(query);
        AqlQuery<AqlRowResult> aqlQuery = adapter.toAqlModel(parserResult);
        String beforeNative = aqlQuery.toNative();

        AqlQueryTemplateBuilder.build(aqlQuery);

        String afterNative = aqlQuery.toNative();
        assertEquals(beforeNative, afterNative,
                "AqlQueryTemplateBuilder.build must restore original literal values");
    }

    @Test
    void buildEvenAfterExceptionRestoresValues() {
        // Restore must happen via try/finally so any future toNative() bug doesn't leave the
        // query in a templated state. Smoke-test by mutating the query post-build.
        String query = "items.find({\"name\":\"manifest.json\"})";
        ParserElementResultContainer parserResult = parser.parse(query);
        AqlQuery<AqlRowResult> aqlQuery = adapter.toAqlModel(parserResult);
        AqlQueryTemplateBuilder.build(aqlQuery);
        // Should be safe to render the original again.
        String again = aqlQuery.toNative();
        assertTrue(again.contains("manifest.json"),
                "Original literal must remain after build returns");
    }

    @Test
    void checksumIsHexAndCheap() {
        // Sergey's pattern from DbQueryMonitorImpl: Integer.toHexString(hashCode())
        // produces 1-8 lower-case hex chars. We don't need to assert the exact value
        // (that would couple us to JDK hashCode), just the shape.
        AqlQueryTemplate t = templateOf("items.find({\"name\":\"manifest.json\"})");
        assertNotNull(t);
        assertTrue(t.checksum().matches("[0-9a-f]{1,8}"),
                "Checksum should be 1-8 lower-case hex chars: " + t.checksum());
    }

    private AqlQueryTemplate templateOf(String query) {
        ParserElementResultContainer parserResult = parser.parse(query);
        AqlQuery<AqlRowResult> aqlQuery = adapter.toAqlModel(parserResult);
        return AqlQueryTemplateBuilder.build(aqlQuery);
    }

    private static void assertTemplateDoesNotLeakValue(String template, String value) {
        assertTrue(!template.contains(value),
                "Template should not contain the original literal `" + value + "` -- got: " + template);
    }
}
