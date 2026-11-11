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

package org.artifactory.util;

import lombok.Builder;
import org.jfrog.support.common.ThreadDumper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * @author Yoav Landman
 * @author Uriah Levy
 */
@Builder(builderClassName = "Builder")
public class ThreadDumpUtils {

    private static final Logger log = LoggerFactory.getLogger(ThreadDumpUtils.class);

    private int count;
    private long intervalMillis;

    public void dumpThreads(StringBuilder msg) {
        dumpThreads(msg, false);
    }

    /**
     * Should not be generally used. Mostly for testsing and debugging. Can cause severe performance issues.
     */
    public void dumpThreadsForcefully(StringBuilder msg) {
        dumpThreads(msg, true);
    }

    private void dumpThreads(StringBuilder msg, boolean force) {
        if (!validArgs()) {
            return;
        }
        try {
            if (log.isTraceEnabled() || force) {
                log.info("Printing locking debug information...");
                for (int i = 0; i < count; i++) {
                    CharSequence dump = new ThreadDumper().dumpThreads();
                    msg.append("\n").append("Dump number: ").append(i + 1)
                            .append(", local time: ").append(LocalDateTime.now())
                            .append("\n").append(dump);
                    // Sleep only if not the last iteration, and interval > 0
                    if (i != count - 1 && intervalMillis > 0L) {
                        Thread.sleep(intervalMillis);
                    }
                }
            }
        } catch (Exception e) {
            log.info("Could not dump threads", e);
        }
    }

    public static String getSingleThreadDump() {
        StringBuilder msg = new StringBuilder();
        ThreadDumpUtils.builder()
                .intervalMillis(0)
                .count(1)
                .build()
                .dumpThreadsForcefully(msg);
        return msg.toString();
    }

    private boolean validArgs() {
        boolean illegalState = (count == 0) || (count > 30) || (intervalMillis > 15000);
        if (illegalState) {
            log.warn("Dump params represent an illegal state. The count cannot " +
                    "be 0 or exceed 30, and the interval cannot exceed 15 seconds.");
            return false;
        }
        return true;
    }
}