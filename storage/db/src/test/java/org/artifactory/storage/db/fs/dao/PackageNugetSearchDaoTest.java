package org.artifactory.storage.db.fs.dao;

import com.jfrog.ph.acl.model.search.nuget.orderby.NugetOrderBy;
import com.jfrog.ph.acl.model.search.nuget.orderby.NugetOrderByColumn;
import com.jfrog.ph.acl.model.search.nuget.orderby.NugetSortOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.storage.db.fs.dao.PackageNugetSearchDaoImpl.QUERY_KEYWORD;
import static org.artifactory.storage.db.fs.dao.PackageNugetSearchDaoImpl.parseQ;
import static org.artifactory.storage.db.fs.dao.PackageNugetSearchDaoImpl.toSql;
import static org.artifactory.storage.db.fs.dao.PackageNugetSearchDaoImpl.trimSemVer2MetaData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Volodymyr Shulga
 */
class PackageNugetSearchDaoTest {

    @Test
    void shouldReturnEmptyStringWhenOrderByIsNullOrEmpty() {
        assertEquals("pkg_name asc", toSql(null));
        assertEquals("pkg_name asc", toSql(List.of()));
    }

    @Test
    void shouldFilterOutVersion() {
        /*
        version is not allowed to be used in sql order by clause. It's applied in memory.
         */
        final List<NugetOrderBy> orderBys = List.of(
                new NugetOrderBy(NugetOrderByColumn.ID, NugetSortOrder.ASC),
                new NugetOrderBy(NugetOrderByColumn.VERSION, NugetSortOrder.DESC));
        assertEquals("pkg_name asc", toSql(orderBys));
    }

    @Test
    void onlyTheFirstOrderByIdLeft() {
        final List<NugetOrderBy> orderBys = List.of(
                new NugetOrderBy(NugetOrderByColumn.ID, NugetSortOrder.DESC),
                new NugetOrderBy(NugetOrderByColumn.ID, NugetSortOrder.ASC));

        assertEquals("pkg_name desc", toSql(orderBys));
    }

    @Test
    void twoOrderBysLeft() {
        final List<NugetOrderBy> orderBys = List.of(
                new NugetOrderBy(NugetOrderByColumn.TITLE, NugetSortOrder.DESC),
                new NugetOrderBy(NugetOrderByColumn.PUBLISHED, NugetSortOrder.ASC));

        assertEquals("pkg_title desc, pkg_created asc", toSql(orderBys));
    }

    @Test
    void versionOrderTransformedToPackageName() {
        /*
        version is not allowed to be used in sql order by clause. It's applied in memory.
         */
        List<NugetOrderBy> orderBys = List.of(new NugetOrderBy(NugetOrderByColumn.VERSION, null));
        assertEquals("pkg_name asc", toSql(orderBys));
        orderBys = List.of(new NugetOrderBy(NugetOrderByColumn.VERSION, NugetSortOrder.ASC));
        assertEquals("pkg_name asc", toSql(orderBys));
        orderBys = List.of(new NugetOrderBy(NugetOrderByColumn.VERSION, NugetSortOrder.DESC));
        // in case of invalid sort column, default one is used including order direction
        assertEquals("pkg_name asc", toSql(orderBys));
    }

    @ParameterizedTest
    @MethodSource("queryParam")
    void testParseQMethod(String q, Map<String, String> expectedResult) {
        Map<String, String> result = parseQ(q);
        assertEquals(expectedResult, result);
    }

    private static Stream<Arguments> queryParam() {
        return Stream.of(
                Arguments.of("", Map.of()),
                Arguments.of(null, Map.of()),
                Arguments.of("a", Map.of(QUERY_KEYWORD, "a")),
                Arguments.of("version:hi", Map.of("version", "hi")),
                Arguments.of("version:hi id:hello", Map.of("version", "hi", "id", "hello")),
                Arguments.of("invalidkey:search", Map.of("invalidkey", "search")),
                Arguments.of("onlykey:", Map.of())
        );
    }

    @Test
    void testTrimSemVer2MetaData() {
        assertEquals("1.0.0-beta", trimSemVer2MetaData("1.0.0-beta+metadata.sha1334.blahah"));
        assertEquals("1.0.0", trimSemVer2MetaData("1.0.0+metadata.sha1334.blahah"));
        assertEquals("1.0.0", trimSemVer2MetaData("1.0.0"));
        assertEquals("1.0.0-beta", trimSemVer2MetaData("1.0.0-beta"));
        assertEquals("1.0.0.111", trimSemVer2MetaData("1.0.0.111"));
        assertEquals("sadfasdf", trimSemVer2MetaData("sadfasdf"));
        assertNull(trimSemVer2MetaData(null));
    }
}
