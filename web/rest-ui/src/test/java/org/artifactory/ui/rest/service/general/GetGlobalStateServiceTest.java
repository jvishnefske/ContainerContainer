package org.artifactory.ui.rest.service.general;

import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.general.GlobalStateModelImpl;
import org.artifactory.ui.rest.service.state.FooterMessage;
import org.artifactory.ui.rest.service.state.GlobalStateProviderService;
import org.artifactory.ui.rest.service.state.State;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexei Vainshtein
 */
public class GetGlobalStateServiceTest {

    @Test
    public void testExecuteNoSubscription() {
        GlobalStateProviderService messageProviderService = Mockito.mock(GlobalStateProviderService.class);
        List<FooterMessage> messages = new ArrayList<>();
        FooterMessage warning_message =
                FooterMessage.createWarning("warning message", null, FooterMessage.FooterMessageVisibility.user);
        messages.add(warning_message);
        GlobalStateModelImpl globalStateModel = new GlobalStateModelImpl();
        globalStateModel.setMessages(messages);
        globalStateModel.setState(new State());
        Mockito.when(messageProviderService.getGlobalStateAndMessages()).thenReturn(globalStateModel);
        GetGlobalStateService stateService = new GetGlobalStateService(messageProviderService);
        ArtifactoryRestResponse artifactoryResponse = new ArtifactoryRestResponse();
        stateService.execute(null, artifactoryResponse);
        GlobalStateModelImpl iModel = (GlobalStateModelImpl) artifactoryResponse.getIModel();

        Assert.assertNotNull(iModel.getState());
        Assert.assertNull(iModel.getState().getSubscriptionType());

        Assert.assertEquals(iModel.getMessages(), messages);
        Assert.assertEquals(iModel.getMessages().get(0), warning_message);
    }

    @Test
    public void testExecuteWithSubscription() {
        GlobalStateProviderService messageProviderService = Mockito.mock(GlobalStateProviderService.class);
        List<FooterMessage> messages = new ArrayList<>();
        FooterMessage warning_message =
                FooterMessage.createWarning("warning message", null, FooterMessage.FooterMessageVisibility.user);
        messages.add(warning_message);
        GlobalStateModelImpl globalStateModel = new GlobalStateModelImpl();
        globalStateModel.setState(new State(SubscriptionType.FREE));
        globalStateModel.setMessages(messages);
        Mockito.when(messageProviderService.getGlobalStateAndMessages()).thenReturn(globalStateModel);
        GetGlobalStateService stateService = new GetGlobalStateService(messageProviderService);
        ArtifactoryRestResponse artifactoryResponse = new ArtifactoryRestResponse();
        stateService.execute(null, artifactoryResponse);
        GlobalStateModelImpl iModel = (GlobalStateModelImpl) artifactoryResponse.getIModel();

        Assert.assertNotNull(iModel.getState());
        Assert.assertEquals(iModel.getState().getSubscriptionType(), SubscriptionType.FREE.getString());
        Assert.assertEquals(iModel.getMessages(), messages);
        Assert.assertEquals(iModel.getMessages().get(0), warning_message);
    }

    @Test
    public void testExecuteWithoutMessages() {
        GlobalStateProviderService messageProviderService = Mockito.mock(GlobalStateProviderService.class);
        GlobalStateModelImpl globalStateModel = new GlobalStateModelImpl();
        globalStateModel.setState(new State(SubscriptionType.FREE));
        Mockito.when(messageProviderService.getGlobalStateAndMessages()).thenReturn(globalStateModel);
        GetGlobalStateService stateService = new GetGlobalStateService(messageProviderService);

        ArtifactoryRestResponse artifactoryResponse = new ArtifactoryRestResponse();
        stateService.execute(null, artifactoryResponse);
        GlobalStateModelImpl iModel = (GlobalStateModelImpl) artifactoryResponse.getIModel();

        Assert.assertNotNull(iModel.getState());
        Assert.assertEquals(iModel.getState().getSubscriptionType(), SubscriptionType.FREE.getString());
        Assert.assertNull(iModel.getMessages());
    }
}