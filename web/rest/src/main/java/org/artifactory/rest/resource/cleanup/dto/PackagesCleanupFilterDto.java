package org.artifactory.rest.resource.cleanup.dto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.exception.http.BadRequestException;

import java.util.List;

/**
 * @author punyap
 */
@Builder(toBuilder = true)
public record PackagesCleanupFilterDto(
        String property,
        Long propertyThreshold,
        List<PackagesDto> packages
) {
    private static final ObjectWriter objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();
    public enum Property {
        LAST_DOWNLOADED;

        public static Property fromString(String property) {
            for (Property prop : Property.values()) {
                if (prop.name().equalsIgnoreCase(property)) {
                    return prop;
                }
            }
            throw new IllegalArgumentException("No enum constant for property: " + property);
        }
    }
    public void validate() {
        // Validate fields
        validateProperty(property);
        validateThreshold(propertyThreshold);
        validatePackages(packages);
    }
    private void validateProperty(String property) {
        if (StringUtils.isBlank(property)) {
            throw new BadRequestException("Property is required");
        }

        try {
            Property.fromString(property);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(String.format("Property '%s' is not a valid property", property));
        }
    }

    private void validateThreshold(Long propertyThreshold) {
        if (propertyThreshold == null || propertyThreshold < 0) {
            throw new BadRequestException("Threshold must be a positive number");
        }
    }

    private void validatePackages(List<PackagesDto> packages) {
        if (packages == null) {
            throw new BadRequestException("Packages list is required");
        }
        if(packages.isEmpty()) {
            throw new BadRequestException("Packages list must not be empty");
        }
        if(packages.size() > 1000) {
            throw new BadRequestException("Packages list must not exceed 1000");
        }
    }

    public String toJSONString() {
        try {
            return objectWriter.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new InternalError("Failed to serialize object to JSON. Error: " + e.getMessage());
        }
    }
}
