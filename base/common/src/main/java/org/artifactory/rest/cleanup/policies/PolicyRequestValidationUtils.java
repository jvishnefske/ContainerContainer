package org.artifactory.rest.cleanup.policies;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.exception.http.BadRequestException;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Oleksandr Legenkiy
 */
@Slf4j
@UtilityClass
public class PolicyRequestValidationUtils {

    public static void validatePolicyKeys(String policyKeyFromUrl, String policyKey) {
        if (StringUtils.isBlank(policyKeyFromUrl) || StringUtils.isBlank(policyKey) ||
                !policyKeyFromUrl.equals(policyKey)) {
            throw new BadRequestException("Incorrect policy key value");
        }
    }

    public static void validateItemTypes(Set<String> itemTypes) {
        String error;
        if (CollectionUtils.isEmpty(itemTypes)) {
            error = "itemType is mandatory";
            log.warn(error);
            throw new BadRequestException(error);
        }
        List<String> codes = Arrays.stream(PolicyItemType.values())
                .map(PolicyItemType::getCode)
                .toList();
        boolean valid = new HashSet<>(codes).containsAll(itemTypes);
        if (!valid) {
            error = "Wrong item type, must be one of [" + String.join(", ", codes) + "]";
            log.warn(error);
            throw new BadRequestException(error);
        }
    }
}
