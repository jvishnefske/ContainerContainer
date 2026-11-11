package org.artifactory.rest.resource.unifiedevent.dto;

import lombok.Builder;
import org.artifactory.unifiedevent.constants.FilterCriteriaType;
import org.artifactory.unifiedevent.constants.SupportedConsumers;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.IncludeExclude;

import java.util.Map;

@Builder
public record ConsumerRegistrationRequest(
        SupportedConsumers name,
        String url,
        Map<SupportedUnifiedEvents, Map<FilterCriteriaType, IncludeExclude>> event
) {}
