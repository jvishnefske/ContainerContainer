/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.event.service;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;
import com.google.common.collect.Maps;
import org.artifactory.event.EventType;
import org.artifactory.mbean.MBeanRegistrationServiceImpl;
import org.artifactory.repo.event.EventInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.concurrent.ConcurrentMap;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * Class that collects dropwizard metrics of node events and report as mbeans.
 *
 * @author Yossi Shaul
 */
@Component
public class EventsDropwizardMetrics implements EventsMetrics {

    @Autowired
    private MetricRegistry metricsRegistry;

    @Autowired
    private MBeanRegistrationServiceImpl mbeansService;

    private Meter events;   // meter for all events

    /**
     * Map of meters by type
     */
    private final ConcurrentMap<EventType, Meter> eventsByType = Maps.newConcurrentMap();

    @PostConstruct
    public void init() {
        events = metricsRegistry.meter(metricsName("ALL"));

        // create meter per event type
        Arrays.stream(EventType.values()).
                forEach(et -> eventsByType.put(et, metricsRegistry.meter(metricsName(et.name()))));

        JmxReporter.forRegistry(metricsRegistry)
                .createsObjectNamesWith((type, domain, name) ->
                        MBeanRegistrationServiceImpl.createObjectName("Metrics, name=Event", name))
                .filter((name, metric) -> name.startsWith("events")).build()
                .start();
    }

    private String metricsName(String name) {
        return name("events", name);
    }

    @Override
    public void reportEvent(EventInfo event) {
        events.mark();
        eventsByType.get(event.getType()).mark();
    }

}