package org.artifactory.ui.rest.service.subscriptions;

import org.artifactory.api.subscriptions.SubscriptionsService;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.subscriptions.UpgradeInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;

/**
 * @author Alexei Vainshtein
 */
class GetUpgradeInfoServiceTest {

    @Test
    void testExecute() throws Exception {
        SubscriptionsService subscriptionsService = Mockito.mock(SubscriptionsService.class);
        Mockito.when(subscriptionsService.getPurchaseUrl()).thenReturn("http://url");
        GetUpgradeInfoService getUpgradeInfoService = new GetUpgradeInfoService(subscriptionsService);
        ArtifactoryRestResponse artifactoryResponse = new ArtifactoryRestResponse();
        getUpgradeInfoService.execute(null, artifactoryResponse);
        UpgradeInfo iModel = (UpgradeInfo) artifactoryResponse.getIModel();
        Assertions.assertEquals(iModel.getPurchaseUrl(), "http://url");
    }

    @Test
    void testExecuteWithException() throws Exception {
        SubscriptionsService subscriptionsService = Mockito.mock(SubscriptionsService.class);
        Mockito.when(subscriptionsService.getPurchaseUrl()).thenThrow(new IOException("This is it"));
        GetUpgradeInfoService getUpgradeInfoService = new GetUpgradeInfoService(subscriptionsService);
        ArtifactoryRestResponse artifactoryResponse = new ArtifactoryRestResponse();
        getUpgradeInfoService.execute(null, artifactoryResponse);
        UpgradeInfo iModel = (UpgradeInfo) artifactoryResponse.getIModel();
        Assertions.assertNull(iModel);
        String error = artifactoryResponse.getFeedbackMsg().getError();
        Assertions.assertEquals(error, "Exception occurred while trying to generate the upgrade url: This is it");
    }
}