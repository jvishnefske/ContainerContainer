package org.artifactory.unifiedevent.registry;

import lombok.Builder;
import org.artifactory.unifiedevent.constants.FilterCriteriaType;
import org.artifactory.unifiedevent.constants.SupportedConsumers;
import org.artifactory.unifiedevent.constants.SupportedOutbound;
import org.artifactory.unifiedevent.model.IncludeExclude;
import org.jfrog.access.client.platformconfig.ConfigurationModel;

import java.util.Map;

@Builder(toBuilder = true)
public record Consumer(
        SupportedConsumers name,
        String endpoint,
        SupportedOutbound outbound,
        Map<FilterCriteriaType, IncludeExclude> filter
) implements ConfigurationModel {
}
