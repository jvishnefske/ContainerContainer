package org.artifactory.rest.resource.system;

import org.apache.http.HttpStatus;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.security.access.AccessService;
import org.artifactory.util.KeyValueList;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import jakarta.ws.rs.core.Response;
import java.util.ArrayList;

/**
 * @author Alexei Vainshtein
 */
public class SystemPropertyResourceTest {

    private AccessService accessService;

    @BeforeClass
    public void setup() {
        ArtifactoryContext context = Mockito.mock(ArtifactoryContext.class);
        AolService aolService = Mockito.mock(AolService.class);
        this.accessService = Mockito.mock(AccessService.class);
        Mockito.when(aolService.isDashboardUser()).thenReturn(true);
        Mockito.when(aolService.isAol()).thenReturn(true);
        Mockito.when(context.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterClass
    public void teardown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testSystemUpdateProperty() {
        CentralConfigService centralConfigService = Mockito.mock(CentralConfigService.class);
        MutableCentralConfigDescriptor descriptor = Mockito.mock(MutableCentralConfigDescriptor.class);
        Mockito.doNothing().when(descriptor).addSystemProperty(Mockito.any());
        Mockito.when(centralConfigService.getMutableDescriptor()).thenReturn(descriptor);
        SystemPropertyResource propertyResource = new SystemPropertyResource(centralConfigService, accessService);
        KeyValueList keyValueList =
                new KeyValueList(ConstantValues.aolSubscriptionType.getPropertyName() + "=" +
                        SubscriptionType.FREE);

        Response response = propertyResource.updateSystemProperty(keyValueList);
        Assert.assertEquals(response.getStatus(), HttpStatus.SC_CREATED);
        Mockito.verify(descriptor, Mockito.times(1)).addSystemProperty(Mockito.any());
        Mockito.verify(descriptor, Mockito.times(1)).removeSystemProperty(Mockito.any());
        Mockito.verify(centralConfigService, Mockito.times(1))
                .saveEditedDescriptorAndReload(Mockito.any(), Mockito.any());
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Properties request only " +
            "supported to a specific system property. Found.*")
    public void testSystemUpdatePropertyWrongKey() {
        CentralConfigService centralConfigService = Mockito.mock(CentralConfigService.class);
        MutableCentralConfigDescriptor descriptor = Mockito.mock(MutableCentralConfigDescriptor.class);
        Mockito.doNothing().when(descriptor).addSystemProperty(Mockito.any());
        Mockito.when(centralConfigService.getMutableDescriptor()).thenReturn(descriptor);
        SystemPropertyResource propertyResource = new SystemPropertyResource(centralConfigService, accessService);
        KeyValueList keyValueList = new KeyValueList("key=value");
        propertyResource.updateSystemProperty(keyValueList);
    }

    @Test
    public void testRemoveSystemProperty() {
        CentralConfigService centralConfigService = Mockito.mock(CentralConfigService.class);
        MutableCentralConfigDescriptor descriptor = Mockito.mock(MutableCentralConfigDescriptor.class);
        Mockito.doNothing().when(descriptor).addSystemProperty(Mockito.any());
        Mockito.when(centralConfigService.getMutableDescriptor()).thenReturn(descriptor);
        SystemPropertyResource propertyResource = new SystemPropertyResource(centralConfigService, accessService);
        ArrayList<String> keys = new ArrayList<>();
        keys.add(ConstantValues.aolSubscriptionType.getPropertyName());
        Response response = propertyResource.deleteSystemProperty(keys);
        Assert.assertEquals(response.getStatus(), HttpStatus.SC_ACCEPTED);
        Mockito.verify(descriptor, Mockito.times(1)).removeSystemProperty(Mockito.any());
        Mockito.verify(centralConfigService, Mockito.times(1)).saveEditedDescriptorAndReload(Mockito.any());
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = "Properties request only " +
            "supported to a specific system property. Found.*")
    public void testRemoveSystemPropertyWrongKey() {
        CentralConfigService centralConfigService = Mockito.mock(CentralConfigService.class);
        MutableCentralConfigDescriptor descriptor = Mockito.mock(MutableCentralConfigDescriptor.class);
        Mockito.doNothing().when(descriptor).addSystemProperty(Mockito.any());
        Mockito.when(centralConfigService.getMutableDescriptor()).thenReturn(descriptor);
        SystemPropertyResource propertyResource = new SystemPropertyResource(centralConfigService, accessService);
        ArrayList<String> keys = new ArrayList<>();
        keys.add("key");
        propertyResource.deleteSystemProperty(keys);
    }
}