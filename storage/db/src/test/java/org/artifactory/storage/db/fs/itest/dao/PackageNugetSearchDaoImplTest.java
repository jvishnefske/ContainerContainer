package org.artifactory.storage.db.fs.itest.dao;

import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.model.search.nuget.NugetSearchParams;
import com.jfrog.ph.acl.model.search.nuget.NugetSearchResult;
import com.jfrog.ph.acl.model.search.nuget.filter.NugetAccumulatingFilter;
import com.jfrog.ph.acl.model.search.nuget.filter.NugetPackageFilter;
import com.jfrog.ph.acl.model.search.nuget.orderby.NugetOrderBy;
import com.jfrog.ph.acl.model.search.nuget.orderby.NugetOrderByColumn;
import com.jfrog.ph.acl.model.search.nuget.orderby.NugetSortOrder;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.storage.db.fs.dao.PackageNugetSearchDaoImpl;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.BINARY_SIZE;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.DOWNLOAD_COUNT;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.LAST_UPDATED;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NODE_ID;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_AUTHORS;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_COPYRIGHT;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_DEPENDENCY;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_DESCRIPTION;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_DIGEST;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_ID;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_RAW_VERSION;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_REQUIRE_LICENSE_ACCEPTANCE;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_TAGS;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.NUGET_VERSION;
import static com.jfrog.ph.acl.model.search.nuget.NugetAclConstants.PUBLISHED;
import static org.artifactory.storage.db.fs.dao.PackageNugetSearchDaoImpl.getArtifactPropValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;


/**
 * author: einasa
 */
public class PackageNugetSearchDaoImplTest extends DbBaseTest {

    @Autowired
    private PackageNugetSearchDaoImpl packageNugetSearch;
    private final AuthorizationService authService = mock(AuthorizationService.class);
    private NugetAccumulatingFilter accumulatingFilter;

    private final static String REPO_KEY = "nuget-local";

    @BeforeClass
    public void setup() throws Exception {
        super.springTestContextPrepareTestInstance();
        addBean(authService, AuthorizationService.class);
        importSql("/sql/nuget_search.sql");
    }

    @Test
    public void nugetV3SearchHappyPathWithPrerelease() {
        NugetSearchParams searchParams = createSearchParams("shay-1-test");
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 3);
        assertEquals(packages.getTotalUniquePackages(), 1);
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(0)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(1)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(2)),
                "shay-1-test");
    }

    @Test(description = "Test that the raw version is not present in the search result as this property will " +
            "not be present for non-normalised repos")
    public void nugetV3SearchResultDoesNotContainRawVersionProperty() {
        NugetSearchParams searchParams = createSearchParams("Azure.Core");
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 1);
        assertEquals(packages.getTotalUniquePackages(), 1);
        //The Azure.core package props in nuget_search.sql does not contain the raw version property. This is to
        // simulate the scenarios where this property is not present for the non-normalised repos. Hence, the raw
        // version should not be present in the retrieved props values as well.
        assertNull(getArtifactPropValue(NUGET_RAW_VERSION, packages.getMatchedPackages().get(0)),
                "The raw version should not be present in the retrieved props values");
    }

    @Test
    public void nugetV3SearchReturnAllPkgsInCaseWrongParamPattern() {
        NugetSearchParams searchParams = createSearchParams("packageid: ");
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 6);
        assertEquals(packages.getTotalUniquePackages(), 4);

        //Verify order reserved
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(0)),
                "Azure.Core");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(1)),
                "shay-1-t");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(2)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(3)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(4)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(5)),
                "ZstdSharp.Port");
    }

    public void nugetV3SearchOnTwoSpecificProperty() {
        NugetSearchParams searchParams = createSearchParams("id:shay version:25.1.0");
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 1);
        assertEquals(packages.getTotalUniquePackages(), 1);
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(0)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_VERSION, packages.getMatchedPackages().get(0)),
                "25.1.0");
    }

    @DataProvider(name = "searchForAllProps")
    public Object[][] searchForAllProps() {
        return new Object[][] {
                {"id:Azure"},
                {"nuget.id:Azure.core"},
                {"packageid:Azure.core"},
                {"nuget.version:1.37"},
                {"version:1.37.0"},
                {"nuget.version:1.37+build-alpha"},
                {"version:1.37.0+build-alpha"},
                {"title:title"},
                {"tags:Azure"},
                {"author:Microsoft"},
                {"description:implementation"},
                {"summary:summary of azure"}
        };
    }

    @Test(dataProvider = "searchForAllProps")
    public void nugetV3SearchOnSpecificPropertiesCoverAllProperties(String searchParamsStr) {
        NugetSearchParams searchParams = createSearchParams(searchParamsStr);
        NugetAccumulatingFilter accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 1);
        assertEquals(packages.getTotalUniquePackages(), 1);
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(0)), "Azure.Core");
        assertEquals(getArtifactPropValue(NUGET_VERSION, packages.getMatchedPackages().get(0)), "1.37.0");
    }

    @DataProvider(name = "unHappyPathCasesForNugetSearchSpecificProp")
    public Object[][] unHappyPath() {
        return new Object[][] {
                {"id:nonExists"},
                {"nuget.id:Azure"},
                {"packageid:Azure"},
                {"nuget.version:0.1.37"},
                {"version:1.0"},
                {"title:none"},
                {"tags:none"},
                {"author:none"},
                {"description:none"},
                {"summary:none"}
        };
    }

    @Test(dataProvider = "unHappyPathCasesForNugetSearchSpecificProp")
    public void nugetV3SearchSpecificPropUnhappyPath(String searchParamsStr) {
        NugetSearchParams searchParams = createSearchParams(searchParamsStr);
        NugetAccumulatingFilter accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 0);
        assertEquals(packages.getTotalUniquePackages(), 0);
    }

    @DataProvider(name = "invalidPropertyForNugetV3SearchOnSpecificProps")
    public Object[][] invalidProps() {
        return new Object[][] {
                {"null:"},
                {"none:"},
                {":none"},
                {":null"},
                {":"},
                {"N:iHerb.Common.RabbitMq.DI.ServiceCollection"},
                {"T:SftpClient"},
                {"T:Azure"},
                {"T:shay"}
        };
    }

    @Test(dataProvider = "invalidPropertyForNugetV3SearchOnSpecificProps")
    public void invalidPropertyForNugetV3SearchOnSpecificPropsTest(String searchParamsStr) {
        NugetSearchParams searchParams = createSearchParams(searchParamsStr);
        NugetAccumulatingFilter accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 6);
        assertEquals(packages.getTotalUniquePackages(), 4);
    }

    @DataProvider(name = "ownerPropertyForNugetV3SearchOnSpecificProps")
    public Object[][] ownerProps() {
        return new Object[][] {
                {"owner:alex", 1, 1},
                {"owner:support", 2, 1},
                {"owner:supporting", 0, 0},
                {"owner:support team", 2, 1},
                {"owner:owner", 1, 1},
                {"owner:Alex", 1, 1},
                {"owner:alex is the owner", 1, 1},
                {"owner:Adam", 0, 0}
        };
    }

    @Test(dataProvider = "ownerPropertyForNugetV3SearchOnSpecificProps")
    public void ownerPropertyForNugetV3SearchOnSpecificPropsTest(String searchParamsStr, int total, int unique) {
        NugetSearchParams searchParams = createSearchParams(searchParamsStr);
        NugetAccumulatingFilter accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), total);
        assertEquals(packages.getTotalUniquePackages(), unique);
    }

    @Test
    public void nugetV2SearchHappyPath() {
        NugetSearchParams searchParams = createSearchParams("shay-1-test");
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV2(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 3);
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(0)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(1)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(2)),
                "shay-1-test");
    }

    @Test
    public void nugetV2SearchWithRelevancy() {
        NugetSearchParams searchParams = createSearchParams("shay-1-t");
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV2(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 4);
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(0)),
                "shay-1-t");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(1)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(2)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(3)),
                "shay-1-test");
    }

    @Test
    public void nugetV3SearchWithRelevancy() {
        NugetSearchParams searchParams = createSearchParams("shay-1-t");
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 4);
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(0)),
                "shay-1-t");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(1)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(2)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(3)),
                "shay-1-test");
    }

    @Test
    public void nugetV3SearchToCheckDownloadCount() {
        NugetSearchParams searchParams = createSearchParams("");
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 6);
        assertEquals(packages.getTotalUniquePackages(), 4);
        assertPackageIds(packages, "Azure.Core", "shay-1-t", "shay-1-test", "shay-1-test",
                "shay-1-test", "ZstdSharp.Port");
        assertEquals(packages.getMatchedPackages().get(0).getDownloadCount(), 11L);
        assertEquals(getArtifactPropValue(DOWNLOAD_COUNT, packages.getMatchedPackages().get(0)), "11");
        assertNull(getArtifactPropValue(DOWNLOAD_COUNT, packages.getMatchedPackages().get(4)));
    }

    @Test
    public void nugetV2SearchOrderByIdAsc() {
        NugetSearchParams searchParams = createSearchParams("");
        searchParams.setOrderBy(List.of(new NugetOrderBy(NugetOrderByColumn.ID, NugetSortOrder.ASC)));
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages = packageNugetSearch.searchNugetPackageV2(searchParams, accumulatingFilter);
        assertPackageIds(packages, "Azure.Core", "shay-1-t", "shay-1-test", "shay-1-test",
                "shay-1-test", "ZstdSharp.Port");
    }

    @Test
    public void nugetV2SearchOrderByIdDesc() {
        NugetSearchParams searchParams = createSearchParams("");
        searchParams.setOrderBy(List.of(new NugetOrderBy(NugetOrderByColumn.ID, NugetSortOrder.DESC)));
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages = packageNugetSearch.searchNugetPackageV2(searchParams, accumulatingFilter);
        assertPackageIds(packages, "ZstdSharp.Port", "shay-1-test", "shay-1-test", "shay-1-test",
                "shay-1-t", "Azure.Core");
        assertEquals(getArtifactPropValue(DOWNLOAD_COUNT, packages.getMatchedPackages().get(5)), "11");
        assertNull(getArtifactPropValue(DOWNLOAD_COUNT, packages.getMatchedPackages().get(3)));
    }

    @Test
    public void nugetSearchIsCaseInsensitiveTest() {
        NugetSearchParams searchParams = createSearchParams("SHAY-1-test");
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 3);
        assertEquals(packages.getTotalUniquePackages(), 1);
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(0)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(1)),
                "shay-1-test");
        assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(2)),
                "shay-1-test");
    }

    @Test
    public void nugetSearchFilterPackagesBySubstringTest() {
        NugetSearchParams searchParams = createSearchParams("a");
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 6);
        assertEquals(packages.getTotalUniquePackages(), 4);
    }

    @Test
    public void searchWithNoPermissionReturnsEmptyResults() {
        NugetPackageFilter filterDenyingReads = mock(NugetPackageFilter.class);
        when(filterDenyingReads.process(any())).thenReturn(false);

        NugetSearchParams searchParams = createSearchParams("shay-1-test");
        accumulatingFilter = createAccumulatingFilter(false);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 0);
        assertEquals(packages.getTotalUniquePackages(), 0);
    }

    //nuget search can be done by searching for one the properties, nuget.id, nuget,version, nuget.summary ..etc
    @Test(dataProvider = "searchTermV3Provider")
    public void nugetV3SearchByTheEightPropertiesTest(String searchTerm) {
        NugetSearchParams searchParams = createSearchParams(searchTerm);
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 1);
        assertEquals(packages.getTotalUniquePackages(), 1);
        assertEquals(getArtifactPropValue(NUGET_ID,
                packages.getMatchedPackages().get(0)), "Azure.Core");
        assertEquals(
                getArtifactPropValue(DOWNLOAD_COUNT, packages.getMatchedPackages().get(0)), "11");
    }

    //nuget search can be done by searching for one the properties, nuget.id, nuget,version, nuget.summary ..etc
    @Test(dataProvider = "searchTermV2Provider")
    public void nugetV2SearchByTheEightPropertiesTest(String searchTerm, int expectedResultsCount) {
        NugetSearchParams searchParams = createSearchParams(searchTerm);
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV2(searchParams, accumulatingFilter);
        if (expectedResultsCount > 0) {
            assertEquals(packages.getMatchedPackages().size(), 1);
            assertEquals(getArtifactPropValue(NUGET_ID, packages.getMatchedPackages().get(0)), "Azure.Core");
            assertEquals(getArtifactPropValue(DOWNLOAD_COUNT, packages.getMatchedPackages().get(0)), "11");
        } else {
            assertEquals(packages.getMatchedPackages().size(), 0);
        }
    }

    @Test
    public void nugetSearchReturningNoPackagesWhenPredicateIsFalseTest() {
        when(authService.canRead(any())).thenReturn(true);
        NugetPackageFilter filterReturningFalse = mock(NugetPackageFilter.class);
        when(filterReturningFalse.process(any())).thenReturn(false);

        NugetSearchParams searchParams = createSearchParams("");
        accumulatingFilter = createAccumulatingFilter(false);

        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 0);
        assertEquals(packages.getTotalUniquePackages(), 0);
    }

    @Test
    public void verifyPropertiesForV3() {
        NugetSearchParams searchParams = createSearchParams("shay-1-test");
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV3(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 3);
        assertEquals(packages.getTotalUniquePackages(), 1);
        PackageArtifact searchRes = packages.getMatchedPackages().stream()
                .filter(pa -> pa.getProperties().get(NUGET_VERSION).iterator().next().equals("24.1.0-prerelease"))
                .findFirst().orElse(null);

        assertNotNull(searchRes);
        assertEquals(searchRes.getProperties().size(), 12);
        assertEquals(getArtifactPropValue(NUGET_ID, searchRes), searchParams.getSearchTerm());
        assertEquals(getArtifactPropValue(NUGET_VERSION, searchRes), "24.1.0-prerelease");
        assertEquals(getArtifactPropValue(NUGET_RAW_VERSION, searchRes), "24.1.0-prerelease+271024-020017-f4bafg");
        assertEquals(getArtifactPropValue(NUGET_AUTHORS, searchRes), "Support");
        assertEquals(getArtifactPropValue(NUGET_DESCRIPTION, searchRes), "bla bla");
        assertEquals(getArtifactPropValue(NUGET_COPYRIGHT, searchRes), "Copyright 2016");
        assertEquals(getArtifactPropValue(NUGET_TAGS, searchRes), "Tag1 Tag2");
        assertEquals(searchRes.getProperties().get(NUGET_DEPENDENCY).stream().toList(),
                List.of("SampleDependency:1.0", "AnotherDependency:2.0"));
        assertEquals(getArtifactPropValue(NUGET_DIGEST, searchRes),
                "1M+QxIjHH9eMlzYFEf3BVAf7HrfOyX6ykp787vKVq5d3QxLxToP0i0FmP/E6HCSR8wu0LSFvecJeQHyCQ0cbzQ==");
        assertEquals(getArtifactPropValue(NUGET_REQUIRE_LICENSE_ACCEPTANCE, searchRes), "false");
    }

    @Test
    public void verifyPropertiesForV2() {
        NugetSearchParams searchParams = createSearchParams("shay-1-test");
        accumulatingFilter = createAccumulatingFilter(true);
        NugetSearchResult packages =
                packageNugetSearch.searchNugetPackageV2(searchParams, accumulatingFilter);
        assertEquals(packages.getMatchedPackages().size(), 3);
        assertEquals(packages.getTotalUniquePackages(), 1);
        PackageArtifact searchRes = packages.getMatchedPackages().stream()
                .filter(pa -> pa.getProperties().get(NUGET_VERSION).iterator().next().equals("24.1.0-prerelease"))
                .findFirst().orElse(null);

        assertNotNull(searchRes);
        assertEquals(searchRes.getProperties().size(), 15);
        assertEquals(getArtifactPropValue(NUGET_ID, searchRes), searchParams.getSearchTerm());
        assertEquals(getArtifactPropValue(NUGET_VERSION, searchRes), "24.1.0-prerelease");
        assertEquals(getArtifactPropValue(NUGET_AUTHORS, searchRes), "Support");
        assertEquals(getArtifactPropValue(NUGET_DESCRIPTION, searchRes), "bla bla");
        assertEquals(getArtifactPropValue(NUGET_COPYRIGHT, searchRes), "Copyright 2016");
        assertEquals(getArtifactPropValue(NUGET_TAGS, searchRes), "Tag1 Tag2");
        assertEquals(getArtifactPropValue(NUGET_RAW_VERSION, searchRes), "24.1.0-prerelease+271024-020017-f4bafg");
        assertEquals(searchRes.getProperties().get(NUGET_DEPENDENCY).stream().toList(),
                List.of("SampleDependency:1.0", "AnotherDependency:2.0"));
        assertEquals(getArtifactPropValue(BINARY_SIZE, searchRes), "2026");
        assertEquals(getArtifactPropValue(LAST_UPDATED, searchRes), "1709109860081");
        assertEquals(getArtifactPropValue(PUBLISHED, searchRes), "1709108809148");
        assertEquals(getArtifactPropValue(NUGET_DIGEST, searchRes),
                "1M+QxIjHH9eMlzYFEf3BVAf7HrfOyX6ykp787vKVq5d3QxLxToP0i0FmP/E6HCSR8wu0LSFvecJeQHyCQ0cbzQ==");
        assertEquals(
                getArtifactPropValue(NUGET_REQUIRE_LICENSE_ACCEPTANCE, searchRes), "false");
    }

    private NugetSearchParams createSearchParams(String searchTerm) {
        return NugetSearchParams.builder()
                .repoKey(REPO_KEY)
                .searchTerm(searchTerm)
                .offset(0)
                .limit(100)
                .build();
    }

    private NugetAccumulatingFilter createAccumulatingFilter(boolean acceptResults) {
        return new DummyAccumulatingFilter(acceptResults);
    }

    private static class DummyAccumulatingFilter implements NugetAccumulatingFilter {
        private final boolean acceptEntries;
        private final List<Long> matchingNodeIds;
        private final Set<String> uniqueIds;

        public DummyAccumulatingFilter(boolean acceptEntries) {
            this.acceptEntries = acceptEntries;
            this.matchingNodeIds = new ArrayList<>();
            this.uniqueIds = new HashSet<>();
        }

        @Override
        public boolean process(Map<String, Collection<String>> packageProperties) {
            if (acceptEntries) {
                uniqueIds.add(packageProperties.get(NUGET_ID).iterator().next());
                matchingNodeIds.add(
                        Long.parseLong(packageProperties.get(NODE_ID).iterator().next()));
            }
            return acceptEntries;
        }

        @Override
        public Pair<List<Long>, Integer> getMatchingNodeIds() {
            return Pair.of(matchingNodeIds, uniqueIds.size());
        }
    }

    @DataProvider
    private Object[][] searchTermV3Provider() {
        return new Object[][] {
                {"azure.core"},
                {"1.37.0"},
                {"Microsoft Azure Client Pipeline"},
                {"this is the title"},
                {"Microsoft"},
                {"This is the implementation of the Azure Client Pipeline"},
                {"summary of azure"},
                {"alex is the owner"}};
    }

    @DataProvider
    private Object[][] searchTermV2Provider() {
        return new Object[][] {
                {"azure.core", 1},
                {"1.37.0", 0},
                {"Microsoft Azure Client Pipeline", 0},
                {"this is the title", 0},
                {"Microsoft", 0},
                {"This is the implementation of the Azure Client Pipeline", 1},
                {"summary of azure", 0},
                {"alex is the owner", 0}};
    }

    private void assertPackageIds(NugetSearchResult packages, String... packageNames) {
        assertEquals(packages.getMatchedPackages().size(), packageNames.length);
        String expected = packages.getMatchedPackages().stream().map(
                        pa -> getArtifactPropValue(NUGET_ID, pa))
                .collect(Collectors.joining(","));
        String actual = String.join(",", packageNames);
        assertEquals(expected, actual);
    }
}
