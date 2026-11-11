package org.artifactory.customer.updates;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Alexei Vainshtein
 */
public class UsageUpdateModelTest {

    @Test
    public void testHasWarningReached() {
        UsageUpdateModel updateModel = new UsageUpdateModel();
        Assert.assertFalse(updateModel.hasWarningReached());
        UsageUpdateItem storage = new UsageUpdateItem();
        storage.setPercentage(90);
        storage.setUsed(1000);
        storage.setLimit(2000);
        storage.setUnits("bytes");
        storage.setStatus("blocked");
        updateModel.setStorage(storage);
        Assert.assertFalse(updateModel.hasWarningReached());
        storage.setStatus("warning");
        Assert.assertTrue(updateModel.hasWarningReached());
    }

    @Test
    public void testHasLimitReached() {
        UsageUpdateModel updateModel = new UsageUpdateModel();
        Assert.assertFalse(updateModel.hasLimitReached());
        UsageUpdateItem traffic = new UsageUpdateItem();
        traffic.setPercentage(90);
        traffic.setUsed(1000);
        traffic.setLimit(2000);
        traffic.setUnits("bytes");
        traffic.setStatus("warning");
        updateModel.setTraffic(traffic);
        Assert.assertFalse(updateModel.hasLimitReached());
        traffic.setStatus("blocked");
        Assert.assertTrue(updateModel.hasLimitReached());
    }
}