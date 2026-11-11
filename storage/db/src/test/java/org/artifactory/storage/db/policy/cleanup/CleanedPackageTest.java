package org.artifactory.storage.db.policy.cleanup;

import org.artifactory.policy.NodeMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CleanedPackageTest {

    @Test
    void getCreatedDateWithMetadata() {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                "path", "name", metadata);

        assertEquals(1000L, pkg.getCreatedDate());
    }

    @Test
    void getCreatedDateWithoutMetadata() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                "path", "name", null);

        assertNull(pkg.getCreatedDate());
    }

    @Test
    void getModifiedDateWithMetadata() {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                "path", "name", metadata);

        assertEquals(2000L, pkg.getModifiedDate());
    }

    @Test
    void getModifiedDateWithoutMetadata() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                "path", "name", null);

        assertNull(pkg.getModifiedDate());
    }

    @Test
    void getLastDownloadedDateWithMetadata() {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                "path", "name", metadata);

        assertEquals(3000L, pkg.getLastDownloadedDate());
    }

    @Test
    void getLastDownloadedDateWithoutMetadata() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                "path", "name", null);

        assertNull(pkg.getLastDownloadedDate());
    }

    @Test
    void getLastDownloadedDateWhenNullInMetadata() {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, null);
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                "path", "name", metadata);

        assertNull(pkg.getLastDownloadedDate());
    }

    @Test
    void getPackagePathWithAllComponents() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                "org/example", "artifact.jar", null);

        assertEquals("repo/org/example/artifact.jar", pkg.getPackagePath());
    }

    @Test
    void getPackagePathWithRepoAndNodeNameOnly() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                null, "artifact.jar", null);

        assertEquals("repo/artifact.jar", pkg.getPackagePath());
    }

    @Test
    void getPackagePathWithRepoAndNodePathOnly() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                "org/example", null, null);

        assertEquals("repo/org/example", pkg.getPackagePath());
    }

    @Test
    void getPackagePathWithRepoOnly() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                null, null, null);

        assertEquals("repo", pkg.getPackagePath());
    }

    @Test
    void getPackagePathWithDotNodePath() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                ".", "artifact.jar", null);

        assertEquals("repo/artifact.jar", pkg.getPackagePath());
    }

    @Test
    void getPackagePathWithEmptyNodePath() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                "", "artifact.jar", null);

        assertEquals("repo/artifact.jar", pkg.getPackagePath());
    }

    @Test
    void getDataWithMetadataIncludesAllFields() {
        NodeMetadata metadata = new NodeMetadata(1609502400000L, 1609588800000L, 1609675200000L);
        CleanedPackage pkg = new CleanedPackage("test-pkg", "1.0.0", "test-repo", 1048576L, 0, "LOCAL", null, 
                "org/example", "test-1.0.0.jar", metadata);

        String[] data = pkg.getDataWithMetadata();

        assertEquals(11, data.length);
        assertEquals("test-pkg", data[0]);
        assertEquals("1.0.0", data[1]);
        assertEquals("1", data[2]);
        assertEquals("test-repo", data[3]);
        assertEquals("LOCAL", data[4]);
        assertEquals("DELETED", data[5]);
        assertEquals("Package was successfully deleted", data[6]);
        assertEquals("test-repo/org/example/test-1.0.0.jar", data[7]);
        assertEquals("2021-01-01", data[8].substring(0, 10));
        assertEquals("2021-01-02", data[9].substring(0, 10));
        assertEquals("2021-01-03", data[10].substring(0, 10));
    }

    @Test
    void getDataWithMetadataWithNullMetadata() {
        CleanedPackage pkg = new CleanedPackage("test-pkg", "1.0.0", "test-repo", 1048576L, 0, "LOCAL", null, 
                "org/example", "test-1.0.0.jar", null);

        String[] data = pkg.getDataWithMetadata();

        assertEquals(11, data.length);
        assertEquals("", data[8]);
        assertEquals("", data[9]);
        assertEquals("", data[10]);
    }

    @Test
    void getDataWithMetadataWithZeroTimestamps() {
        NodeMetadata metadata = new NodeMetadata(0L, 0L, 0L);
        CleanedPackage pkg = new CleanedPackage("test-pkg", "1.0.0", "test-repo", 1048576L, 0, "LOCAL", null, 
                "org/example", "test-1.0.0.jar", metadata);

        String[] data = pkg.getDataWithMetadata();

        assertEquals("", data[8]);
        assertEquals("", data[9]);
        assertEquals("", data[10]);
    }

    @Test
    void toPackageKeyInstance() {
        CleanedPackage pkg = new CleanedPackage("nginx", "1.1.1", "cool-repo", 100L, 0, "LOCAL", "message", 
                "path", "name", null);

        String key = pkg.toPackageKey();

        assertEquals("nginx|1.1.1|cool-repo|message", key);
    }

    @Test
    void toPackageKeyInstanceWithNullMessage() {
        CleanedPackage pkg = new CleanedPackage("nginx", "1.1.1", "cool-repo", 100L, 0, "LOCAL", null, 
                "path", "name", null);

        String key = pkg.toPackageKey();

        assertEquals("nginx|1.1.1|cool-repo|", key);
    }

    @Test
    void toPackageKeyStatic() {
        CleanedNode node = new CleanedNode("pkey", 1, "nginx", "1.1.1", true, "cool-repo", "path", 
                "name", 100L, "sha1", 0, "message", null);

        String key = CleanedPackage.toPackageKey(node);

        assertEquals("nginx|1.1.1|cool-repo|message", key);
    }

    @Test
    void toPackageKeyStaticWithNullMessage() {
        CleanedNode node = new CleanedNode("pkey", 1, "nginx", "1.1.1", true, "cool-repo", "path", 
                "name", 100L, "sha1", 0, null, null);

        String key = CleanedPackage.toPackageKey(node);

        assertEquals("nginx|1.1.1|cool-repo|", key);
    }

    @Test
    void constructorWithMinimalParameters() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 0);

        assertNotNull(pkg);
        assertEquals("pkg", pkg.packageName());
        assertEquals("1.0", pkg.packageVersion());
        assertEquals("repo", pkg.repo());
        assertEquals(100L, pkg.binLength());
        assertEquals(0, pkg.status());
        assertEquals("LOCAL", pkg.repoType());
        assertNull(pkg.message());
        assertNull(pkg.nodePath());
        assertNull(pkg.nodeName());
        assertNull(pkg.nodeMetadata());
    }

    @Test
    void constructorWithMessage() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 1, "test-message");

        assertNotNull(pkg);
        assertEquals("test-message", pkg.message());
        assertEquals("LOCAL", pkg.repoType());
        assertNull(pkg.nodeMetadata());
    }

    @Test
    void toBuilderCreatesModifiableCopy() {
        NodeMetadata metadata = new NodeMetadata(1000L, 2000L, 3000L);
        CleanedPackage original = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null, 
                "path", "name", metadata);

        CleanedPackage modified = original.toBuilder()
                .packageVersion("2.0")
                .build();

        assertEquals("pkg", modified.packageName());
        assertEquals("2.0", modified.packageVersion());
        assertEquals(metadata, modified.nodeMetadata());
    }

    @Test
    void getRestoreDataWithMetadataIncludesCorrectFieldCount() {
        NodeMetadata metadata = new NodeMetadata(1609502400000L, 1609588800000L, null);
        CleanedPackage pkg = new CleanedPackage("test-pkg", "1.0.0", "test-repo", 1048576L, 0, "LOCAL", null,
                "org/example", "test-1.0.0.jar", metadata);

        String[] data = pkg.getRestoreDataWithMetadata();

        assertEquals(10, data.length);
        assertEquals("test-pkg", data[0]);
        assertEquals("1.0.0", data[1]);
        assertEquals("1", data[2]);
        assertEquals("test-repo", data[3]);
        assertEquals("LOCAL", data[4]);
        assertEquals("DELETED", data[5]);
        assertEquals("Package was successfully deleted", data[6]);
        assertEquals("test-repo/org/example/test-1.0.0.jar", data[7]);
        assertEquals("2021-01-01", data[8].substring(0, 10));
        assertEquals("2021-01-02", data[9].substring(0, 10));
    }

    @Test
    void getRestoreDataWithMetadataWithNullMetadata() {
        CleanedPackage pkg = new CleanedPackage("test-pkg", "1.0.0", "test-repo", 1048576L, 0, "LOCAL", null,
                "org/example", "test-1.0.0.jar", null);

        String[] data = pkg.getRestoreDataWithMetadata();

        assertEquals(10, data.length);
        assertEquals("", data[8]);
        assertEquals("", data[9]);
    }

    @Test
    void getPackagePathWithSpecialCharacters() {
        CleanedPackage pkg = new CleanedPackage("@scope/package", "1.0.0", "npm-repo", 100L, 0, "LOCAL", null,
                "org/example/@scope", "package-1.0.0.tgz", null);

        String[] data = pkg.getDataWithMetadata();

        assertEquals("npm-repo/org/example/@scope/package-1.0.0.tgz", data[7]);
    }

    @Test
    void toBuilderModifiesMetadata() {
        NodeMetadata originalMetadata = new NodeMetadata(1000L, 2000L, 3000L);
        NodeMetadata newMetadata = new NodeMetadata(4000L, 5000L, 6000L);

        CleanedPackage original = new CleanedPackage("pkg", "1.0", "repo", 100L, 0, "LOCAL", null,
                "path", "name", originalMetadata);

        CleanedPackage modified = original.toBuilder()
                .nodeMetadata(newMetadata)
                .build();

        assertEquals(4000L, modified.getCreatedDate());
        assertEquals(5000L, modified.getModifiedDate());
        assertEquals(6000L, modified.getLastDownloadedDate());
    }

    @Test
    void updateSkippedMessageWithFederationEnumKey() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 1, "MEMBER_UNAVAILABLE");

        String msg = pkg.updateSkippedMessage();

        assertEquals("One or more federation members are unavailable.", msg);
    }

    @Test
    void updateSkippedMessageWithPackageModifiedEnumKey() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 1, "PACKAGE_MODIFIED");

        String msg = pkg.updateSkippedMessage();

        assertEquals("Package at source modified since last archive", msg);
    }

    @Test
    void updateSkippedMessageWithRestoredPackagePipeDelimited() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 1,
                "RESTORED_PACKAGE|1714296832000");

        String msg = pkg.updateSkippedMessage();

        assertEquals("Package was restored at 2024-04-28 09:33:52 +0000", msg);
    }

    @Test
    void updateSkippedMessageWithNullMessage() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 1, null);

        String msg = pkg.updateSkippedMessage();

        assertEquals("Unable to delete the package. For more details see artifactory-service.log", msg);
    }

    @Test
    void updateSkippedMessageWithRestorePackageAlreadyExists() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 1,
                "RESTORE_PACKAGE_ALREADY_EXISTS");

        String msg = pkg.updateSkippedMessage();

        assertEquals("Package restoration was skipped as the package is already available in the active instance.",
                msg);
    }

    @Test
    void updateSkippedMessageWithUnknownKeyReturnsDefault() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 1, "SOME_UNKNOWN_KEY");

        String msg = pkg.updateSkippedMessage();

        assertEquals("Unable to delete the package. For more details see artifactory-service.log", msg);
    }

    @Test
    void updateSkippedMessageRestoredPackageWithInvalidEpochPassesThrough() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 1,
                "RESTORED_PACKAGE|not-a-number");

        String msg = pkg.updateSkippedMessage();

        assertEquals("Package was restored at not-a-number", msg);
    }

    @Test
    void updateMessageForSkippedStatusDelegates() {
        CleanedPackage pkg = new CleanedPackage("pkg", "1.0", "repo", 100L, 1, "LOCAL",
                "PACKAGE_MODIFIED", null, null, null);

        String[] data = pkg.getData();

        assertEquals("SKIPPED", data[5]);
        assertEquals("Package at source modified since last archive", data[6]);
    }
}
