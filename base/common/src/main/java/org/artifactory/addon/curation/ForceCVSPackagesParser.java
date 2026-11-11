package org.artifactory.addon.curation;

import org.artifactory.common.repo.PackageType;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Parses and normalizes {@code forceCVSPackages} values from API/DB input.
 * Unknown package types are ignored; missing/null input becomes an empty set.
 */
public final class ForceCVSPackagesParser {

    private ForceCVSPackagesParser() {
    }

    public static Set<PackageType> emptyIfNull(Set<PackageType> packages) {
        return packages == null || packages.isEmpty() ? Set.of() : Set.copyOf(packages);
    }

    public static Set<PackageType> fromStrings(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<PackageType> result = new LinkedHashSet<>();
        for (String value : raw) {
            parseString(value).ifPresent(result::add);
        }
        return Set.copyOf(result);
    }

    private static Optional<PackageType> parseString(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim();
        try {
            return Optional.of(PackageType.valueOf(normalized.toUpperCase()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
