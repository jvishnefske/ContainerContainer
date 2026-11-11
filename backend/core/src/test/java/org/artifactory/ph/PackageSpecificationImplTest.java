package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.event.EventHintType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.ph.PackageSpecificationImpl.PACKAGES_LACKING_SMART_URL_PREFIX;
import static org.artifactory.ph.PackageSpecificationImpl.SPECIAL_SMART_URL_PREFIXES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.AssertJUnit.assertEquals;

class PackageSpecificationImplTest extends ArtifactoryHomeBoundJUnitTest {

    private PackageSpecificationImpl packageSpecification;

    @Mock
    private PackageSpecificationAdapter packageSpecificationAdapter;

    private AutoCloseable mocks;


    @BeforeEach
    void setup() {
        mocks = openMocks(this);
        packageSpecification = new PackageSpecificationImpl(packageSpecificationAdapter);
    }

    @AfterEach
    void teardown() throws Exception {
        EventLogHintManager.clear();
        mocks.close();
    }

    @Test
    void testIsExpirable() throws PackageException {
        when(packageSpecificationAdapter.isExpirable(any(), any(), any())).thenReturn(false);
        when(packageSpecificationAdapter.isSupportedPackage(any(PackageType.class))).thenReturn(true);

        Assertions.assertFalse(packageSpecification.isExpirable(RepoType.CRAN, "repoKey", "a/b/c"));
        verify(packageSpecificationAdapter, times(1)).isExpirable("repoKey", PackageType.CRAN, "a/b/c");
        Mockito.reset(packageSpecificationAdapter);
        when(packageSpecificationAdapter.isExpirable(any(), any(), any())).thenReturn(true);
        when(packageSpecificationAdapter.isSupportedPackage(any(PackageType.class))).thenReturn(true);
        Assertions.assertTrue(packageSpecification.isExpirable(RepoType.CRAN, "repoKey", "a/b/c"));
        verify(packageSpecificationAdapter, times(1)).isExpirable("repoKey", PackageType.CRAN, "a/b/c");
    }

    @Test
    void testShouldExcludeFromReplication() throws PackageException {
        when(packageSpecificationAdapter.shouldExcludeFromReplication(any(), any())).thenReturn(false);
        when(packageSpecificationAdapter.isSupportedPackage(any(PackageType.class))).thenReturn(true);
        Assertions.assertFalse(packageSpecification.isLocalGenerated(RepoType.CRAN, "repoKey", "a/b/c"));
        verify(packageSpecificationAdapter, times(1)).shouldExcludeFromReplication(PackageType.CRAN, "a/b/c");
        Mockito.reset(packageSpecificationAdapter);
        when(packageSpecificationAdapter.shouldExcludeFromReplication(any(), any())).thenReturn(true);
        when(packageSpecificationAdapter.isSupportedPackage(any(PackageType.class))).thenReturn(true);
        Assertions.assertTrue(packageSpecification.isLocalGenerated(RepoType.CRAN, "repoKey", "a/b/c"));
        verify(packageSpecificationAdapter, times(1)).shouldExcludeFromReplication(PackageType.CRAN, "a/b/c");
    }

    @Test
    void testShouldExcludePropertyFromMirroring() throws PackageException {
        when(packageSpecificationAdapter.isLocallyGeneratedProperty(PackageType.CRAN, "repoKey")).thenReturn(false);
        when(packageSpecificationAdapter.isSupportedPackage(any(PackageType.class))).thenReturn(true);
        Assertions.assertFalse(packageSpecification.shouldExcludePropertyFromMirroring(RepoType.CRAN, "repoKey"));
        verify(packageSpecificationAdapter, times(1)).isLocallyGeneratedProperty(PackageType.CRAN, "repoKey");
        Mockito.reset(packageSpecificationAdapter);
        when(packageSpecificationAdapter.isLocallyGeneratedProperty(PackageType.CRAN, "repoKey")).thenReturn(true);
        when(packageSpecificationAdapter.isSupportedPackage(any(PackageType.class))).thenReturn(true);
        Assertions.assertTrue(packageSpecification.shouldExcludePropertyFromMirroring(RepoType.CRAN, "repoKey"));
        verify(packageSpecificationAdapter, times(1)).isLocallyGeneratedProperty(PackageType.CRAN, "repoKey");
    }

    @Test
    void testIsPackageArtifact() throws PackageException {
        when(packageSpecificationAdapter.isSupportedPackage(any(String.class))).thenReturn(true);
        Assertions.assertFalse(packageSpecification.isPackageArtifact("repoKey", "path"));
        verify(packageSpecificationAdapter, times(1)).isPackageArtifact("repoKey", "path");
        Mockito.reset(packageSpecificationAdapter);

        when(packageSpecificationAdapter.isSupportedPackage(any(String.class))).thenReturn(true);
        when(packageSpecificationAdapter.isPackageArtifact("repoKey", "path")).thenReturn(true);
        Assertions.assertTrue(packageSpecification.isPackageArtifact("repoKey", "path"));
        verify(packageSpecificationAdapter, times(1)).isPackageArtifact("repoKey", "path");
    }

    @ParameterizedTest
    @MethodSource("testIsViewable")
    void testView(String path, RepoType repoType, boolean isViewable) {
        when(packageSpecificationAdapter.isViewable("repoKey", path)).thenReturn(isViewable);
        assertEquals(isViewable, packageSpecification.isViewable("repoKey", path, repoType));
    }

    private static Stream<Arguments> testIsViewable() {
        return Stream.of(
                Arguments.of("index/2/hello", RepoType.Cargo, true),
                Arguments.of("index/2/hello", RepoType.Generic, false),
                Arguments.of("index/2/", RepoType.Cargo, false),
                Arguments.of("crates/hello", RepoType.Cargo, false),
                Arguments.of("/Packages", RepoType.Debian, true),
                Arguments.of("dists/bionic/Release", RepoType.Debian, true),
                // Paths that do not end with /Packages or /Release are not viewable
                Arguments.of("dists/bionic/InRelease", RepoType.Debian, false),
                Arguments.of("dists/bionic/main/binary-amd64/Packages.gz", RepoType.Debian, false),
                Arguments.of("/Packages", RepoType.Generic, false),
                Arguments.of("/.podspec", RepoType.CocoaPods, true),
                Arguments.of("/pod/spec", RepoType.CocoaPods, false),
                Arguments.of("viewable", RepoType.Npm, true),
                Arguments.of("not-viewable", RepoType.Npm, false)
        );
    }

    @ParameterizedTest
    @MethodSource("testIsPackageArtifactFallbackProvider")
    void testIsPackageArtifactFallback(RepoType repoType, String path, boolean expectedResult)
            throws PackageException {
        when(packageSpecificationAdapter.isSupportedPackage(any(String.class))).thenReturn(false);
        Assertions.assertEquals(expectedResult, packageSpecification.isPackageArtifact("repoKey", path, repoType));
        verify(packageSpecificationAdapter, never()).isPackageArtifact("repoKey", path);
    }

    private static Stream<Arguments> testIsPackageArtifactFallbackProvider() {
        return Stream.of(Arguments.of(RepoType.Npm, "path.tgz", true),
                Arguments.of(RepoType.Npm, "path.zip", false),
                Arguments.of(RepoType.NuGet, "path.tgz", false),
                Arguments.of(RepoType.NuGet, "path.nupkg", true)
        );
    }

    @Test
    void excludePropertyValueFromMirroring() throws PackageException {
        when(packageSpecificationAdapter.isSupportedPackage(PackageType.NPM)).thenReturn(true);
        EventLogHintManager.put(EventHintType.FederatedReplication, "key.value");
        packageSpecification.excludePropertyValueFromMirroring("key", Set.of("value"), RepoType.Npm);
        verify(packageSpecificationAdapter, times(1)).excludePropertyValueFromMirroring("key", Set.of("value"),
                PackageType.NPM);
        verify(packageSpecificationAdapter, times(1)).isSupportedPackage(PackageType.NPM);
    }

    @Test
    void excludePropertyValueFromMirroringNotNeedEventLoggerHintNull() throws PackageException {
        if (EventLogHintManager.getHints() != null) {
            EventLogHintManager.getHints().clear();
        }
        when(packageSpecificationAdapter.isSupportedPackage(PackageType.NPM)).thenReturn(true);
        packageSpecification.excludePropertyValueFromMirroring("key", Set.of("value"), RepoType.Npm);
        verify(packageSpecificationAdapter, times(0)).excludePropertyValueFromMirroring("key", Set.of("value"),
                PackageType.NPM);
        verify(packageSpecificationAdapter, times(1)).isSupportedPackage(PackageType.NPM);
    }

    @Test
    void excludePropertyValueFromMirroringNotNeedPackageTypeNull() {
        packageSpecification.excludePropertyValueFromMirroring("key", Set.of("value"), RepoType.Distribution);
        verify(packageSpecificationAdapter, times(0)).excludePropertyValueFromMirroring(anyString(), anySet(),
                any(PackageType.class));
    }

    @Test
    void excludePropertyValueFromMirroringNotNeedPackageTypeNotSupported() throws PackageException {
        when(packageSpecificationAdapter.isSupportedPackage(PackageType.NPM)).thenReturn(false);
        packageSpecification.excludePropertyValueFromMirroring("key", Set.of("value"), RepoType.Npm);
        verify(packageSpecificationAdapter, times(0)).excludePropertyValueFromMirroring("key", Set.of("value"),
                PackageType.NPM);
        verify(packageSpecificationAdapter, times(1)).isSupportedPackage(PackageType.NPM);
    }

    @Test
    void excludePropertyValueFromMirroringNotNeedEventLoggerHintContainsRal() throws PackageException {
        when(packageSpecificationAdapter.isSupportedPackage(PackageType.NPM)).thenReturn(false);
        EventLogHintManager.put(EventHintType.ReplicationAllowed, "key.value");
        packageSpecification.excludePropertyValueFromMirroring("key", Set.of("value"), RepoType.Npm);
        verify(packageSpecificationAdapter, times(0)).excludePropertyValueFromMirroring("key", Set.of("value"),
                PackageType.NPM);
        verify(packageSpecificationAdapter, times(1)).isSupportedPackage(PackageType.NPM);
    }

    @ParameterizedTest
    @MethodSource("testGetVirtualReposContainingRepoProvider")
    void testGetVirtualReposContainingRepo(RepoType virtualRepoType, RepoType aggregatedRepoType,
            boolean expectedResult) {
        Assertions.assertEquals(expectedResult,
                packageSpecification.isVirtualCanContainRepoWithType(virtualRepoType, aggregatedRepoType));
    }

    @ParameterizedTest
    @MethodSource("provideRepoTypeAndExpectedPrefix")
    void testGetPrefixForSmartUrl(RepoType repoType, String expectedPrefix) {
        Assertions.assertEquals(expectedPrefix, packageSpecification.getPrefixForSmartUrl(repoType));
    }

    @Test
    void testGetPrefixForSmartUrl_nullRepoType() {
        Assertions.assertNull(packageSpecification.getPrefixForSmartUrl(null));
    }

    private static Stream<Arguments> testGetVirtualReposContainingRepoProvider() {
        return Stream.of(
                Arguments.of(RepoType.Generic, RepoType.Docker, true),
                Arguments.of(RepoType.OCI, RepoType.HelmOCI, true),
                Arguments.of(RepoType.Maven, RepoType.SBT, true),
                Arguments.of(RepoType.SBT, RepoType.Ivy, true),
                Arguments.of(RepoType.Helm, RepoType.Helm, true),
                Arguments.of(RepoType.Maven, RepoType.Generic, false),
                Arguments.of(RepoType.HelmOCI, RepoType.OCI, false),
                Arguments.of(RepoType.Helm, RepoType.NuGet, false));
    }

    @Test
    void testHasPathReindexReturnsTrueForConan() {
        Assertions.assertTrue(packageSpecification.hasPathReindex(RepoType.Conan));
    }

    @Test
    void testHasPathReindexReturnsFalseForNonConanTypes() {
        for (RepoType repoType : RepoType.values()) {
            if (repoType != RepoType.Conan) {
                Assertions.assertFalse(packageSpecification.hasPathReindex(repoType),
                        "Expected hasPathReindex to be false for " + repoType);
            }
        }
    }

    @Test
    void testHasPathReindexReturnsFalseForNull() {
        Assertions.assertFalse(packageSpecification.hasPathReindex(null));
    }

    private static Stream<Arguments> provideRepoTypeAndExpectedPrefix() {
        List<Arguments> args = new ArrayList<>();
        for (RepoType packageType : RepoType.values()) {
            String expected;
            if (PACKAGES_LACKING_SMART_URL_PREFIX.contains(packageType)) {
                expected = null;
            } else if (SPECIAL_SMART_URL_PREFIXES.containsKey(packageType)) {
                expected = SPECIAL_SMART_URL_PREFIXES.get(packageType);
            } else {
                expected = packageType.getType();
            }
            args.add(Arguments.of(packageType, expected));
        }
        return args.stream();
    }
}