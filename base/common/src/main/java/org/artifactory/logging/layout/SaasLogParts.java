package org.artifactory.logging.layout;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.experimental.UtilityClass;
import org.artifactory.logging.entry.AccessEntryPart;
import org.artifactory.logging.entry.SaasBaseResponseEntryPart;
import org.artifactory.logging.entry.SaasOutboundRequestEntryPart;
import org.artifactory.logging.entry.SaasRequestEntryPart;
import org.artifactory.logging.entry.ServiceEntryAppPart;
import org.jfrog.access.common.ServiceType;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.Map;

import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_DATE_TIME;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_DURATION;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_LENGTH;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_METHOD;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_REPO;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_SHA1;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_STATUS;
import static org.jfrog.storage.binstore.ifc.model.BinaryRequestOutKeys.KEY_URL;

/**
 * @author Tomer Slutsky
 */
@UtilityClass
public class SaasLogParts {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZoneUTC();

    public static String formatTimestamp(long timestamp) {
        return DATE_TIME_FORMAT.print(timestamp);
    }

    public static ServiceEntryAppPart getServiceEntryAppPart(Map<String, Object> data, ILoggingEvent event) {
        return ServiceEntryAppPart.builder().dateTime(DATE_TIME_FORMAT.print(
                        Long.parseLong(data.get("timestamp").toString())))
                .service(ServiceType.ARTIFACTORY)
                .logLevel(data.get("level").toString())
                .traceId(getTraceId(event))
                .clazz(event.getCallerData()[0].getFileName())
                .line(String.valueOf(event.getCallerData()[0].getLineNumber()))
                .thread(event.getThreadName())
                .message(data.get("message").toString()).build();
    }

    public static AccessEntryPart getAccessEntryPart(Map<String, Object> data, ILoggingEvent event) {
        Object[] argumentArray = event.getArgumentArray();
        return AccessEntryPart.builder()
                .dateTime(DATE_TIME_FORMAT.print(Long.parseLong(data.get("timestamp").toString())))
                .traceId(getTraceId(event))
                .response(argumentArray[0].toString())
                .action(argumentArray[1].toString())
                .repositoryPath(argumentArray[2].toString())
                .message(argumentArray[3].toString())
                .username(argumentArray[4].toString())
                .userIp(argumentArray[5].toString())
                .authType(argumentArray[6].toString())
                .build();
    }

    public static SaasRequestEntryPart getRequestEntryPart(ILoggingEvent event) {
        Object[] argumentArray = event.getArgumentArray();
        return SaasRequestEntryPart.builder()
                .traceId(getTraceId(event))
                .client(argumentArray[2].toString())
                .user(argumentArray[3].toString())
                .method(argumentArray[4].toString())
                .path(argumentArray[5].toString())
                .length(Long.parseLong(argumentArray[7].toString())).userAgent(argumentArray[10].toString()).build();
    }

    public static SaasBaseResponseEntryPart getResponseEntryPart(ILoggingEvent event) {
        Object[] argumentArray = event.getArgumentArray();
        return SaasBaseResponseEntryPart.builder().dateTime(argumentArray[0].toString())
                .length(Long.parseLong(argumentArray[8].toString()))
                .status(argumentArray[6].toString())
                .duration(Long.parseLong(argumentArray[9].toString())).build();
    }

    public static SaasBaseResponseEntryPart getStorageResponseEntryPart(ILoggingEvent event) {
        return SaasBaseResponseEntryPart.builder().dateTime(event.getMDCPropertyMap().get(KEY_DATE_TIME.getKeyName()))
                .length(Long.parseLong(event.getMDCPropertyMap().get(KEY_LENGTH.getKeyName())))
                .status(event.getMDCPropertyMap().get(KEY_STATUS.getKeyName()))
                .duration(Long.parseLong(event.getMDCPropertyMap().get(KEY_DURATION.getKeyName()))).build();
    }

    public static SaasOutboundRequestEntryPart getOutboundRequestEntryPart(ILoggingEvent event) {
        Object[] argumentArray = event.getArgumentArray();
        return SaasOutboundRequestEntryPart.builder()
                .traceId(getTraceId(event))
                .remoteRepository(argumentArray[2].toString())
                .user(argumentArray[3].toString())
                .method(argumentArray[4].toString())
                .path(argumentArray[5].toString())
                .length(Long.parseLong(argumentArray[7].toString())).build();
    }

    public static SaasOutboundRequestEntryPart getStorageOutboundRequestEntryPart(ILoggingEvent event) {
        return SaasOutboundRequestEntryPart.builder()
                .traceId(getTraceId(event))
                .remoteRepository(event.getMDCPropertyMap().get(KEY_REPO.getKeyName()))
                .user("internal")
                .method(event.getMDCPropertyMap().get(KEY_METHOD.getKeyName()))
                .path(event.getMDCPropertyMap().get(KEY_URL.getKeyName()))
                .sha1(getSha1(event))
                .length(Long.parseLong(event.getMDCPropertyMap().get(KEY_LENGTH.getKeyName()))).build();
    }

    public static String getTraceId(ILoggingEvent event) {
        return event.getMDCPropertyMap().get("uber-trace-id");
    }

    public static String getSha1(ILoggingEvent event) {
        return event.getMDCPropertyMap().getOrDefault(KEY_SHA1.getKeyName(), null);
    }
}
