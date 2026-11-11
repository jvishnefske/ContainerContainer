package org.artifactory.logging.layout.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.boolex.EvaluationException;
import ch.qos.logback.core.boolex.EventEvaluator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.ActivityConstants;
import org.artifactory.logging.layout.BaseJsonLayout;
import org.artifactory.logging.layout.SaasLogParts;
import org.jfrog.access.common.ServiceType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.artifactory.logging.SaasLogNames.SERVICE_LOG;

/**
 * @author Tomer Slutsky
 */
@Slf4j
public class ServiceJsonLayout extends BaseJsonLayout {
    public static final Set<String> DEFAULT_MDC_WHITE_LIST = Set.of(
            ActivityConstants.ACTIVITY_PROP_KIND,
            ActivityConstants.ACTIVITY_PROP_GROUP,
            ActivityConstants.ACTIVITY_PROP_PACKAGE,
            ActivityConstants.ACTIVITY_PROP_ACTOR
    );

    private static final int maxStackTraceLines = 5;
    private final Set<String> mdcFieldsWhitelist = new HashSet<>(DEFAULT_MDC_WHITE_LIST);
    @Getter
    @Setter
    private String throttleStackTrace = "THROTTLE_EXCEPTIONS";
    private EventEvaluator<ILoggingEvent> shouldThrottleStackTrace;

    public ServiceJsonLayout() {
        super();
    }

    public Set<String> getMdcFields() {
        return this.mdcFieldsWhitelist;
    }

    public void addIncludeMdcField(String fieldName) {
        this.mdcFieldsWhitelist.add(fieldName);
    }

    public void addExcludeMdcField(String fieldName) {
        if ("*".equals(fieldName)) {
            this.mdcFieldsWhitelist.clear();
        } else {
            this.mdcFieldsWhitelist.remove(fieldName);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void start() {
        if (this.throttleStackTrace != null && !this.throttleStackTrace.isEmpty()) {
            Map<String, EventEvaluator<?>> evaluatorMap = (Map<String, EventEvaluator<?>>) context
                    .getObject(CoreConstants.EVALUATOR_MAP);
            this.shouldThrottleStackTrace = (EventEvaluator<ILoggingEvent>) evaluatorMap.get(this.throttleStackTrace);
        }
        super.start();
    }

    @Override
    protected void addCustomDataToJsonMap(Map<String, Object> data, ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        copyJsonFields(fields, data, event);

        data.clear();
        data.put(SERVICE_LOG.getLogKey(), SERVICE_LOG.getLogName());
        data.put("app", fields);
    }

    protected void copyJsonFields(Map<String, Object> fields, Map<String, Object> data, ILoggingEvent event) {
        fields.put("datetime", data.get(TIMESTAMP_ATTR_NAME));
        fields.put("service", ServiceType.ARTIFACTORY);
        fields.put("loglevel", data.get(LEVEL_ATTR_NAME));
        fields.put("traceid", event.getMDCPropertyMap().get("uber-trace-id"));
        if (event.getCallerData().length > 0) {
            int lineNumber = 0;
            for (int i = 0; i < Math.min(maxStackTraceLines, event.getCallerData().length); i++) {
                if (event.getLoggerName().equals(event.getCallerData()[i].getClassName())) {
                    lineNumber = i;
                    break;
                }
            }
            fields.put("class", event.getCallerData()[lineNumber].getClassName());
            fields.put("line", event.getCallerData()[lineNumber].getLineNumber());
        }
        fields.put("thread", data.get(THREAD_ATTR_NAME));
        fields.put("message", data.get(FORMATTED_MESSAGE_ATTR_NAME));

        // Copy exception (if any)
        Object exception = data.get(EXCEPTION_ATTR_NAME);
        if (exception != null) {
            fields.put("exception", exception);
        }

        // Add MDC (if any)
        addMdcFields(event, fields);
    }

    @Override
    protected String formatTimestamp(long timestamp) {
        return SaasLogParts.formatTimestamp(timestamp);
    }

    @Override
    protected void addThrowableInfo(String fieldName, boolean field, ILoggingEvent value, Map<String, Object> map) {
        if (field && value != null && value.getThrowableProxy() != null) {
            Map<String, Object> details = new LinkedHashMap<>();
            boolean printStackTrace = shouldPrintStackTrace(value);
            fillExceptionDetails(value, details, printStackTrace);
            map.put(fieldName, details);
        }
    }

    protected boolean shouldPrintStackTrace(ILoggingEvent event) {
        try {
            return this.shouldThrottleStackTrace == null || !this.shouldThrottleStackTrace.evaluate(event);
        } catch (EvaluationException e) {
            return true;
        }
    }

    protected void fillExceptionDetails(ILoggingEvent event, Map<String, Object> details, boolean printStackTrace) {
        IThrowableProxy error = event.getThrowableProxy();
        details.put("msg", error.getMessage());
        details.put("type", error.getClassName());
        if (printStackTrace) {
            String stack = this.getThrowableProxyConverter().convert(event);
            int offset = stack.indexOf('\n');
            if (offset > 0) {
                stack = stack.substring(offset + 1);
            }
            details.put("stack", stack);
        }
    }

    protected void addMdcFields(ILoggingEvent event, Map<String, Object> map) {
        if (this.includeMDC && event.getLevel().isGreaterOrEqual(Level.WARN)) {
            Map<String, String> mdc = prepareMdcFields(event.getMDCPropertyMap());
            addMap("mdc", true, mdc, map);
        }
    }

    protected Map<String, String> prepareMdcFields(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (this.mdcFieldsWhitelist.contains(entry.getKey())) {
                result.put(entry.getKey().replace(".", "_"), entry.getValue());
            }
        }
        return result;
    }
}
