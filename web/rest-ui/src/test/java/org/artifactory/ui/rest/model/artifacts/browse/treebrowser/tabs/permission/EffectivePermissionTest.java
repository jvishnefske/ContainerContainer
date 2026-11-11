package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.permission;

import org.artifactory.model.security.AceImpl;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Nadav Yogev
 */
@Test
public class EffectivePermissionTest {

    public void testEffectiveAggregation() {
        EffectivePermission effectivePermission = new EffectivePermission();
        effectivePermission.setRead(true);
        EffectivePermission effectivePermission2 = new EffectivePermission();
        effectivePermission2.setDelete(true);
        effectivePermission.aggregatePermissions(effectivePermission2);
        assertTrue(effectivePermission.isRead());
        assertTrue(effectivePermission.isDelete());
        assertFalse(effectivePermission.isAnnotate());
        assertFalse(effectivePermission.isDeploy());
        assertFalse(effectivePermission.isDistribute());
        assertFalse(effectivePermission.isManagedXrayMeta());
        assertNull(effectivePermission.getMask());
    }

    public void testAceEffectiveAggregation() {
        AceImpl ace = new AceImpl();
        ace.setDistribute(true);
        EffectivePermission effectivePermission = new EffectivePermission(ace);
        AceImpl ace2 = new AceImpl();
        ace2.setAnnotate(true);
        effectivePermission.aggregatePermissions(ace2);
        assertFalse(effectivePermission.isRead());
        assertFalse(effectivePermission.isDelete());
        assertTrue(effectivePermission.isAnnotate());
        assertFalse(effectivePermission.isDeploy());
        assertTrue(effectivePermission.isDistribute());
        assertFalse(effectivePermission.isManaged());
        assertFalse(effectivePermission.isManagedXrayMeta());
        assertEquals(Integer.valueOf(64), effectivePermission.getMask());
    }

    public void testAdmin() {
        EffectivePermission effectivePermission = new EffectivePermission();
        effectivePermission.setAdmin();
        assertTrue(effectivePermission.isRead());
        assertTrue(effectivePermission.isDelete());
        assertTrue(effectivePermission.isAnnotate());
        assertTrue(effectivePermission.isDeploy());
        assertTrue(effectivePermission.isDistribute());
        assertNull(effectivePermission.isManaged());
        assertTrue(effectivePermission.isManagedXrayMeta());
        assertNull(effectivePermission.getMask());
    }

}