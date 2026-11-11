package org.artifactory.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.ConfigurationException;
import org.artifactory.repo.remote.CustomHttpHeader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Locale.ROOT;

@UtilityClass
public class CustomHttpHeaderValidator {

    public static void validate(List<CustomHttpHeader> headers) {
        if (!ConstantValues.remoteRepoCustomHttpHeadersEnabled.getBoolean()) {
            if (headers != null && !headers.isEmpty()) {
                throw new ConfigurationException(
                        "Custom HTTP headers are disabled (artifactory.remoteRepo.customHttpHeaders.enabled=false) "
                                + "but the repository configuration contains custom HTTP headers");
            }
            return;
        }
        if (headers == null || headers.isEmpty()) {
            return;
        }
        int maxCount = ConstantValues.remoteRepoCustomHttpHeadersMaxCount.getInt();
        if (headers.size() > maxCount) {
            throw new ConfigurationException("Custom HTTP headers count must not exceed " + maxCount + ", got " +
                    headers.size());
        }
        List<Pattern> blocklistPatterns = getBlocklistPatterns();
        List<Pattern> whitelistPatterns = getWhitelistPatterns();
        List<String> blockedPrefixes = getBlockedPrefixes();
        Set<String> seenNamesLower = new HashSet<>();
        for (CustomHttpHeader header : headers) {
            if (header == null) {
                throw new ConfigurationException("Custom HTTP headers must not contain null entries");
            }
            validateSingleCustomHeader(header, seenNamesLower, blocklistPatterns, whitelistPatterns, blockedPrefixes);
        }
    }

    private static List<Pattern> getBlocklistPatterns() {
        return ConstantValues.remoteRepoCustomHttpHeadersBlockList.getList().stream()
                .map(s -> s == null ? null : s.trim())
                .filter(StringUtils::isNotBlank)
                .map(CustomHttpHeaderValidator::compileHeaderPattern)
                .filter(p -> p != null)
                .collect(Collectors.toList());
    }

    private static List<Pattern> getWhitelistPatterns() {
        return ConstantValues.remoteRepoCustomHttpHeadersWhitelist.getList().stream()
                .map(s -> s == null ? null : s.trim())
                .filter(StringUtils::isNotBlank)
                .map(CustomHttpHeaderValidator::compileHeaderPattern)
                .filter(p -> p != null)
                .collect(Collectors.toList());
    }

    private static List<String> getBlockedPrefixes() {
        return ConstantValues.remoteRepoCustomHttpHeadersBlockedPrefixes.getList().stream()
                .map(s -> s == null ? null : s.trim())
                .filter(StringUtils::isNotBlank)
                .map(s -> s.toLowerCase(ROOT))
                .collect(Collectors.toList());
    }

    private static Pattern compileHeaderPattern(String entry) {
        if (entry == null || entry.isEmpty()) {
            return null;
        }
        String trimmed = entry.trim();
        try {
            if (HttpUtils.hasRegexSpecialChars(trimmed)) {
                return Pattern.compile(trimmed, Pattern.CASE_INSENSITIVE);
            }
            return Pattern.compile("^" + Pattern.quote(trimmed) + "$", Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            String message =
                    "Invalid custom HTTP header blocklist/whitelist pattern: \"" + trimmed + "\". " + e.getMessage();
            throw new ConfigurationException(message, e);
        }
    }

    private static void validateSingleCustomHeader(CustomHttpHeader header, Set<String> seenNamesLower,
            List<Pattern> blocklistPatterns, List<Pattern> whitelistPatterns, List<String> blockedPrefixes) {
        String name = header.getName() == null ? null : header.getName().trim();
        if (StringUtils.isBlank(name)) {
            throw new ConfigurationException("Custom HTTP header name must not be empty");
        }
        if (name.indexOf('%') >= 0) {
            throw new ConfigurationException("Custom HTTP header name must not contain '%'");
        }
        if (name.length() > HttpUtils.MAX_CUSTOM_HEADER_NAME_LENGTH) {
            throw new ConfigurationException("Custom HTTP header name must not exceed "
                    + HttpUtils.MAX_CUSTOM_HEADER_NAME_LENGTH + " characters");
        }
        if (!HttpUtils.isValidRfc7230HeaderName(name)) {
            throw new ConfigurationException("Custom HTTP header name must match RFC 7230 token format: " + name);
        }
        String nameLower = name.toLowerCase(ROOT);
        if (seenNamesLower.contains(nameLower)) {
            throw new ConfigurationException("Duplicate custom HTTP header name: " + name);
        }
        seenNamesLower.add(nameLower);
        if (!matchesAny(nameLower, whitelistPatterns)) {
            if (startsWithAny(nameLower, blockedPrefixes)) {
                throw new ConfigurationException("Custom HTTP header name is not allowed: " + name);
            }
            if (matchesAny(nameLower, blocklistPatterns)) {
                throw new ConfigurationException("Custom HTTP header name is not allowed: " + name);
            }
        }
        validateCustomHeaderValue(header, name);
    }

    private static boolean matchesAny(String nameLower, List<Pattern> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (Pattern p : patterns) {
            if (p != null && p.matcher(nameLower).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithAny(String value, List<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix != null && value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void validateCustomHeaderValue(CustomHttpHeader header, String name) {
        String value = header.getValue();
        if (value == null) {
            throw new ConfigurationException("Custom HTTP header value must not be null for header: " + name);
        }
        if (value.indexOf('%') >= 0) {
            throw new ConfigurationException("Custom HTTP header value must not contain '%' for header: " + name);
        }
        if (value.length() > HttpUtils.MAX_CUSTOM_HEADER_VALUE_LENGTH) {
            throw new ConfigurationException("Custom HTTP header value must not exceed "
                    + HttpUtils.MAX_CUSTOM_HEADER_VALUE_LENGTH + " characters for header: " + name);
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new ConfigurationException("Custom HTTP header value must not contain CRLF for header: " + name);
        }
        if (!header.isSensitive() && StringUtils.isBlank(value)) {
            throw new ConfigurationException("Custom HTTP header value must not be empty for header: " + name);
        }
    }
}
