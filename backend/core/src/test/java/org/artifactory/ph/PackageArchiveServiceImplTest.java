package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.exception.PackageUnsupportedOperationException;
import com.jfrog.ph.acl.model.packagearchive.PackageArchive;
import com.jfrog.ph.acl.model.packagearchive.PackageArchiveArtifact;
import com.jfrog.ph.acl.model.packagearchive.PackageArchiveQuery;
import com.jfrog.ph.acl.model.packagearchive.PackageArchivesResult;
import lombok.SneakyThrows;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.packagesarchive.PackagesArchiveAddon;
import org.artifactory.common.repo.PackageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.ph.PackageArchiveServiceImpl.PACKAGE_NOT_SUPPORTED_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;


class PackageArchiveServiceImplTest {

    AddonsManager addonsManager;
    PackageArchiveService packageArchiveService;
    PackagesArchiveAddon addon;

    @BeforeEach
    void setUp() {
        addonsManager = Mockito.mock(AddonsManager.class);
        addon = Mockito.mock(PackagesArchiveAddon.class);
        Mockito.when(addonsManager.addonByType(PackagesArchiveAddon.class)).thenReturn(addon);
        packageArchiveService = Mockito.spy(new PackageArchiveServiceImpl(addonsManager));
    }

    @ParameterizedTest
    @MethodSource("supportedPackagesTestData")
    void isPackageArchivingSupported(PackageType packageType, boolean archivingSupported) {
        assertEquals(packageArchiveService.isPackageArchivingSupported(packageType), archivingSupported);
    }


    @Test
    void getAllSupportedPackages() {
        assertEquals(
                Set.of(PackageType.DOCKER, PackageType.MAVEN, PackageType.GRADLE, PackageType.OCI, PackageType.HELMOCI,
                        PackageType.NPM, PackageType.NUGET, PackageType.YUM, PackageType.GENERIC, PackageType.CONAN,
                        PackageType.HELM, PackageType.DEBIAN, PackageType.GO, PackageType.PYPI, PackageType.GEMS,
                        PackageType.TERRAFORM, PackageType.COCOAPODS, PackageType.HUGGINGFACEML, PackageType.CARGO,
                        PackageType.TERRAFORMBACKEND, PackageType.MACHINELEARNING, PackageType.ANSIBLE,
                        PackageType.CONDA, PackageType.SBT, PackageType.ALPINE, PackageType.HEX, PackageType.VAGRANT,
                        PackageType.CHEF, PackageType.PUPPET, PackageType.SWIFT, PackageType.OPKG,
                        PackageType.COMPOSER, PackageType.SKILLS, PackageType.AGENTPACKAGES,
                        PackageType.AGENTPLUGINS, PackageType.LUAROCKS),
                packageArchiveService.getAllSupportedPackages().stream().map(PackageArchiveConfig::packageType)
                        .collect(Collectors.toSet()));
    }

    @Test
    void getPackageToArchiveFromAddonTest() throws PackageException {
        PackageArchiveQuery query = new PackageArchiveQuery("stam/path/to/repo", PackageType.DOCKER, 0, 0, 0,
                "ab", "cd", 100);
        packageArchiveService.getPackageToArchiveResult(query);
        Mockito.verify(addon, times(1)).getPackagesToArchive(query);
    }

    @Test
    void getPackageToArchiveWithResultFromAddonTest() throws PackageException {
        PackageArchiveQuery query = new PackageArchiveQuery("stam/path/to/repo", PackageType.DOCKER, 0, 0, 0,
                "ab", "cd", 100);
        when(packageArchiveService.getPackageToArchiveResult(query)).thenReturn(
                new PackageArchivesResult(List.of(), null));
        PackageArchivesResult packageToArchiveResult = packageArchiveService.getPackageToArchiveResult(query);
        assertEquals(packageToArchiveResult.packageArchives(), List.of());
        Mockito.verify(addon, times(1)).getPackagesToArchive(query);
    }

    @Test
    void getPackageToArchiveWithoutResultFromAddonTest() throws PackageException {
        PackageArchiveQuery query = new PackageArchiveQuery("stam/path/to/repo", PackageType.DOCKER, 0, 0, 0,
                "ab", "cd", 100);
        when(packageArchiveService.getPackageToArchiveResult(query)).thenReturn(null);
        PackageArchivesResult packageToArchiveResult = packageArchiveService.getPackageToArchiveResult(query);
        assertNull(packageToArchiveResult);
        Mockito.verify(addon, times(1)).getPackagesToArchive(query);
    }

    @Test
    void getPackagesCountToArchiveTest() throws PackageException {
        PackageArchiveQuery query = new PackageArchiveQuery("stam/path/to/repo", PackageType.DOCKER, 0, 0, 0,
                "ab", "cd", 100);
        assertEquals(0, packageArchiveService.getPackagesCountToArchive(query));
    }

    @Test
    void getPackagesCountToArchiveFromAddonTest() throws PackageException {
        PackageArchiveQuery query = new PackageArchiveQuery("stam/path/to/repo", PackageType.DOCKER, 0, 0, 0,
                "ab", "cd", 100);
        packageArchiveService.getPackagesCountToArchive(query);
        Mockito.verify(addon, times(1)).getPackagesCountToArchive(query);
    }

    @Test
    void getPackagesCountToArchiveDefaultImpl() throws PackageException {
        PackageArchiveQuery query = new PackageArchiveQuery("stam/path/to/repo", PackageType.DOCKER, 0, 0, 0,
                "ab", "cd", 100);
        PackageArchiveService defaultArchiveService = spy(PackageArchiveService.class);
        assertEquals(0, defaultArchiveService.getPackagesCountToArchive(query));
    }

    @Test
    void validateQueryDefaultImpl() throws PackageException {
        PackageArchiveQuery query = new PackageArchiveQuery("stam/path/to/repo", PackageType.DOCKER, 0, 0, 0,
                "ab", "cd", 100);
        PackageArchiveService defaultArchiveService = spy(PackageArchiveService.class);
        assertTrue(defaultArchiveService.validateQuery(query));
    }

    @Test
    void validateQuery() throws PackageException {
        PackageArchiveQuery query = new PackageArchiveQuery("stam/path/to/repo", PackageType.DOCKER, 0, 0, 0,
                "ab", "cd", 100);
        when(addon.validateQuery(query)).thenReturn(true);
        assertTrue(packageArchiveService.validateQuery(query));

        assertThrows(PackageException.class, () -> packageArchiveService.validateQuery(
                new PackageArchiveQuery("stam/path/to/repo", PackageType.IVY, 0, 0, 0,
                        "ab", "cd", 100)));

    }

    @SneakyThrows
    @Test
    void getPackageLeadFilePathValidRequestReturnsLeadArtifactPath() {
        String packageType = "PYPI";
        String packageRepoName = "repo";
        String packageName = "package";
        String packageVersion = "1.0";
        String expectedLeadArtifactPath = "path/to/lead/artifact";

        PackageArchiveQuery query = new PackageArchiveQuery(
                List.of(packageRepoName), PackageType.valueOf(packageType), 0, 0, 0, packageName, packageVersion, 1);

        PackageArchive packageArchive =
                new PackageArchive(List.of(new PackageArchiveArtifact(expectedLeadArtifactPath, true)),
                        PackageType.PYPI, 0, 0, 0, packageName, packageVersion, expectedLeadArtifactPath);

        when(addonsManager.addonByType(PackagesArchiveAddon.class)).thenReturn(addon);
        when(addon.getPackagesToArchive(query)).thenReturn(new PackageArchivesResult(List.of(packageArchive), null));

        PackageArchive result =
                packageArchiveService.getPackageVersion(packageType, packageRepoName, packageName, packageVersion);

        assertEquals(expectedLeadArtifactPath, result.leadArtifactPath());
        Mockito.verify(addon, times(1)).getPackagesToArchive(query);
    }

    @SneakyThrows
    @Test
    void getPackageLeadFilePathUnsupportedPackageTypeThrowsException() {
        String packageType = "JEM";
        String packageRepoName = "repo";
        String packageName = "package";
        String packageVersion = "1.0";

        PackageException exception = assertThrows(PackageUnsupportedOperationException.class, () ->
                packageArchiveService.getPackageVersion(packageType, packageRepoName, packageName,
                        packageVersion));

        assertEquals(String.format(PACKAGE_NOT_SUPPORTED_ERROR, packageType), exception.getInternalReason());
        assertEquals("Not supported", exception.getMessage());
    }

    @SneakyThrows
    @Test
    void getPackageLeadFilePathBadPackageTypeThrowsException() {
        String packageType = "KUKULUKO";
        String packageRepoName = "repo";
        String packageName = "package";
        String packageVersion = "1.0";

        PackageException exception = assertThrows(PackageNotFoundException.class, () ->
                packageArchiveService.getPackageVersion(packageType, packageRepoName, packageName,
                        packageVersion));

        assertEquals(String.format("Package type KUKULUKO is not supported"), exception.getInternalReason());
        assertEquals("Not found", exception.getMessage());
    }

    @Test
    void getPackageLeadFilePathNoLeadFileFoundThrowsException() {
        String packageType = "PYPI";
        String packageRepoName = "repo";
        String packageName = "package";
        String packageVersion = "1.0";

        PackageException exception = assertThrows(PackageNotFoundException.class, () ->
                packageArchiveService.getPackageVersion(packageType, packageRepoName, packageName,
                        packageVersion));

        assertEquals("Not found", exception.getMessage());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("packageArchivesTestProvider")
    void getPackageLeadFilePathEmptyOrNullPackageArchivesThrowsPackageNotFoundException() {
        String packageType = "PYPI";
        String packageRepoName = "repo";
        String packageName = "package";
        String packageVersion = "1.0";

        when(addonsManager.addonByType(PackagesArchiveAddon.class)).thenReturn(addon);
        when(addon.getPackagesToArchive(any())).thenReturn(new PackageArchivesResult(List.of(), null));

        PackageException exception = assertThrows(PackageNotFoundException.class, () ->
                packageArchiveService.getPackageVersion(packageType, packageRepoName, packageName,
                        packageVersion));

        assertInstanceOf(PackageNotFoundException.class, exception);
    }

    private static Stream<Arguments> packageArchivesTestProvider() {
        return Stream.of(
                Arguments.of(new PackageArchivesResult(List.of(), null)),
                Arguments.of(null, null)
        );
    }

    private static Stream<Arguments> supportedPackagesTestData() {
        //packageType, archivingSupported
        return Stream.of(
                Arguments.of(PackageType.NPM, true),
                Arguments.of(PackageType.PYPI, true),
                Arguments.of(PackageType.DOCKER, true),
                Arguments.of(PackageType.MAVEN, true),
                Arguments.of(PackageType.GRADLE, true),
                Arguments.of(PackageType.NUGET, true),
                Arguments.of(PackageType.CONAN, true),
                Arguments.of(PackageType.YUM, true),
                Arguments.of(PackageType.GO, true),
                Arguments.of(PackageType.DEBIAN, true),
                Arguments.of(PackageType.COCOAPODS, true),
                Arguments.of(PackageType.SBT, true),
                Arguments.of(PackageType.ALPINE, true),
                Arguments.of(PackageType.HEX, true),
                Arguments.of(PackageType.VAGRANT, true),
                Arguments.of(PackageType.CHEF, true),
                Arguments.of(PackageType.PUPPET, true),
                Arguments.of(PackageType.GENERIC, true));
    }
}