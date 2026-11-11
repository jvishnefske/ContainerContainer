package org.artifactory.jfbus;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

import java.util.Map;

/**
 * Immutable request for a UEM v2 publish.
 *
 * @param id               optional originating event id; forwarded as the CloudEvents {@code ce-id} so the
 *                         published event keeps the source event's identity (consumers can dedup on it). When
 *                         {@code null}, the library mints a fresh {@code ce-id}. (Note: {@code ce-time} is left
 *                         to the library to stamp — it validates a strict RFC3339-with-millis format, so
 *                         forwarding an arbitrary source time could throw and drop the event.)
 * @param eventHeaders     extra CloudEvents extension headers; the library emits each with an {@code eh-} prefix
 *                         (e.g. {@code eventHeader("custom", v)} becomes header {@code eh-custom}), so this is not
 *                         a way to override raw {@code ce-*} headers.
 * @param transportHeaders extra broker transport headers, emitted as-is.
 */
@Builder
public record UemV2PublishRequest(
        @NonNull Object data,
        @NonNull String source,
        @NonNull String type,
        @NonNull String subject,
        @NonNull String dataSchema,
        @NonNull String entityType,
        String partitionKeyOverride,
        String id,
        @Singular("eventHeader") Map<String, String> eventHeaders,
        @Singular("transportHeader") Map<String, String> transportHeaders
) {}
