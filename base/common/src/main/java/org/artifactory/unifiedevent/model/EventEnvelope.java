package org.artifactory.unifiedevent.model;

import lombok.Builder;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;

@Builder(toBuilder = true)
public record EventEnvelope<T> (
        String specVersion,
        String id,
        SupportedUnifiedEvents type,
        String source,
        String time,
        String subject,
        String dataSchema,
        String dataContentType,
        HeadersData eventHeaders,
        T data
) {
}
