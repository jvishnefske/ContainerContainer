package org.artifactory.sensitive.fields;

import jakarta.annotation.Nullable;
import org.artifactory.api.config.DynamicSensitiveValueHandler;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Synthetic {@link DynamicSensitiveValueHandler} for a list of {@link TestSensitiveHeader} used by
 * {@link SensitiveDiffUtilTest}. Reports the {@code value} leaf of each sensitive header as sensitive.
 */
class TestSensitiveHeaderListHandler implements DynamicSensitiveValueHandler<List<TestSensitiveHeader>> {

    @Override
    public void validate(@Nullable List<TestSensitiveHeader> value) {
    }

    @Override
    public void encrypt(@Nullable List<TestSensitiveHeader> value) {
    }

    @Override
    public void decrypt(@Nullable List<TestSensitiveHeader> value) {
    }

    @Override
    public List<TestSensitiveHeader> mask(@Nullable List<TestSensitiveHeader> value) {
        if (value == null) {
            return null;
        }
        return value.stream()
                .map(TestSensitiveHeaderListHandler::masked)
                .toList();
    }

    private static TestSensitiveHeader masked(TestSensitiveHeader header) {
        if (header == null) {
            return null;
        }
        if (header.isSensitive() && header.getValue() != null && !header.getValue().isBlank()) {
            return new TestSensitiveHeader(header.getName(), "***", true);
        }
        return new TestSensitiveHeader(header.getName(), header.getValue(), header.isSensitive());
    }

    @Override
    public Set<String> sensitiveLeafPaths(@Nullable List<TestSensitiveHeader> value, String pathDelimiter) {
        if (value == null) {
            return Set.of();
        }
        return value.stream()
                .filter(header -> header != null && header.isSensitive())
                .map(header -> header.getName() + pathDelimiter + "value")
                .collect(Collectors.toSet());
    }
}
