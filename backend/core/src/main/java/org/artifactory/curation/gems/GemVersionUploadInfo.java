package org.artifactory.curation.gems;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;

/**
 * Represents version upload information from the RubyGems API.
 * Maps to the JSON response from /api/v1/versions/[GEM_NAME].json
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GemVersionUploadInfo {

    private static final String RUBY_PLATFORM = "ruby";

    // Parsed by JavaTimeModule which handles ISO 8601 flexibly (with/without milliseconds)
    private Instant createdAt;

    private String number;

    private String platform;

    /**
     * Returns the version identifier as it appears in the compact index.
     * Format: "number" or "number-platform" if platform is not "ruby".
     *
     * @return the version for index file representation
     */
    public String getVersionForIndexFile() {
        if (StringUtils.isNotBlank(platform) && !RUBY_PLATFORM.equalsIgnoreCase(platform)) {
            return String.join("-", number, platform);
        }
        return number;
    }
}
