package org.artifactory.layout;

import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.repo.service.trash.TrashServiceImpl;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Shay Bagants
 */
public class TrashcanConfigurationInterceptorTest {

    @Test(dataProvider = "provideValidRetentionDays")
    public void testOnBeforeSave(int retentionDays) {
        TrashServiceImpl trashService = new TrashServiceImpl(null, null, null, null, null, null, null);
        TrashcanConfigurationInterceptor interceptor = new TrashcanConfigurationInterceptor(trashService);
        CentralConfigDescriptorImpl centralConfigDescriptor = new CentralConfigDescriptorImpl();
        configureTrashcan(retentionDays, centralConfigDescriptor);

        interceptor.onBeforeSave(centralConfigDescriptor);
        Assert.assertNotNull(centralConfigDescriptor.getTrashcanConfig());
    }

    @DataProvider
    public static Object[][] provideValidRetentionDays() {
        return new Object[][] {
                {14},
                {22},
                {400},
                {500},
                {1000},
                {Days.daysBetween(LocalDate.now().minusDays(300), LocalDate.now()).getDays()},
                {Days.daysBetween(LocalDate.now().minusDays(900), LocalDate.now()).getDays()},
                {Days.daysBetween(LocalDate.now().minusYears(5), LocalDate.now()).getDays()}
        };
    }

    @Test(dataProvider = "provideInvalidRetentionDays", expectedExceptions = ConfigurationException.class,
            expectedExceptionsMessageRegExp = "Illegal retention date. Retention period goes back too far.")
    public void testOnBeforeSaveInvalidRetention(int retentionDays) {
        TrashServiceImpl trashService = new TrashServiceImpl(null, null, null, null, null, null, null);
        TrashcanConfigurationInterceptor interceptor = new TrashcanConfigurationInterceptor(trashService);
        CentralConfigDescriptorImpl centralConfigDescriptor = new CentralConfigDescriptorImpl();
        configureTrashcan(retentionDays, centralConfigDescriptor);

        interceptor.onBeforeSave(centralConfigDescriptor);
        Assert.assertNotNull(centralConfigDescriptor.getTrashcanConfig());
    }

    @DataProvider
    public static Object[][] provideInvalidRetentionDays() {
        return new Object[][] {
                {Days.daysBetween(new LocalDate(2003, 1, 1).minusDays(1), LocalDate.now()).getDays() },
                {Days.daysBetween(new LocalDate(1993, 1, 1), LocalDate.now()).getDays()},
                {Days.daysBetween(new LocalDate(1973, 1, 1), LocalDate.now()).getDays()},
        };
    }

    private void configureTrashcan(int retentionDays, CentralConfigDescriptorImpl centralConfigDescriptor) {
        TrashcanConfigDescriptor trashcanConfigDescriptor = new TrashcanConfigDescriptor();
        trashcanConfigDescriptor.setEnabled(true);
        trashcanConfigDescriptor.setRetentionPeriodDays(retentionDays);
        centralConfigDescriptor.setTrashcanConfig(trashcanConfigDescriptor);
    }
}