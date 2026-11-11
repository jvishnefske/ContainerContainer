package org.artifactory.metadata.service.indexer;

import com.jfrog.artifactory.packageindexed.CreatePackageIndexEventOuterClass;
import com.jfrog.artifactory.packageindexed.FileInfoOuterClass;
import com.jfrog.artifactory.packageindexed.PackageIndexEventOuterClass.PackageIndexEvent;
import com.jfrog.artifactory.packageindexed.QualifierOuterClass;
import com.jfrog.artifactory.packageindexed.UserPropertyOuterClass;
import org.jfrog.metadata.client.model.MetadataFile;
import org.jfrog.metadata.client.model.MetadataLicense;
import org.jfrog.metadata.client.model.MetadataPackage;
import org.jfrog.metadata.client.model.MetadataQualifierEntity;
import org.jfrog.metadata.client.model.MetadataUserProperty;
import org.jfrog.metadata.client.model.MetadataVersion;
import org.jfrog.metadata.client.model.MetadataVersionRepo;
import org.jfrog.metadata.client.model.MetadataVersionTagEntity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackageIndexEventConverterTest {

    static Stream<String> scenarios() {
        return Stream.of("emptyVersions", "fullVersion", "largeFileLength");
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void convert_scenarios(String scenario) {
        switch (scenario) {
            case "emptyVersions":
                testEmptyVersions();
                break;
            case "fullVersion":
                testFullVersion();
                break;
            case "largeFileLength":
                testLargeFileLength();
                break;
            default:
                fail("Unknown scenario: " + scenario);
        }
    }

    private void testLargeFileLength() {
        // 3 GB > Integer.MAX_VALUE; old (int) cast produced -1_073_741_824
        long largeLength = 3_221_225_472L;

        MetadataPackage pkg = mock(MetadataPackage.class);
        MetadataVersion version = mock(MetadataVersion.class);
        MetadataVersionRepo repo = mock(MetadataVersionRepo.class);
        MetadataFile metaFile = mock(MetadataFile.class);

        when(pkg.getName()).thenReturn("large-pkg");
        when(pkg.getPkgid()).thenReturn("maven://large-pkg");
        when(pkg.getPackageType()).thenReturn("maven");
        when(pkg.getRating()).thenReturn(0);
        LinkedList<MetadataVersion> versions = new LinkedList<>();
        versions.add(version);
        when(pkg.getVersions()).thenReturn(versions);
        when(version.getName()).thenReturn("1.0");
        when(version.getRepos()).thenReturn(new LinkedList<>(List.of(repo)));
        when(version.getFiles()).thenReturn(List.of(metaFile));
        when(repo.getName()).thenReturn("libs-release");
        when(metaFile.getName()).thenReturn("large-artifact.tar.gz");
        when(metaFile.getLength()).thenReturn(largeLength);

        PackageIndexEvent event = PackageIndexEventConverter.convert(pkg, Collections.emptyMap());

        FileInfoOuterClass.FileInfo fi = event.getCreatePackageIndexEvent().getFiles(0);
        assertEquals(largeLength, fi.getLength(), "length must be preserved as int64, not truncated to int32");
        assertTrue(fi.getLength() > 0, "length must be positive for a >2GB file");
    }

    private void testEmptyVersions() {
        // Mock MetadataPackage with no versions and a modified timestamp
        MetadataPackage pkg = mock(MetadataPackage.class);
        when(pkg.getPkgid()).thenReturn("docker://alpine");
        when(pkg.getVersions()).thenReturn(null);
        // model returns modified as a string
        when(pkg.getModified()).thenReturn(String.valueOf(12345L));

        Map<String, String> appProps = Collections.emptyMap();

        PackageIndexEvent event = PackageIndexEventConverter.convert(pkg, appProps);
        assertNotNull(event);
        assertTrue(event.hasCreatePackageIndexEvent());
        CreatePackageIndexEventOuterClass.CreatePackageIndexEvent created = event.getCreatePackageIndexEvent();
        // created.modified is a string in the protobuf
        assertEquals(String.valueOf(12345L), created.getModified());
    }

    private void testFullVersion() {
        // Mock metadata package and version
        MetadataPackage pkg = mock(MetadataPackage.class);
        MetadataVersion version = mock(MetadataVersion.class);
        MetadataVersionRepo repo = mock(MetadataVersionRepo.class);

        when(pkg.getName()).thenReturn("mypkg");
        when(pkg.getDescription()).thenReturn("A useful package");
        when(pkg.getPackageType()).thenReturn("npm");
        when(pkg.getRating()).thenReturn(5);
        when(pkg.getPkgid()).thenReturn("docker://alpine");

        // License-like anonymous object
        MetadataLicense license = mock(MetadataLicense.class);
        when(license.getName()).thenReturn("MIT");
        when(pkg.getLicenses()).thenReturn(List.of(license));
        when(pkg.getIssuesUrl()).thenReturn("http://issues");

        // Versions list
        java.util.LinkedList<MetadataVersion> versionsList = new java.util.LinkedList<>();
        versionsList.add(version);
        when(pkg.getVersions()).thenReturn(versionsList);

        when(version.getName()).thenReturn("1.2.3");
        // model returns modified as a string
        when(version.getModified()).thenReturn(String.valueOf(99999L));

        MetadataQualifierEntity qualifierEntity = mock(MetadataQualifierEntity.class);
        when(qualifierEntity.getName()).thenReturn("os");
        when(qualifierEntity.getValue()).thenReturn("linux");
        when(version.getQualifiers()).thenReturn(List.of(qualifierEntity));

        // Tags: anonymous object with getValue
        MetadataVersionTagEntity tagEntity = mock(MetadataVersionTagEntity.class);
        when(tagEntity.getValue()).thenReturn("stable");
        when(version.getTags()).thenReturn(Set.of(tagEntity));

        // User properties
        MetadataUserProperty metaUserProperty = mock(MetadataUserProperty.class);
        when(metaUserProperty.getName()).thenReturn("team");
        when(metaUserProperty.getValues()).thenReturn(Set.of("infra"));
        when(version.getUserProperties()).thenReturn(List.of(metaUserProperty));

        // Repo
        java.util.LinkedList<MetadataVersionRepo> repos = new LinkedList<>();
        repos.add(repo);
        when(version.getRepos()).thenReturn(repos);
        when(repo.getName()).thenReturn("central");
        when(repo.getType()).thenReturn("LOCAL");

        // Files
        MetadataFile metaFile = mock(MetadataFile.class);
        when(metaFile.getName()).thenReturn("artifact.jar");
        when(metaFile.getLength()).thenReturn(42L);
        when(metaFile.getSha1()).thenReturn("sha1Val");
        when(metaFile.getSha256()).thenReturn("sha256Val");
        when(metaFile.getMd5()).thenReturn("md5val");
        when(version.getFiles()).thenReturn(List.of(metaFile));

        Map<String, String> appProps = new HashMap<>();
        appProps.put("internal.key", "value1");

        PackageIndexEvent event = PackageIndexEventConverter.convert(pkg, appProps);
        assertNotNull(event);
        assertTrue(event.hasCreatePackageIndexEvent());
        CreatePackageIndexEventOuterClass.CreatePackageIndexEvent created = event.getCreatePackageIndexEvent();

        // Package-level assertions
        assertEquals("mypkg", created.getName());
        assertEquals("A useful package", created.getDescription());
        assertEquals("npm", event.getRepoPackageType());
        assertEquals(5, created.getRating());
        assertEquals(1, created.getLicensesCount());
        assertEquals("MIT", created.getLicenses(0));
        assertEquals("http://issues", created.getIssuesUrl());

        // Application properties
        assertEquals(1, created.getInternalPropertiesCount());
        assertEquals("internal.key", created.getInternalProperties(0).getName());
        assertEquals("value1", created.getInternalProperties(0).getValues(0));

        // Version-level
        assertEquals("1.2.3", created.getVersion());
        // created.modified is a string in the protobuf
        assertEquals(String.valueOf(99999L), created.getModified());

        // Qualifiers
        assertEquals(1, created.getQualifiersCount());
        QualifierOuterClass.Qualifier q = created.getQualifiers(0);
        assertEquals("os", q.getName());
        assertEquals("linux", q.getValue());

        // Tags
        assertEquals(1, created.getTagsCount());
        assertEquals("stable", created.getTags(0));

        // User properties
        assertEquals(1, created.getUserPropertiesCount());
        UserPropertyOuterClass.UserProperty up = created.getUserProperties(0);
        assertEquals("team", up.getName());
        assertEquals("infra", up.getValues(0));

        // Repo info
        assertEquals("central", event.getRepoName());
        assertEquals("LOCAL", created.getRepoType());

        // Files
        assertEquals(1, created.getFilesCount());
        FileInfoOuterClass.FileInfo fi = created.getFiles(0);
        assertEquals("artifact.jar", fi.getName());
        assertEquals(42, fi.getLength());
        assertEquals("sha1Val", fi.getSha1());
        assertEquals("sha256Val", fi.getSha256());
        assertEquals("md5val", fi.getMd5());
    }
}