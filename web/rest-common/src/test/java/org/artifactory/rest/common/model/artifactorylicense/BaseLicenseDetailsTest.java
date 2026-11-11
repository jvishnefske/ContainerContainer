package org.artifactory.rest.common.model.artifactorylicense;

import org.jfrog.common.platform.subscription.SubscriptionType;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Alexei Vainshtein
 */
public class BaseLicenseDetailsTest {

    @Test
    public void testConstructorFree() {
        BaseLicenseDetails baseLicenseDetails = new BaseLicenseDetails("", "", "", SubscriptionType.FREE);
        Assert.assertEquals(baseLicenseDetails.getSubscriptionType(), SubscriptionType.FREE.getString());
        baseLicenseDetails = new BaseLicenseDetails("", "", "", null);
        Assert.assertNull(baseLicenseDetails.getSubscriptionType());
    }

    @Test
    public void testConstructorFreeXray() {
        BaseLicenseDetails baseLicenseDetails = new BaseLicenseDetails("", "", "", SubscriptionType.FREE_XRAY);
        Assert.assertEquals(baseLicenseDetails.getSubscriptionType(), SubscriptionType.FREE_XRAY.getString());
        baseLicenseDetails = new BaseLicenseDetails("", "", "", null);
        Assert.assertNull(baseLicenseDetails.getSubscriptionType());
    }
}