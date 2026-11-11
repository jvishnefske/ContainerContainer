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

package org.artifactory.storage.db.security.service.access;

import com.google.common.collect.Maps;
import org.artifactory.model.security.GroupImpl;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.MutableGroupInfo;
import org.jfrog.access.model.Realm;
import org.jfrog.access.rest.group.Group;
import org.jfrog.access.rest.group.GroupRequest;
import org.jfrog.access.rest.group.GroupResponse;
import org.testng.annotations.Test;

import java.util.Map;

import static org.artifactory.storage.db.security.service.access.GroupMapper.ArtifactoryBuiltInGroupProperty.artifactory_admin;
import static org.artifactory.storage.db.security.service.access.GroupMapper.ArtifactoryBuiltInGroupProperty.policy_manager;
import static org.artifactory.storage.db.security.service.access.GroupMapper.ArtifactoryBuiltInGroupProperty.policy_viewer;
import static org.artifactory.storage.db.security.service.access.GroupMapper.ArtifactoryBuiltInGroupProperty.reports_manager;
import static org.artifactory.storage.db.security.service.access.GroupMapper.ArtifactoryBuiltInGroupProperty.watch_manager;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.MapAssert.entry;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Units tests of the {@link GroupMapper}.
 *
 * @author Yossi Shaul
 */
@Test
public class GroupMapperTest {

    public void groupInfoToGroup() {
        GroupInfo groupInfo = sampleGroupInfo();
        GroupRequest accessGroup = GroupMapper.toAccessGroup(groupInfo);
        assertNotNull(accessGroup);
        assertEquals(accessGroup.getName(), groupInfo.getGroupName());
        assertEquals(accessGroup.getDescription(), groupInfo.getDescription());
        assertEquals(accessGroup.isAutoJoin(), groupInfo.isNewUserDefault());
        assertNull(accessGroup.getCustomData());
        assertEquals(accessGroup.getRealm().getName(), groupInfo.getRealm());
        assertEquals(accessGroup.getRealmAttributes(), groupInfo.getRealmAttributes());
        assertNull(accessGroup.getExternalId());
    }

    public void groupInfoWithExternalIdToGroup() {
        MutableGroupInfo groupInfo = sampleGroupInfo();
        groupInfo.setExternalId("ext-id");
        GroupRequest accessGroup = GroupMapper.toAccessGroup(groupInfo);
        assertEquals(accessGroup.getExternalId(), groupInfo.getExternalId());
    }

    public void groupInfoWithEmptyRealmToGroup() {
        GroupImpl groupInfo = new GroupImpl("northmen");
        groupInfo.setRealm("");

        GroupRequest accessGroup = GroupMapper.toAccessGroup(groupInfo);
        assertNotNull(accessGroup);
        assertEquals(accessGroup.getRealm(), Realm.INTERNAL);
    }

    public void groupInfoWithNullRealmToGroup() {
        GroupImpl groupInfo = new GroupImpl("northmen");
        groupInfo.setRealm(null);

        GroupRequest accessGroup = GroupMapper.toAccessGroup(groupInfo);
        assertNotNull(accessGroup);
        assertEquals(accessGroup.getRealm(), Realm.INTERNAL);
    }

    public void groupInfoWithGroupAdminToGroup() {
        MutableGroupInfo groupInfo = sampleGroupInfo();
        groupInfo.setAdminPrivileges(true);
        GroupRequest accessGroup = GroupMapper.toAccessGroup(groupInfo);
        assertThat(accessGroup.getCustomData()).isNotNull().hasSize(1)
                .includes(entry(artifactory_admin.name(), "true"));
    }

    public void groupInfoWithPolicyManagerToGroup() {
        MutableGroupInfo groupInfo = sampleGroupInfo();
        groupInfo.setPolicyManager(true);
        GroupRequest accessGroup = GroupMapper.toAccessGroup(groupInfo);
        assertThat(accessGroup.getCustomData()).isNotNull().hasSize(1)
                .includes(entry(policy_manager.name(), "true"));
    }

    public void groupInfoWithPolicyViewerToGroup() {
        MutableGroupInfo groupInfo = sampleGroupInfo();
        groupInfo.setPolicyViewer(true);
        GroupRequest accessGroup = GroupMapper.toAccessGroup(groupInfo);
        assertThat(accessGroup.getCustomData()).isNotNull().hasSize(1)
                .includes(entry(policy_viewer.name(), "true"));
    }

    public void groupInfoWithWatchManagerToGroup() {
        MutableGroupInfo groupInfo = sampleGroupInfo();
        groupInfo.setWatchManager(true);
        GroupRequest accessGroup = GroupMapper.toAccessGroup(groupInfo);
        assertThat(accessGroup.getCustomData()).isNotNull().hasSize(1)
                .includes(entry(watch_manager.name(), "true"));
    }

    public void groupInfoWithReportsManagerToGroup() {
        MutableGroupInfo groupInfo = sampleGroupInfo();
        groupInfo.setReportsManager(true);
        GroupRequest accessGroup = GroupMapper.toAccessGroup(groupInfo);
        assertThat(accessGroup.getCustomData()).isNotNull().hasSize(1)
                .includes(entry(reports_manager.name(), "true"));
    }

    public void groupInfoUnknownRealmToGroup() {
        MutableGroupInfo groupInfo = sampleGroupInfo();
        groupInfo.setRealm("unknown_realm");
        GroupRequest accessGroup = GroupMapper.toAccessGroup(groupInfo);
        assertEquals(accessGroup.getRealm(), Realm.valueOf(groupInfo.getRealm()));
    }

    public void groupInfoArtifactoryRealmToAccessInternalRealm() {
        MutableGroupInfo groupInfo = sampleGroupInfo();
        groupInfo.setRealm("artifactory");
        GroupRequest accessGroup = GroupMapper.toAccessGroup(groupInfo);
        assertEquals(accessGroup.getRealm(), Realm.INTERNAL, "Old artifactory realm is converted to internal realm");
    }

    public void accessGroupToGroupInfo() {
        Group accessGroup = sampleAccessGroup();
        GroupInfo groupInfo = GroupMapper.toArtifactoryGroup(accessGroup);
        assertNotNull(groupInfo);
        assertEquals(groupInfo.getGroupName(), accessGroup.getName());
        assertEquals(groupInfo.getDescription(), accessGroup.getDescription());
        assertEquals(groupInfo.isNewUserDefault(), accessGroup.isAutoJoin());
        assertFalse(groupInfo.isAdminPrivileges());
        assertFalse(groupInfo.isPolicyViewer());
        assertFalse(groupInfo.isPolicyManager());
        assertFalse(groupInfo.isWatchManager());
        assertEquals(groupInfo.getRealm(), accessGroup.getRealm().getName());
        assertEquals(groupInfo.getRealmAttributes(), accessGroup.getRealmAttributes());
        assertNull(groupInfo.getExternalId());
    }

    public void accessGroupWithExternalIdToGroupInfo() {
        Group accessGroup = new GroupResponse().name("northmen").description("the northmen").autoJoin(false)
                .realm(Realm.LDAP).realmAttributes("a b c").externalId("ext-id");
        GroupInfo groupInfo = GroupMapper.toArtifactoryGroup(accessGroup);
        assertEquals(groupInfo.getExternalId(), accessGroup.getExternalId());
    }

    public void accessAdminGroupToGroupInfo() {
        Group accessGroup = adminAccessGroup();
        GroupInfo groupInfo = GroupMapper.toArtifactoryGroup(accessGroup);
        assertNotNull(groupInfo);
        assertEquals(groupInfo.getGroupName(), accessGroup.getName());
        assertEquals(groupInfo.getDescription(), accessGroup.getDescription());
        assertEquals(groupInfo.isNewUserDefault(), accessGroup.isAutoJoin());
        assertTrue(groupInfo.isAdminPrivileges());
        assertFalse(groupInfo.isPolicyViewer());
        assertFalse(groupInfo.isPolicyManager());
        assertFalse(groupInfo.isWatchManager());
        assertEquals(groupInfo.getRealm(), accessGroup.getRealm().getName());
        assertEquals(groupInfo.getRealmAttributes(), accessGroup.getRealmAttributes());
    }

    public void accessWatchManagerGroupToGroupInfo() {
        Group accessGroup = watchManagerGroup();
        GroupInfo groupInfo = GroupMapper.toArtifactoryGroup(accessGroup);
        assertNotNull(groupInfo);
        assertEquals(groupInfo.getGroupName(), accessGroup.getName());
        assertEquals(groupInfo.getDescription(), accessGroup.getDescription());
        assertEquals(groupInfo.isNewUserDefault(), accessGroup.isAutoJoin());
        assertFalse(groupInfo.isAdminPrivileges());
        assertFalse(groupInfo.isPolicyViewer());
        assertFalse(groupInfo.isPolicyManager());
        assertTrue(groupInfo.isWatchManager());
        assertEquals(groupInfo.getRealm(), accessGroup.getRealm().getName());
        assertEquals(groupInfo.getRealmAttributes(), accessGroup.getRealmAttributes());
    }

    public void accessPolicyViewerGroupToGroupInfo() {
        Group accessGroup = policyViewerGroup();
        GroupInfo groupInfo = GroupMapper.toArtifactoryGroup(accessGroup);
        assertNotNull(groupInfo);
        assertEquals(groupInfo.getGroupName(), accessGroup.getName());
        assertEquals(groupInfo.getDescription(), accessGroup.getDescription());
        assertEquals(groupInfo.isNewUserDefault(), accessGroup.isAutoJoin());
        assertFalse(groupInfo.isAdminPrivileges());
        assertTrue(groupInfo.isPolicyViewer());
        assertFalse(groupInfo.isPolicyManager());
        assertFalse(groupInfo.isWatchManager());
        assertEquals(groupInfo.getRealm(), accessGroup.getRealm().getName());
        assertEquals(groupInfo.getRealmAttributes(), accessGroup.getRealmAttributes());
    }

    public void accessPolicyManagerGroupToGroupInfo() {
        Group accessGroup = policyManagerGroup();
        GroupInfo groupInfo = GroupMapper.toArtifactoryGroup(accessGroup);
        assertNotNull(groupInfo);
        assertEquals(groupInfo.getGroupName(), accessGroup.getName());
        assertEquals(groupInfo.getDescription(), accessGroup.getDescription());
        assertEquals(groupInfo.isNewUserDefault(), accessGroup.isAutoJoin());
        assertFalse(groupInfo.isAdminPrivileges());
        assertFalse(groupInfo.isPolicyViewer());
        assertTrue(groupInfo.isPolicyManager());
        assertFalse(groupInfo.isWatchManager());
        assertEquals(groupInfo.getRealm(), accessGroup.getRealm().getName());
        assertEquals(groupInfo.getRealmAttributes(), accessGroup.getRealmAttributes());
    }

    public void accessReportsManagerGroupToGroupInfo() {
        Group accessGroup = allResourceReaderGroup();
        GroupInfo groupInfo = GroupMapper.toArtifactoryGroup(accessGroup);
        assertNotNull(groupInfo);
        assertEquals(groupInfo.getGroupName(), accessGroup.getName());
        assertEquals(groupInfo.getDescription(), accessGroup.getDescription());
        assertEquals(groupInfo.isNewUserDefault(), accessGroup.isAutoJoin());
        assertFalse(groupInfo.isAdminPrivileges());
        assertTrue(groupInfo.isReportsManager());
        assertFalse(groupInfo.isPolicyManager());
        assertFalse(groupInfo.isWatchManager());
        assertEquals(groupInfo.getRealm(), accessGroup.getRealm().getName());
        assertEquals(groupInfo.getRealmAttributes(), accessGroup.getRealmAttributes());
    }

    public void accessInternalGroupToGroupInfo() {
        Group accessGroup = new GroupResponse().name("northmen").description("the northmen").autoJoin(false)
                .realm(Realm.INTERNAL).realmAttributes("a b c");
        GroupInfo groupInfo = GroupMapper.toArtifactoryGroup(accessGroup);
        assertEquals(groupInfo.getRealm(), "internal");
    }

    private MutableGroupInfo sampleGroupInfo() {
        GroupImpl group = new GroupImpl("northmen");
        group.setDescription("the northmen");
        group.setNewUserDefault(false);
        group.setAdminPrivileges(false);
        group.setRealm("something_new");
        group.setRealmAttributes("a b c");
        return group;
    }

    private Group sampleAccessGroup() {
        return new GroupResponse().name("northmen").description("the northmen").autoJoin(false)
                .realm(Realm.LDAP).realmAttributes("a b c");
    }

    private Group adminAccessGroup() {
        Map<String, String> customData = Maps.newHashMap();
        customData.put(artifactory_admin.name(), "true");
        return new GroupResponse().name("northmen").description("the northmen").autoJoin(false)
                .realm(Realm.LDAP).realmAttributes("a b c").customData(customData);
    }

    private Group watchManagerGroup() {
        Map<String, String> customData = Maps.newHashMap();
        customData.put(watch_manager.name(), "true");
        return new GroupResponse().name("northmen").description("the northmen").autoJoin(false)
                .realm(Realm.LDAP).realmAttributes("a b c").customData(customData);
    }

    private Group policyViewerGroup() {
        Map<String, String> customData = Maps.newHashMap();
        customData.put(policy_viewer.name(), "true");
        return new GroupResponse().name("northmen").description("the northmen").autoJoin(false)
                .realm(Realm.LDAP).realmAttributes("a b c").customData(customData);
    }

    private Group policyManagerGroup() {
        Map<String, String> customData = Maps.newHashMap();
        customData.put(policy_manager.name(), "true");
        return new GroupResponse().name("northmen").description("the northmen").autoJoin(false)
                .realm(Realm.LDAP).realmAttributes("a b c").customData(customData);
    }

    private Group allResourceReaderGroup() {
        Map<String, String> customData = Maps.newHashMap();
        customData.put(reports_manager.name(), "true");
        return new GroupResponse().name("northmen").description("the northmen").autoJoin(false)
                .realm(Realm.LDAP).realmAttributes("a b c").customData(customData);
    }

}