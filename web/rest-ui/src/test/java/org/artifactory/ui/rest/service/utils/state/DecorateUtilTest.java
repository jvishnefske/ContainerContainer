package org.artifactory.ui.rest.service.utils.state;

import org.artifactory.customer.updates.UsageUpdateItem;
import org.artifactory.customer.updates.UsageUpdateModel;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexei Vainshtein
 */
public class DecorateUtilTest {

    @Test
    public void testGetWarningMessage() {
        UsageUpdateModel updateModel = new UsageUpdateModel();
        UsageUpdateItem storage = new UsageUpdateItem();
        storage.setPercentage(50);
        storage.setUsed(1024L * 1024L * 1024L);
        storage.setLimit(1024L * 1024L * 1024L * 2);
        storage.setUnits("bytes");
        storage.setStatus("warning");
        updateModel.setStorage(storage);
        UsageUpdateItem traffic = new UsageUpdateItem();
        traffic.setPercentage(100);
        traffic.setUsed(2000);
        traffic.setLimit(2000);
        traffic.setUnits("bytes");
        traffic.setStatus("blocked");
        updateModel.setTraffic(traffic);
        String warningMessage = DecorateUtil.getWarningMessage(updateModel);
        StringBuilder expectedMessage = new StringBuilder("Quota Alert: ");
        String format = String
                .format("Storage (%s out of %s)", DecorateUtil.getUserReadableSize(storage.getUsed()),
                        DecorateUtil.getUserReadableSize(storage.getLimit()));
        expectedMessage.append(format).append(". To enjoy unlimited usage, Upgrade");
        Assert.assertEquals(warningMessage, expectedMessage.toString());
    }

    @Test
    public void testGetErrorMessage() {
        UsageUpdateModel updateModel = new UsageUpdateModel();
        UsageUpdateItem storage = new UsageUpdateItem();
        storage.setPercentage(100);
        storage.setUsed(2000);
        storage.setLimit(2000);
        storage.setUnits("bytes");
        storage.setStatus("blocked");
        updateModel.setStorage(storage);
        UsageUpdateItem traffic = new UsageUpdateItem();
        traffic.setPercentage(100);
        traffic.setUsed(2000);
        traffic.setLimit(2000);
        traffic.setUnits("bytes");
        traffic.setStatus("blocked");
        updateModel.setTraffic(traffic);
        String errorMessage = DecorateUtil.getErrorMessage(updateModel);
        Assert.assertEquals(errorMessage,
                "You have reached your monthly quota for Data Transfer and Storage. To enjoy unlimited usage, Upgrade");
    }

    @Test
    public void testGetUserReadableSize() {
        UsageUpdateModel updateModel = new UsageUpdateModel();
        String userReadableSize = DecorateUtil.getUserReadableSize(2000000);
        Assert.assertEquals(userReadableSize, "1.91 MB");

        userReadableSize = DecorateUtil.getUserReadableSize(1048576);
        Assert.assertEquals(userReadableSize, "1.00 MB");
        double size = 1024L * 1024L * 1024L * 1024L;
        userReadableSize = DecorateUtil.getUserReadableSize(size);
        Assert.assertEquals(userReadableSize, "1.00 TB");

        size = 1024L * 1024L * 1024L;
        userReadableSize = DecorateUtil.getUserReadableSize(size);
        Assert.assertEquals(userReadableSize, "1.00 GB");

        size = 1024L * 1024L * 1024L * 5;
        userReadableSize = DecorateUtil.getUserReadableSize(size);
        Assert.assertEquals(userReadableSize, "5.00 GB");

        size = 1024L * 1024L * 1024L * 2;
        userReadableSize = DecorateUtil.getUserReadableSize(size);
        Assert.assertEquals(userReadableSize, "2.00 GB");
    }

    @Test
    public void testBuildMessage() {
        UsageUpdateModel updateModel = new UsageUpdateModel();
        StringBuilder builder = new StringBuilder("Test ");
        List<String> messages = new ArrayList<>();
        DecorateUtil.buildMessage(builder, messages);
        Assert.assertEquals(builder.toString(), "Test ");

        messages.add("One");
        DecorateUtil.buildMessage(builder, messages);
        Assert.assertEquals(builder.toString(), "Test One");

        builder = new StringBuilder("Test ");
        messages.add("Two");
        DecorateUtil.buildMessage(builder, messages);
        Assert.assertEquals(builder.toString(), "Test One and Two");

        builder = new StringBuilder("Test ");
        messages.add("Three");
        DecorateUtil.buildMessage(builder, messages);
        Assert.assertEquals(builder.toString(), "Test One, Two and Three");
    }
}