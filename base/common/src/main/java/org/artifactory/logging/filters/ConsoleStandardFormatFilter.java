package org.artifactory.logging.filters;

import ch.qos.logback.core.filter.AbstractMatcherFilter;
import ch.qos.logback.core.spi.FilterReply;

import static ch.qos.logback.core.spi.FilterReply.ACCEPT;
import static ch.qos.logback.core.spi.FilterReply.DENY;

/**
 * @author Dor Solomon
 */
public class ConsoleStandardFormatFilter extends AbstractMatcherFilter {
    private final ConsoleJsonFormatFilter jsonFormatFilter = new ConsoleJsonFormatFilter();

    @Override
    public FilterReply decide(Object event) {
        if (jsonFormatFilter.decide(event) == ACCEPT) {
            return DENY;
        }
        return ACCEPT;
    }
}
