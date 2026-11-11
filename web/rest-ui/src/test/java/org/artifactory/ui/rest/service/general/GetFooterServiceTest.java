package org.artifactory.ui.rest.service.general;

import jakarta.annotation.Nonnull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.AddonsWebManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.addon.CoreAddons;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.subscriptions.SubscriptionsService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.platform.PlatformGeneralConfig;
import org.artifactory.platform.PlatformSystemMessageConfig;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.access.AccessService;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.general.FooterImpl;
import org.jfrog.access.client.platformconfig.GrpcPlatformConfigClient;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author dudim
 */
@Test
public class GetFooterServiceTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactoryContext context;
    private AutoCloseable mocks;

    @BeforeMethod
    private void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        AccessService accessService = mock(AccessService.class);
        GrpcPlatformConfigClient grpcPlatformConfigClient = mock(GrpcPlatformConfigClient.class);
        when(accessService.getPlatformConfigClient()).thenReturn(grpcPlatformConfigClient);
        when(grpcPlatformConfigClient.getConfig(anyString(), eq(PlatformGeneralConfig.class)))
                .thenReturn(Optional.empty());
        when(grpcPlatformConfigClient.getConfig(anyString(), eq(PlatformSystemMessageConfig.class)))
                .thenReturn(Optional.empty());
        when(context.beanForType(AccessService.class)).thenReturn(accessService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test(dataProvider = "typesProvider")
    public void testGetVersionInfoFreeTier(SubscriptionType type) {
        FooterImpl iModel = executeAndGetFooter(type);
        if (type != null) {
            Assert.assertEquals(iModel.getSubscriptionTierType(), type.getString());
        } else {
            Assert.assertNull(iModel.getSubscriptionTierType());
        }
    }

    @Test
    public void testGetUserEmail() {
        Authentication authentication = mock(Authentication.class);
        SimpleUser simpleUser = mock(SimpleUser.class);
        when(simpleUser.getEmail()).thenReturn("a@a.com");
        when(authentication.getPrincipal()).thenReturn(simpleUser);
        SecurityContext mock = mock(SecurityContext.class);
        SecurityContextHolder.setContext(mock);
        when(mock.getAuthentication()).thenReturn(authentication);
        GetFooterService footerService =
                new GetFooterService(mock(CentralConfigService.class), mock(SecurityService.class),
                        mock(MetadataEventService.class), mock(PlatformConfigService.class));
        Assert.assertEquals(footerService.getUserEmail(), "a@a.com");
        SecurityContextHolder.clearContext();
    }

    @Test
    public void testGetUserEmailNull() {
        SecurityContext mock = mock(SecurityContext.class);
        SecurityContextHolder.setContext(mock);
        when(mock.getAuthentication()).thenReturn(null);
        GetFooterService footerService =
                new GetFooterService(mock(CentralConfigService.class), mock(SecurityService.class),
                        mock(MetadataEventService.class), mock(PlatformConfigService.class));
        Assert.assertNull(footerService.getUserEmail());
        SecurityContextHolder.clearContext();
    }

    @DataProvider
    public Object[][] typesProvider() {
        return new Object[][] {
                {SubscriptionType.FREE},
                {SubscriptionType.FREE_XRAY},
                {SubscriptionType.PRO_TEAM},
                {SubscriptionType.ENTERPRISE_TEAM},
                {SubscriptionType.ENTERPRISE_PLUS},
                {null}
        };
    }

    @DataProvider
    public Object[][] archiveIndexProvider() {
        return new Object[][] {
                {true},
                {false},
        };
    }

    private void prepareAddOns(GetFooterService footerServiceSpy) {
        AddonsManager addonsManagerMock = mock(AddonsManager.class);
        CoreAddons coreAddons = mock(CoreAddons.class);
        when(addonsManagerMock.addonByType(CoreAddons.class)).thenReturn(coreAddons);
        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        when(addonsManagerMock.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(addonsManagerMock.getArtifactoryRunningMode()).thenReturn(ArtifactoryRunningMode.PRO);
        doReturn(addonsManagerMock).when(footerServiceSpy).getAddonManager();
    }

    @Nonnull
    private CentralConfigService prepareCentralConfigDescriptor() {
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        TrashcanConfigDescriptor trashcanConfigDescriptor = mock(TrashcanConfigDescriptor.class);
        when(centralConfigDescriptor.getTrashcanConfig()).thenReturn(trashcanConfigDescriptor);
        SecurityDescriptor securityDescriptor = mock(SecurityDescriptor.class);
        when(centralConfigDescriptor.getSecurity()).thenReturn(securityDescriptor);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        return centralConfigService;
    }

    private void prepareContext(SubscriptionType type) {
        AddonsWebManager addonsWebManager = mock(AddonsWebManager.class);
        ArtifactoryBinaryService binaryService = mock(ArtifactoryBinaryService.class);
        SubscriptionsService subscriptionsService = mock(SubscriptionsService.class);
        when(subscriptionsService.getSubscriptionType()).thenReturn(type);
        when(context.getArtifactoryHome()).thenReturn(homeStub);
        when(context.beanForType(AddonsWebManager.class)).thenReturn(addonsWebManager);
        when(context.beanForType(ArtifactoryBinaryService.class)).thenReturn(binaryService);
        when(context.beanForType(SubscriptionsService.class)).thenReturn(subscriptionsService);
    }

    private FooterImpl executeAndGetFooter(SubscriptionType subscriptionType) {
        CentralConfigService centralConfigService = prepareCentralConfigDescriptor();
        MetadataEventService metadataEventService = mock(MetadataEventService.class);
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnonymous()).thenReturn(false);
        PlatformConfigService platformConfigService = mock(PlatformConfigService.class);
        when(platformConfigService.getPlatformGeneralConfig()).thenReturn(PlatformGeneralConfig.builder().build());
        when(platformConfigService.getPlatformSystemMessageConfig())
                .thenReturn(PlatformSystemMessageConfig.builder().build());
        prepareContext(subscriptionType);
        GetFooterService footerServiceSpy = spy(new GetFooterService(centralConfigService, securityService,
                metadataEventService, platformConfigService));
        prepareAddOns(footerServiceSpy);
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryRestResponse artifactoryRestResponse = new ArtifactoryRestResponse();
        footerServiceSpy.execute(null, artifactoryRestResponse);
        return (FooterImpl) artifactoryRestResponse.getIModel();
    }
}