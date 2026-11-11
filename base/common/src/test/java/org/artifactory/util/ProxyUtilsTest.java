package org.artifactory.util;

import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.jfrog.access.common.ServiceType;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.artifactory.util.ProxyUtils.validateProxyServices;

/**
 * @author Yuval Reches
 */
public class ProxyUtilsTest {

    @Test(dataProvider = "servicesProvider")
    public void testValidateProxyServices(String services, boolean valid) {
        ProxyDescriptor proxy = new ProxyDescriptor();
        proxy.setServices(services);
        try {
            validateProxyServices(proxy);
        } catch (Exception e) {
            if (valid) {
                // Throwing the exception as we expected a valid service, but got an exception anyways
                throw e;
            }
        }
    }

    @DataProvider(name = "servicesProvider")
    public static Object[][] servicesProvider() {
        return new Object[][] {
                {ServiceType.XRAY, true},
                {ServiceType.ARTIFACTORY, true},
                {ServiceType.DISTRIBUTION, true},
                {ServiceType.MISSION_CONTROL, true},
                {ServiceType.ACCESS, false},
                {ServiceType.FRONTEND, false},
                {String.join(",", ServiceType.ARTIFACTORY, ServiceType.XRAY), true},
                {String.join(",", ServiceType.ARTIFACTORY, "Gandalf"), false},
                {"Sauron", false},
                {"", true},
                {null, true},
                {",", true},
                {";", true},
                {"\n", true},
                {" ", true},
                {" a", false},
        };
    }
}