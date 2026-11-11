package org.artifactory.logging.layout.cleanup;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.contrib.jackson.JacksonJsonFormatter;
import ch.qos.logback.contrib.json.classic.JsonLayout;
import lombok.SneakyThrows;
import org.artifactory.logging.SaasLogNames;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class CleanupAuditJsonLayout extends JsonLayout {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");

    public CleanupAuditJsonLayout() {
        setAppendLineSeparator(true);
        setJsonFormatter(new JacksonJsonFormatter());
    }

    @SneakyThrows
    @Override
    protected Map<String, Object> toJsonMap(ILoggingEvent event) {
        Object[] argumentArray = event.getArgumentArray();
        if (argumentArray.length == 0) {
            return Map.of();
        }
        String formattedDateTime = formatter.format(Instant.now().atZone(UTC_ZONE_ID));
        return Map.of(SaasLogNames.CLEANUP_AUDIT_LOG.getLogKey(), SaasLogNames.CLEANUP_AUDIT_LOG.getLogName(),
                "timestamp", formattedDateTime,
                "cleaned_node", argumentArray[0]);
    }
}