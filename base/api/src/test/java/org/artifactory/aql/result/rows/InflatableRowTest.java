/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.aql.result.rows;

import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.Row;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Eli Fishman
 */
class InflatableRowTest extends ArtifactoryHomeBoundJUnitTest {

    private static final Pattern COMPOSITE_KEY_SEGMENT =
            Pattern.compile("\\(\\(key:[^)]+\\)\\(value:[^)]+\\)\\)");

    private static final List<String> ITEMS_ONLY_COMPOSITE_KEY_SEGMENTS = List.of(
            "((key:itemId)(value:42))",
            "((key:itemRepo)(value:my-repo))");
    private static final List<String> MULTI_DOMAIN_ITEMS_COMPOSITE_KEY_SEGMENTS = List.of(
            "((key:itemId)(value:100))",
            "((key:itemRepo)(value:libs-local))");
    private static final List<String> MULTI_DOMAIN_PROPERTIES_COMPOSITE_KEY_SEGMENTS = List.of(
            "((key:propertyValue)(value:42))",
            "((key:propertyId)(value:7))",
            "((key:propertyKey)(value:build.number))");
    private static final List<String> MULTI_DOMAIN_STATISTICS_COMPOSITE_KEY_SEGMENTS = List.of(
            "((key:statId)(value:3))",
            "((key:statDownloads)(value:5))");

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void inflate_shouldReturnSingleRowWithItemRepo_whenOneItemsDomainField(
            boolean enhancementEnabled) {
        setStringSystemProperty(ConstantValues.inflateRowEnhancementEnabled,
                String.valueOf(enhancementEnabled));

        InflatableRow row = new InflatableRow();
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo,
                List.of(AqlDomainEnum.items)), "my-repo");

        Map<String, Row> result = row.inflate();

        assertEquals(1, result.size());
        Row inflatedRow = result.values().iterator().next();
        assertEquals(AqlDomainEnum.items, inflatedRow.domain);
        assertEquals("my-repo", inflatedRow.itemRepo);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void inflate_shouldReturnRowWithMultipleFields_whenMultipleItemsFieldsSet(
            boolean enhancementEnabled) {
        setStringSystemProperty(ConstantValues.inflateRowEnhancementEnabled,
                String.valueOf(enhancementEnabled));

        InflatableRow row = new InflatableRow();
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo,
                List.of(AqlDomainEnum.items)), "libs-local");
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.itemPath,
                List.of(AqlDomainEnum.items)), "com/example");
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.itemName,
                List.of(AqlDomainEnum.items)), "artifact.jar");

        Map<String, Row> result = row.inflate();

        assertEquals(1, result.size());
        Row inflatedRow = result.values().iterator().next();
        assertEquals("libs-local", inflatedRow.itemRepo);
        assertEquals("com/example", inflatedRow.itemPath);
        assertEquals("artifact.jar", inflatedRow.itemName);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void inflate_shouldSetDomainOnRow_whenInflated(boolean enhancementEnabled) {
        setStringSystemProperty(ConstantValues.inflateRowEnhancementEnabled,
                String.valueOf(enhancementEnabled));

        InflatableRow row = new InflatableRow();
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo,
                List.of(AqlDomainEnum.items)), "repo");

        Map<String, Row> result = row.inflate();

        Row inflatedRow = result.values().iterator().next();
        assertEquals(AqlDomainEnum.items, inflatedRow.getDomain());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void inflate_shouldReturnNonEmptyMapWithCorrectKeyFormat_whenItemsDataPresent(
            boolean enhancementEnabled) {
        setStringSystemProperty(ConstantValues.inflateRowEnhancementEnabled,
                String.valueOf(enhancementEnabled));

        InflatableRow row = new InflatableRow();
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo,
                List.of(AqlDomainEnum.items)), "my-repo");
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.itemId,
                List.of(AqlDomainEnum.items)), 42L);

        Map<String, Row> result = row.inflate();

        assertFalse(result.isEmpty());
        assertCompositeKeyEquals(ITEMS_ONLY_COMPOSITE_KEY_SEGMENTS, result.keySet().iterator().next());
        Row inflatedRow = result.values().iterator().next();
        assertEquals("my-repo", inflatedRow.itemRepo);
        assertEquals(42L, inflatedRow.itemId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void inflate_shouldBuildNestedItemsPropertiesAndStatsDomains_whenMultiDomainFieldsPresent(
            boolean enhancementEnabled) {
        setStringSystemProperty(ConstantValues.inflateRowEnhancementEnabled,
                String.valueOf(enhancementEnabled));

        InflatableRow row = buildMultiDomainInflatableRow();

        Map<String, Row> result = row.inflate();

        assertEquals(1, result.size());
        assertCompositeKeyEquals(MULTI_DOMAIN_ITEMS_COMPOSITE_KEY_SEGMENTS, result.keySet().iterator().next());

        Row itemRow = result.values().iterator().next();
        assertEquals(AqlDomainEnum.items, itemRow.domain);
        assertEquals("libs-local", itemRow.itemRepo);
        assertEquals(100L, itemRow.itemId);
        assertEquals(2, itemRow.subDomains.size());

        Row propertyRow = findSubRowByDomain(itemRow, AqlDomainEnum.properties);
        Row statRow = findSubRowByDomain(itemRow, AqlDomainEnum.statistics);

        assertNotNull(propertyRow);
        assertNotNull(statRow);
        assertEquals("build.number", propertyRow.propertyKey);
        assertEquals("42", propertyRow.propertyValue);
        assertEquals(7L, propertyRow.propertyId);
        assertEquals(5, statRow.statDownloads);
        assertEquals(3L, statRow.statId);

        assertCompositeKeyEquals(MULTI_DOMAIN_PROPERTIES_COMPOSITE_KEY_SEGMENTS,
                findSubRowKey(itemRow, AqlDomainEnum.properties));
        assertCompositeKeyEquals(MULTI_DOMAIN_STATISTICS_COMPOSITE_KEY_SEGMENTS,
                findSubRowKey(itemRow, AqlDomainEnum.statistics));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void inflate_shouldCallResolveIdOncePerDomain_whenMultiDomainFieldsPresent(
            boolean enhancementEnabled) {
        setStringSystemProperty(ConstantValues.inflateRowEnhancementEnabled,
                String.valueOf(enhancementEnabled));

        InflatableRow row = buildMultiDomainInflatableRow();

        row.inflate();

        assertEquals(3, row.lastInflateResolveIdCallCount);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void inflate_shouldProduceStableCompositeKey_onRepeatedInflate(boolean enhancementEnabled) {
        setStringSystemProperty(ConstantValues.inflateRowEnhancementEnabled,
                String.valueOf(enhancementEnabled));

        InflatableRow row = buildMultiDomainInflatableRow();

        String firstKey = row.inflate().keySet().iterator().next();
        String secondKey = row.inflate().keySet().iterator().next();

        assertEquals(firstKey, secondKey);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void merge_shouldCombinePropertySubRows_whenItemsCompositeKeysMatch(boolean enhancementEnabled) {
        setStringSystemProperty(ConstantValues.inflateRowEnhancementEnabled,
                String.valueOf(enhancementEnabled));

        List<AqlDomainEnum> itemsPath = List.of(AqlDomainEnum.items);
        List<AqlDomainEnum> itemPropertyPath = List.of(AqlDomainEnum.items, AqlDomainEnum.properties);

        InflatableRow firstPropertyRow = new InflatableRow();
        firstPropertyRow.put(new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo, itemsPath), "libs-local");
        firstPropertyRow.put(new DomainSensitiveField(AqlPhysicalFieldEnum.itemId, itemsPath), 100L);
        firstPropertyRow.put(new DomainSensitiveField(AqlPhysicalFieldEnum.propertyKey, itemPropertyPath),
                "build.number");
        firstPropertyRow.put(new DomainSensitiveField(AqlPhysicalFieldEnum.propertyValue, itemPropertyPath), "42");
        firstPropertyRow.put(new DomainSensitiveField(AqlPhysicalFieldEnum.propertyId, itemPropertyPath), 7L);

        InflatableRow secondPropertyRow = new InflatableRow();
        secondPropertyRow.put(new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo, itemsPath), "libs-local");
        secondPropertyRow.put(new DomainSensitiveField(AqlPhysicalFieldEnum.itemId, itemsPath), 100L);
        secondPropertyRow.put(new DomainSensitiveField(AqlPhysicalFieldEnum.propertyKey, itemPropertyPath),
                "version");
        secondPropertyRow.put(new DomainSensitiveField(AqlPhysicalFieldEnum.propertyValue, itemPropertyPath), "1.0");
        secondPropertyRow.put(new DomainSensitiveField(AqlPhysicalFieldEnum.propertyId, itemPropertyPath), 8L);

        Map<String, Row> firstResult = firstPropertyRow.inflate();
        Map<String, Row> secondResult = secondPropertyRow.inflate();
        Row firstItemRow = firstResult.values().iterator().next();
        Row secondItemRow = secondResult.values().iterator().next();

        String firstKey = firstResult.keySet().iterator().next();
        String secondKey = secondResult.keySet().iterator().next();
        assertCompositeKeyEquals(MULTI_DOMAIN_ITEMS_COMPOSITE_KEY_SEGMENTS, firstKey);
        assertCompositeKeyEquals(MULTI_DOMAIN_ITEMS_COMPOSITE_KEY_SEGMENTS, secondKey);
        assertEquals(firstKey, secondKey);
        firstItemRow.merge(secondItemRow);
        firstItemRow.build();

        assertEquals(2, firstItemRow.properties.size());
    }

    private static InflatableRow buildMultiDomainInflatableRow() {
        List<AqlDomainEnum> itemsPath = List.of(AqlDomainEnum.items);
        List<AqlDomainEnum> itemPropertyPath = List.of(AqlDomainEnum.items, AqlDomainEnum.properties);
        List<AqlDomainEnum> itemStatPath = List.of(AqlDomainEnum.items, AqlDomainEnum.statistics);

        InflatableRow row = new InflatableRow();
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo, itemsPath), "libs-local");
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.itemId, itemsPath), 100L);
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.propertyKey, itemPropertyPath), "build.number");
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.propertyValue, itemPropertyPath), "42");
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.propertyId, itemPropertyPath), 7L);
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.statDownloads, itemStatPath), 5);
        row.put(new DomainSensitiveField(AqlPhysicalFieldEnum.statId, itemStatPath), 3L);
        return row;
    }

    private static void assertCompositeKeyEquals(List<String> expectedSegments, String actualKey) {
        List<String> actualSegments = extractCompositeKeySegments(actualKey);
        List<String> sortedExpected = new ArrayList<>(expectedSegments);
        List<String> sortedActual = new ArrayList<>(actualSegments);
        Collections.sort(sortedExpected);
        Collections.sort(sortedActual);
        assertEquals(sortedExpected, sortedActual);
    }

    private static List<String> extractCompositeKeySegments(String compositeKey) {
        Matcher matcher = COMPOSITE_KEY_SEGMENT.matcher(compositeKey);
        List<String> segments = new ArrayList<>();
        while (matcher.find()) {
            segments.add(matcher.group());
        }
        return segments;
    }

    private static Row findSubRowByDomain(Row parent, AqlDomainEnum domain) {
        for (Row subRow : parent.subDomains.values()) {
            if (subRow.domain == domain) {
                return subRow;
            }
        }
        return null;
    }

    private static String findSubRowKey(Row parent, AqlDomainEnum domain) {
        for (Map.Entry<String, Row> entry : parent.subDomains.entrySet()) {
            if (entry.getValue().domain == domain) {
                return entry.getKey();
            }
        }
        return null;
    }
}
