package org.artifactory.api.request;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.factory.InfoFactory;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

public class ArtifactoryRequestBaseTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private InfoFactory infoFactory;

    private MockedStatic<InfoFactoryHolder> infoFactoryHolderStatic;
    private ArtifactoryRequestBase artifactoryRequestBase;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        ArtifactoryRequestBase.resetArchiveDotslashSplitPatternCache();
        mocks = MockitoAnnotations.openMocks(this);
        infoFactoryHolderStatic = mockStatic(InfoFactoryHolder.class);
        infoFactoryHolderStatic.when(InfoFactoryHolder::get).thenReturn(infoFactory);
        artifactoryRequestBase = spy(ArtifactoryRequestBase.class);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (infoFactoryHolderStatic != null) {
            infoFactoryHolderStatic.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    private static Stream<Arguments> removeDotSegmentsTestCases() {
        return Stream.of(
            arguments("", "", "Empty path"),
            arguments("/", "/", "Root path"),
            arguments("./", "/", "Equals ./"),
            arguments("/path/to/file.txt", "/path/to/file.txt", "Normal path"),
            arguments("/path/./to/file.txt", "/path/to/file.txt", "Remove single /./"),
            arguments("/path/./to/./file.txt", "/path/to/file.txt", "Remove multiple /./ segments"),
            arguments("/path/to/file.txt/.", "/path/to/file.txt/", "Remove trailing /."),
            arguments("/path/to/archive.jar", "/path/to/archive.jar", "Archive with zero dot slash"),
            arguments("/path/archive.zip!/./internal/file.txt", "/path/archive.zip!/./internal/file.txt",
                    "Archive zip single /./"),
            arguments("/path/to/archive.tar.gz!/./resource", "/path/to/archive.tar.gz!/./resource",
                    "Archive tar.gz single /./"),
            arguments("/archive.jar!/./path/.", "/archive.jar!/./path/",
                    "Archive path trailing /. after preservation cleaned"),
            arguments("/path/./archive.zip!/./internal/file.txt", "/path/archive.zip!/./internal/file.txt",
                    "Cleanup before part then preserve"),
            arguments("/archive.war!/./path/./to/file", "/archive.war!/./path/to/file",
                    "Archive with multiple /./ cleaned"),
            arguments("file.zip!/././deep", "file.zip!/./deep", "Archive with consecutive /./ cleaned"),
            arguments("/archive.jar!/./path/.", "/archive.jar!/./path/", "Remove trailing /. in archive path"),
            arguments("/path/../to/file.txt", "/path/../to/file.txt", "Path with ../ (not handled by this method)"),
            arguments("file.zip/./", "file.zip/", "Archive with no remainder - /./ removed"),
            arguments("file.notarchive/./x", "file.notarchive/x", "Non-archive extension fallback")
        );
    }

    private static Stream<Arguments> removeDotSegmentsTestCasesWithDisableFlag() {
        return Stream.of(
            arguments("", "", "Empty path"),
            arguments("/", "/", "Root path"),
            arguments("./", "/", "Equals ./"),
            arguments("/path/to/file.txt", "/path/to/file.txt", "Normal path"),
            arguments("/path/./to/file.txt", "/path/to/file.txt", "Remove single /./"),
            arguments("/path/./to/./file.txt", "/path/to/file.txt", "Remove multiple /./ segments"),
            arguments("/path/to/file.txt/.", "/path/to/file.txt/", "Remove trailing /."),
            arguments("/path/to/archive.jar", "/path/to/archive.jar", "Archive with zero dot slash"),
            arguments("/path/archive.zip!/./internal/file.txt", "/path/archive.zip!/internal/file.txt",
                    "Archive zip single /./"),
            arguments("/path/to/archive.tar.gz!/./resource", "/path/to/archive.tar.gz!/resource",
                    "Archive tar.gz single /./"),
            arguments("/archive.war!/./path/./to/file", "/archive.war!/path/to/file",
                    "Archive with multiple /./ cleaned"),
            arguments("file.zip!/././deep", "file.zip!/./deep", "Archive with consecutive /./ cleaned")
        );
    }

    @ParameterizedTest
    @MethodSource("removeDotSegmentsTestCases")
    public void testRemoveDotSegments(final String inputPath,
                                      final String expectedOutput,
                                      final String description) {
        assertEquals(artifactoryRequestBase.removeDotSegments(inputPath), expectedOutput, description);
    }

    @ParameterizedTest
    @MethodSource("removeDotSegmentsTestCasesWithDisableFlag")
    public void testRemoveDotSegmentsWithDisableFlag(final String inputPath,
                                                     final String expectedOutput,
                                                     final String description) {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.strictArchiveDotSlash.getPropertyName(), "false");

        assertEquals(artifactoryRequestBase.removeDotSegments(inputPath), expectedOutput, description);
    }

    @Test
    public void testGetArchiveDotslashSplitPatternWithNullMimeTypes() {
        getOrCreateArtifactoryHomeStub().setMimeTypes(null);
        ArtifactoryRequestBase.resetArchiveDotslashSplitPatternCache();

        assertEquals("^$", ArtifactoryRequestBase.getArchiveDotslashSplitPattern().pattern(),
                "Should fall back to default no-match pattern when extensions are empty");
    }

    @Test
    public void testArchiveDotslashSplitPatternCompiledOnce() {
        Pattern patternAfterFirstRequest = ArtifactoryRequestBase.getArchiveDotslashSplitPattern();
        Pattern patternAfterSecondRequest = ArtifactoryRequestBase.getArchiveDotslashSplitPattern();
        assertSame(patternAfterFirstRequest, patternAfterSecondRequest,
                "Archive dot-slash split pattern must be compiled once and shared across request instances");
    }
}