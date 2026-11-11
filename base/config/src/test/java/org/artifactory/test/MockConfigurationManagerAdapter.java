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

package org.artifactory.test;

import org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.config.broadcast.BroadcastChannel;
import org.jfrog.config.broadcast.TemporaryBroadcastChannelImpl;
import org.jfrog.config.db.DbChannel;
import org.jfrog.config.db.TemporaryDBChannel;

/**
 * @author gidis
 */
public class MockConfigurationManagerAdapter extends ArtifactoryConfigurationAdapter {

    public MockConfigurationManagerAdapter(ArtifactoryHome home) {
        super(home);
    }

    @Override
    public void initialize() {
        this.home.initArtifactorySystemProperties();
        this.ha = home.getArtifactoryHaNodePropertiesFile().exists();
    }

    @Override
    public DbChannel getDbChannel() {
        if (dbChannel == null) {
            ArtifactoryDbProperties dbProperties = this.home.getDBProperties();
            this.dbChannel = new TemporaryDBChannel(dbProperties);
        }
        return dbChannel;
    }

    @Override
    public BroadcastChannel getBroadcastChannel() {
        return new TemporaryBroadcastChannelImpl();
    }

    @Override
    public void destroy() {
    }

    @Override
    public void setPermanentBroadcastChannel(BroadcastChannel broadcastChannel) {
    }

    @Override
    public void setPermanentDBChannel(DbChannel permanentDbChannel) {
    }

    @Override
    public long getBlockingTime() {
        return ArtifactoryConfigurationAdapter.BLOCKING_TIME;
    }

    @Override
    public long getQuietPeriod() {
        return ArtifactoryConfigurationAdapter.QUITE_PERIOD;
    }
}
