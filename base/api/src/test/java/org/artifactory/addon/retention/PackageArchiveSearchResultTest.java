package org.artifactory.addon.retention;

import org.artifactory.addon.retention.exception.PackageArchiveSearchException;
import org.artifactory.aql.result.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * @author Oleksandr Legenkiy
 */
class PackageArchiveSearchResultTest {

    @Test
    void testSuccessfulInit() {
        PackageArchiveSearchResult searchResult = new PackageArchiveSearchResult(createFullRow());
        assertEquals("someTestName", searchResult.getManifestName());
        assertEquals("docker", searchResult.getPackageType());
        assertEquals("ngnix", searchResult.getPackageName());
        assertEquals("testRepo", searchResult.getOriginalRepository());
        assertEquals("latest", searchResult.getVersion());
        assertEquals(1679070175531L, searchResult.getArchivingTime());
    }

    @Test
    void testMissingField() {
        Row row = createFullRow();
        row.properties.remove(3); // remove repoName property
        Throwable t = assertThrows(PackageArchiveSearchException.class, () -> new PackageArchiveSearchResult(row));
        assertEquals("Missing required package archive data for field: originalRepository", t.getMessage());
    }

    @Test
    void testCannotParseArchivingTime() {
        Row row = createFullRow();
        row.properties.get(4).propertyValue = "1112invalidLongValue"; // change archiving time to invalid value
        Throwable t = assertThrows(PackageArchiveSearchException.class, () -> new PackageArchiveSearchResult(row));
        assertEquals("Cannot parse archive date property value: 1112invalidLongValue", t.getMessage());
    }

    private Row createFullRow() {
        Row row = new Row();
        row.itemName = "someTestName";
        row.properties = createFullPropsList();
        return row;
    }

    private List<Row> createFullPropsList() {
        List<Row> props = new ArrayList<>(5);
        props.add(createProp("archive.packageType", "docker"));
        props.add(createProp("archive.packageName", "ngnix"));
        props.add(createProp("archive.version", "latest"));
        props.add(createProp("archive.repoName", "testRepo"));
        props.add(createProp("archive.time", "1679070175531"));
        return props;
    }

    private Row createProp(String key, String value) {
        Row propRow = new Row();
        propRow.propertyKey = key;
        propRow.propertyValue = value;
        return propRow;
    }
}