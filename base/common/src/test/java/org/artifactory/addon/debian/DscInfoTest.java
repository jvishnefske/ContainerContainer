package org.artifactory.addon.debian;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;

/**
 * @author Priyanshu Gupta
 */
public class DscInfoTest {

    @Test
    public void testEmptyDscInfo() {
        DscInfo dscInfo = new DscInfo();

        assertNull(dscInfo.getPackageInfo());
        assertNull(dscInfo.getAssociatedFilesAndChecksums());
        assertNull(dscInfo.getAdditionalMetadataLinks());
        assertNull(dscInfo.getPackageLists());
        assertNull(dscInfo.getDepends());
        assertNull(dscInfo.getConflicts());
    }

    @Test
    public void testDscInfoSettersAndGetters() {
        DscInfo.PackageInfo packageInfo = new DscInfo.PackageInfo();
        List<DscInfo.FileChecksumInfo> filesInfo = Arrays.asList(new DscInfo.FileChecksumInfo());
        DscInfo.AdditionalMetadata additionalMetadata = new DscInfo.AdditionalMetadata();
        List<DscInfo.PackageListEntry> packageLists = Arrays.asList(new DscInfo.PackageListEntry());
        List<String> depends = Arrays.asList("dep1", "dep2");
        List<String> conflicts = Arrays.asList("conflict1");

        DscInfo dscInfo = new DscInfo();
        dscInfo.setPackageInfo(packageInfo);
        dscInfo.setAssociatedFilesAndChecksums(filesInfo);
        dscInfo.setAdditionalMetadataLinks(additionalMetadata);
        dscInfo.setPackageLists(packageLists);
        dscInfo.setDepends(depends);
        dscInfo.setConflicts(conflicts);

        assertEquals(packageInfo, dscInfo.getPackageInfo());
        assertEquals(filesInfo, dscInfo.getAssociatedFilesAndChecksums());
        assertEquals(additionalMetadata, dscInfo.getAdditionalMetadataLinks());
        assertEquals(packageLists, dscInfo.getPackageLists());
        assertEquals(depends, dscInfo.getDepends());
        assertEquals(conflicts, dscInfo.getConflicts());
    }

    @Test
    public void testPackageInfoSettersAndGetters() {
        String source = "test-source";
        String version = "1.0.0";
        String architecture = "amd64";
        String format = "3.0 (quilt)";
        List<String> binary = Arrays.asList("bin1", "bin2");
        String maintainer = "Test Maintainer <test@example.com>";
        String uploaders = "Other Uploader <other@example.com>";

        DscInfo.PackageInfo packageInfo = new DscInfo.PackageInfo();
        packageInfo.setSource(source);
        packageInfo.setVersion(version);
        packageInfo.setArchitecture(architecture);
        packageInfo.setFormat(format);
        packageInfo.setBinary(binary);
        packageInfo.setMaintainer(maintainer);
        packageInfo.setUploaders(uploaders);

        assertEquals(source, packageInfo.getSource());
        assertEquals(version, packageInfo.getVersion());
        assertEquals(architecture, packageInfo.getArchitecture());
        assertEquals(format, packageInfo.getFormat());
        assertEquals(binary, packageInfo.getBinary());
        assertEquals(maintainer, packageInfo.getMaintainer());
        assertEquals(uploaders, packageInfo.getUploaders());
    }

    @Test
    public void testFileChecksumInfoSettersAndGetters() {
        String fileName = "test.tar.gz";
        String checksumsSha256 = "a1b2c3d4e5f6";
        String checksumsSha1 = "a1b2c3";
        String size = "12345";

        DscInfo.FileChecksumInfo fileInfo = new DscInfo.FileChecksumInfo();
        fileInfo.setFileName(fileName);
        fileInfo.setChecksumsSha256(checksumsSha256);
        fileInfo.setChecksumsSha1(checksumsSha1);
        fileInfo.setSize(size);

        assertEquals(fileName, fileInfo.getFileName());
        assertEquals(checksumsSha256, fileInfo.getChecksumsSha256());
        assertEquals(checksumsSha1, fileInfo.getChecksumsSha1());
        assertEquals(size, fileInfo.getSize());
    }

    @Test
    public void testAdditionalMetadataSettersAndGetters() {
        String homepage = "https://example.com";
        String standardsVersion = "4.5.1";
        String vcsBrowser = "https://github.com/example/repo";
        String vcsGit = "https://github.com/example/repo.git";

        DscInfo.AdditionalMetadata metadata = new DscInfo.AdditionalMetadata();
        metadata.setHomepage(homepage);
        metadata.setStandardsVersion(standardsVersion);
        metadata.setVcsBrowser(vcsBrowser);
        metadata.setVcsGit(vcsGit);

        assertEquals(homepage, metadata.getHomepage());
        assertEquals(standardsVersion, metadata.getStandardsVersion());
        assertEquals(vcsBrowser, metadata.getVcsBrowser());
        assertEquals(vcsGit, metadata.getVcsGit());
    }

    @Test
    public void testPackageListEntrySettersAndGetters() {
        String packageName = "test-package";
        String type = "deb";
        String section = "main";
        String priority = "optional";
        String architecture = "all";

        DscInfo.PackageListEntry entry = new DscInfo.PackageListEntry();
        entry.setPackageName(packageName);
        entry.setType(type);
        entry.setSection(section);
        entry.setPriority(priority);
        entry.setArchitecture(architecture);

        assertEquals(packageName, entry.getPackageName());
        assertEquals(type, entry.getType());
        assertEquals(section, entry.getSection());
        assertEquals(priority, entry.getPriority());
        assertEquals(architecture, entry.getArchitecture());
    }
}
