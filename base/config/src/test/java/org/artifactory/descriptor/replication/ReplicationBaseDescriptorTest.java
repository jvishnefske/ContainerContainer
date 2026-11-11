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

package org.artifactory.descriptor.replication;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Noam Y. Tenne
 */
@Test
public abstract class ReplicationBaseDescriptorTest<T extends ReplicationBaseDescriptor> {

    @Test
    public void testDefaultBaseValues() {
        T replicationDescriptor = constructDescriptor();
        assertFalse(replicationDescriptor.isEnabled(), "Default enabled state should be false.");
        assertNull(replicationDescriptor.getCronExp(), "Default cron exp should be null.");
        assertFalse(replicationDescriptor.isSyncDeletes(), "Default replication deletes state should be true.");
        assertTrue(replicationDescriptor.isSyncProperties(), "Default replication properties state should be true.");
        assertNull(replicationDescriptor.getRepoKey(), "Default repo key should be null.");
        assertNull(replicationDescriptor.getIncludePathPrefixPattern(),
                "Default replication include path prefix pattern should be null.");
        assertNull(replicationDescriptor.getExcludePathPrefixPattern(),
                "Default replication exclude path prefix pattern should be null.");
    }

    @Test
    public void testBaseSetters() throws Exception {
        T replicationDescriptor = constructDescriptor();
        replicationDescriptor.setEnabled(true);
        replicationDescriptor.setCronExp("0 0/5 * * * ?");
        replicationDescriptor.setSyncDeletes(false);
        replicationDescriptor.setSyncProperties(false);
        replicationDescriptor.setIncludePathPrefixPattern("/org/ca/7.5*/**");
        replicationDescriptor.setRepoKey("koko");

        assertTrue(replicationDescriptor.isEnabled(), "Unexpected enabled state.");
        assertEquals(replicationDescriptor.getCronExp(), "0 0/5 * * * ?", "Unexpected cron exp.");
        assertFalse(replicationDescriptor.isSyncDeletes(), "Unexpected replication deletes state.");
        assertFalse(replicationDescriptor.isSyncProperties(), "Unexpected replication properties state.");
        assertEquals(replicationDescriptor.getIncludePathPrefixPattern(), "/org/ca/7.5*/**",
                "Unexpected include path prefix pattern.");
        assertEquals(replicationDescriptor.getRepoKey(), "koko", "Unexpected repo key.");
    }

    @Test
    public void testEventBasedReplicationEnabled() {
        T replicationDescriptor = constructDescriptor();
        replicationDescriptor.setEnabled(false);
        replicationDescriptor.setEnableEventReplication(true);
        assertFalse(replicationDescriptor.isEventBasedReplicationEnabled(),
                "Active replication disabled should exclude repo from event channels.");

        replicationDescriptor.setEnabled(true);
        replicationDescriptor.setEnableEventReplication(true);
        assertTrue(replicationDescriptor.isEventBasedReplicationEnabled(),
                "Both flags enabled should include repo in event channels.");

        replicationDescriptor.setEnabled(true);
        replicationDescriptor.setEnableEventReplication(false);
        assertFalse(replicationDescriptor.isEventBasedReplicationEnabled(),
                "Event replication disabled should exclude repo from event channels.");
    }

    protected abstract T constructDescriptor();
}
