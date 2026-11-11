package org.artifactory.mime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Priyanshu Gupta
 */
public class DebianNamingTest {
    @ParameterizedTest
    @ValueSource(strings = {DebianNaming.RELEASE, DebianNaming.INRELEASE, "release", "inRELease"})
    void testIsReleaseIndexReturnsTrue(String fileName) {
        assertTrue(DebianNaming.isReleaseIndex(fileName), "Should be a release index file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Release.gpg", "Packages", "Releas", ""})
    void testIsReleaseIndexReturnsFalse(String fileName) {
        assertFalse(DebianNaming.isReleaseIndex(fileName), "Should not be a release index file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            DebianNaming.PACKAGES, DebianNaming.PACKAGES_GZ, DebianNaming.PACKAGES_BZ2,
            DebianNaming.PACKAGES_XZ, DebianNaming.PACKAGES_LZMA,
            "packages", "packages.gz", "PACKAGES.Bz2"
    })
    void testIsPackagesIndexReturnsTrue(String fileName) {
        assertTrue(DebianNaming.isPackagesIndex(fileName), "Should be a packages index file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sources", "Packages.zip", "Package", ""})
    void testIsPackagesIndexReturnsFalse(String fileName) {
        assertFalse(DebianNaming.isPackagesIndex(fileName), "Should not be a packages index file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            DebianNaming.SOURCES, DebianNaming.SOURCES_GZ, DebianNaming.SOURCES_BZ2, DebianNaming.SOURCES_XZ,
            "sources", "SOURCES.gz", "sources.bz2"
    })
    void testIsSourcesIndex_true(String fileName) {
        assertTrue(DebianNaming.isSourcesIndex(fileName), "Should be a sources index file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Packages", "Sources.zip", "Source", ""})
    void testIsSourcesIndexReturnsFalse(String fileName) {
        assertFalse(DebianNaming.isSourcesIndex(fileName), "Should not be a sources index file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Translation-en", "translation-fr_FR", "Translation-DE"})
    void testIsTranslationIndexReturnsTrue(String fileName) {
        assertTrue(DebianNaming.isTranslationIndex(fileName), "Should be a translation file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Translatio-en", "MyTranslation-en", "translation", "en-Translation", ""})
    void testIsTranslationIndexReturnsFalse(String fileName) {
        assertFalse(DebianNaming.isTranslationIndex(fileName), "Should not be a translation file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {DebianNaming.RELEASE_GPG, "release.gpg"})
    void testIsSigningFileReturnsTrue(String fileName) {
        assertTrue(DebianNaming.isSigningFile(fileName), "Should be a signing file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Release", "Release.gp", "inrelease.gpg", ""})
    void testIsSigningFileReturnsFalse(String fileName) {
        assertFalse(DebianNaming.isSigningFile(fileName), "Should not be a signing file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {DebianNaming.RELEASE_GPG, "release.gpg"})
    void testIsDebianTempFilesReturnsTrue(String fileName) {
        assertTrue(DebianNaming.isDebianTempFiles(fileName), "Should be a Debian temp file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Release", "Packages", "inrelease.gpg", ""})
    void testIsDebianTempFilesReturnsFalse(String fileName) {
        assertFalse(DebianNaming.isDebianTempFiles(fileName), "Should not be a Debian temp file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {DebianNaming.INRELEASE, "inrelease"})
    void testIsInReleaseReturnsTrue(String fileName) {
        assertTrue(DebianNaming.isInRelease(fileName), "Should be an InRelease file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Release", "inrelease.gpg", "InRelease.gz", ""})
    void testIsInReleaseReturnsFalse(String fileName) {
        assertFalse(DebianNaming.isInRelease(fileName), "Should not be an InRelease file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Contents-amd64", "contents-i386", "Contents-all.gz",
            "contents-armhf.bz2", "CONTENTS-s390x.xz"
    })
    void testIsContentIndexReturnsTrue(String fileName) {
        assertTrue(DebianNaming.isContentIndex(fileName), "Should be a content index file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Contents", "Content-amd64", "contents.gz", "MyContents-amd64", ""})
    void testIsContentIndexReturnsFalse(String fileName) {
        assertFalse(DebianNaming.isContentIndex(fileName), "Should not be a content index file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {DebianNaming.SHA256SUMS, DebianNaming.MD5SUMS, "sha256sums", "md5sums"})
    void testIsSumsFileReturnsTrue(String fileName) {
        assertTrue(DebianNaming.isSumsFile(fileName), "Should be a sums file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SHA1SUMS", "SUMS", "sha256sums.gpg", ""})
    void testIsSumsFileReturnsFalse(String fileName) {
        assertFalse(DebianNaming.isSumsFile(fileName), "Should not be a sums file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            DebianNaming.RELEASE, DebianNaming.INRELEASE,
            DebianNaming.PACKAGES, DebianNaming.PACKAGES_GZ,
            "Contents-amd64", "contents-all.gz"
    })
    void testIsIndexFile_true(String fileName) {
        assertTrue(DebianNaming.isIndexFile(fileName), "Should be an index file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sources", "Release.gpg", "Translation-en", "SHA256SUMS", "random-file", ""})
    void testIsIndexFileReturnsFalse(String fileName) {
        assertFalse(DebianNaming.isIndexFile(fileName), "Should not be an index file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            DebianNaming.RELEASE, DebianNaming.INRELEASE,
            DebianNaming.PACKAGES, DebianNaming.PACKAGES_XZ
    })
    void testIsSupportedIndexReturnsTrue(String fileName) {
        assertTrue(DebianNaming.isSupportedIndex(fileName), "Should be a supported index file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Contents-amd64", "Sources.bz2", "Release.gpg", "Translation-de", ""})
    void testIsSupportedIndexReturnsFalse(String fileName) {
        assertFalse(DebianNaming.isSupportedIndex(fileName), "Should not be a supported index file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Translation-en",
            DebianNaming.SOURCES, DebianNaming.SOURCES_GZ,
            DebianNaming.RELEASE, DebianNaming.INRELEASE,
            DebianNaming.RELEASE_GPG,
            DebianNaming.PACKAGES, DebianNaming.PACKAGES_LZMA,
            "Contents-i386", "contents-all.xz",
            DebianNaming.SHA256SUMS, DebianNaming.MD5SUMS
    })
    void testIsExpirableReturnsTrue(String fileName) {
        assertTrue(DebianNaming.isExpirable(fileName), "Should be an expirable file: " + fileName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"some-other-file.deb", "README.txt", "my-package_1.0_all.deb", ""})
    void testIsExpirableReturnsFalse(String fileName) {
        assertFalse(DebianNaming.isExpirable(fileName), "Should not be an expirable file: " + fileName);
    }
}
