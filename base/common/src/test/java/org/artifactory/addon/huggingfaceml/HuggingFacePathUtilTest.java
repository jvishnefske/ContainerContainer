package org.artifactory.addon.huggingfaceml;

import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.of;

/**
 * @author Volodymyr Shulga
 */
class HuggingFacePathUtilTest {

    @ParameterizedTest
    @MethodSource("validPaths")
    void validPathsTest(String path) {
        assertTrue(HuggingFacePathUtil.isValidPath(path));
    }

    @ParameterizedTest
    @MethodSource("invalidPaths")
    void invalidPathsTest(String path) {
        assertFalse(HuggingFacePathUtil.isValidPath(path));
    }

    @ParameterizedTest
    @MethodSource("validPathsWithExpected")
    void convertLogicWorksAsExpectedForValidPaths(String path, String expected) {
        String newPath = HuggingFacePathUtil.convertPathToNewLayout(path);
        assertEquals(expected, newPath);
    }

    @ParameterizedTest
    @MethodSource("invalidPaths")
    void convertPathReturnsNullsForInvalidPaths(String path) {
        assertNull(HuggingFacePathUtil.convertPathToNewLayout(path));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("entityTypePaths")
    void checkEntityTypeLogic(String path,
            EntityType expected) {
        assertEquals(expected, HuggingFacePathUtil.getEntityType(path));
    }

    @ParameterizedTest
    @MethodSource("fileTypePaths")
    void getFileType(String path, FileType expected) {
        assertEquals(expected, HuggingFacePathUtil.getFileType(path));
    }

    @ParameterizedTest
    @MethodSource("validOldLayoutPaths")
    void isPathMatchingOldLayoutReturnsTrueForValidPaths(String path, EntityType entityType,
            FileType fileType) {
        assertTrue(HuggingFacePathUtil.isPathMatchingOldLayout(path, entityType, fileType));
    }

    @ParameterizedTest
    @MethodSource("invalidOldLayoutPaths")
    void isPathMatchingOldLayoutReturnsFalseForInvalidPaths(String path, EntityType entityType, FileType fileType) {
        assertFalse(HuggingFacePathUtil.isPathMatchingOldLayout(path, entityType, fileType));
    }

    @ParameterizedTest
    @MethodSource("tokensAndExpectedResults")
    void getRevisionWithTimestampForLeadFile(String[] tokens, String expected) {
        String result = HuggingFacePathUtil.getRevisionWithTimestampForLeadFile(tokens);
        assertEquals(expected, result);
    }

    static Stream<Arguments> tokensAndExpectedResults() {
        return Stream.of(
                Arguments.of(new String[] {"models", "name", "revision", "2024-09-12T13:25:52.272Z", "file.txt"},
                        "revision_2024-09-12T13:25:52.272Z"),
                Arguments.of(new String[] {"models", "name"}, ""),
                Arguments.of(new String[] {}, "")
        );
    }

    static Stream<Arguments> validOldLayoutPaths() {
        return Stream.of(
                of("models/name/revision/2024-09-12T13:25:52.272Z/file.txt", EntityType.MODEL,
                        FileType.GENERIC),
                of("datasets/name/revision/2024-09-12T13:25:52.272Z/.jfrog_huggingface_dataset_info.json",
                        EntityType.DATASET, FileType.LEAD_FILE),
                of("models/name/revision/.latest_huggingface_model_info.json",
                        EntityType.MODEL, FileType.LATEST_LEAD_FILE)
        );
    }

    static Stream<Arguments> invalidOldLayoutPaths() {
        return Stream.of(
                of("invalid/path/structure", EntityType.UNKNOWN,
                        FileType.GENERIC),
                of("datasets/name/revision/file.txt", EntityType.DATASET,
                        FileType.GENERIC),
                of("datasets/name/.latest_huggingface_dataset_info.json",
                        EntityType.DATASET, FileType.LATEST_LEAD_FILE)
        );
    }

    static Stream<Arguments> validPaths() {
        return Stream.of(
                of("models/name/revision/.latest_huggingface_model_info.json"),
                of("datasets/name/revision/.latest_huggingface_dataset_info.json"),
                of("models/name/revision/2024-09-12T13:25:52.272Z/.jfrog_huggingface_model_info.json"),
                of("datasets/name/revision/2024-09-12T13:25:52.272Z/.jfrog_huggingface_dataset_info.json"),
                of("models/org/name/revision/2024-09-12T13:25:52.272Z/inner/file.txt"),
                of("models/org/name/revision/2024-09-12T13:25:52.272Z/file.txt"),
                of("datasets/org/name/revision/2024-09-12T13:25:52.272Z/file.txt"),
                of("datasets/org/name/revision/2024-09-12T13:25:52.272Z/inner/file.txt")
        );
    }

    static Stream<Arguments> invalidPaths() {
        return Stream.of(
                of("invalid/path/structure"),
                of("dataset/org/name/revision/2024-09-12T13:25:52.272Z/inner/file.txt"),
                of("datasets/org/name/revision/inner/file.txt"),
                of("org/name/revision/2024-09-12T13:25:52.272Z/inner/file.txt"),
                of("datasets/revision/2024-09-12T13:25:52.272Z/file.txt"),
                of("datasets/org/name/revision/file.txt"),
                of("models/org/name/revision/.jfrog_huggingface_model_info.json"),
                of("unknown/path/structure")
        );
    }

    static Stream<Arguments> validPathsWithExpected() {
        return Stream.of(
                of("models/name/revision/2024-09-12T13:25:52.272Z/.jfrog_huggingface_model_info.json",
                        "models/name/revision_2024-09-12T13:25:52.272Z/.jfrog_huggingface_model_info.json"),
                of("models/name/revision/.latest_huggingface_model_info.json",
                        ".jfrog/ml_layout/latest_lead_files/models/name/revision/.latest_huggingface_model_info.json"),
                of("datasets/org/name/revision/2024-09-12T13:25:52.272Z/inner/file.txt",
                        "datasets/org/name/revision_2024-09-12T13:25:52.272Z/dataset/inner/file.txt"),
                of("datasets/org/with/slash/name/revision/2024-09-12T13:25:52.272Z/inner/file.txt",
                        "datasets/org/with/slash/name/revision_2024-09-12T13:25:52.272Z/dataset/inner/file.txt"),
                of("models/org/name/revision/2024-09-12T13:25:52.272Z/file.txt",
                        "models/org/name/revision_2024-09-12T13:25:52.272Z/model/file.txt")
        );
    }

    static Stream<Arguments> entityTypePaths() {
        return Stream.of(
                of("models/name/revision/file.txt", EntityType.MODEL),
                of("datasets/org/name/revision/file.txt", EntityType.DATASET),
                of("dataset/org/name/revision/file.txt", EntityType.UNKNOWN),
                of("model/org/name/revision/file.txt", EntityType.UNKNOWN),
                of("unknown/path/structure", EntityType.UNKNOWN)
        );
    }

    static Stream<Arguments> fileTypePaths() {
        return Stream.of(
                of("models/name/revision/.jfrog_huggingface_model_info.json",
                        FileType.LEAD_FILE),
                of("datasets/org/name/revision/.latest_huggingface_dataset_info.json",
                        FileType.LATEST_LEAD_FILE),
                of("models/name/revision/file.txt", FileType.GENERIC)
        );
    }
}