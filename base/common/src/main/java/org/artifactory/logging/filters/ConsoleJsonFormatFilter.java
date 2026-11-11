package org.artifactory.logging.filters;

import ch.qos.logback.core.filter.AbstractMatcherFilter;
import ch.qos.logback.core.spi.FilterReply;
import com.jfrog.sysconf.SysConfig;

/**
 * @author Tomer Slutsky
 */
public class ConsoleJsonFormatFilter extends AbstractMatcherFilter {
    static final String ENABLE_CONSOLE_LOG_PRO_NAME = "shared.logging.enableJsonConsoleLogAppenders";
    public static volatile Boolean isEnabled = null;

    @Override
    public FilterReply decide(Object event) {
        if (isConsoleJsonLogEnabled()) {
            return FilterReply.ACCEPT;
        }
        return FilterReply.DENY;
    }

    private boolean isConsoleJsonLogEnabled() {
        if (isEnabled == null) {
            synchronized (this) {
                if (isEnabled == null) {
                    try {
                        isEnabled =
                                new SysConfig.Builder().withHome(SysConfig.detectAbsoluteHomeDir().getAbsolutePath())
                                        .build()
                                        .getBoolean(ENABLE_CONSOLE_LOG_PRO_NAME, false);
                    } catch (Exception e) {
                        String forceConsoleJsonLog = System.getProperty("FORCE_CONSOLE_JSON_LOG");
                        if (forceConsoleJsonLog != null) {
                            isEnabled = forceConsoleJsonLog.equals("true");
                            return isEnabled;
                        }
                        isEnabled = false;
                        System.err.println(
                                "Error has occurred while reading system.yaml config. could not determine console " +
                                        "logs format." + e.getMessage());
                        System.exit(1);
                    }
                }
            }
        }
        return isEnabled;
    }
}
