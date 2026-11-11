package org.artifactory.ui.rest.service.jfevent;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.jfevent.JfEventAddon;
import org.artifactory.addon.jfevent.JfEventDomainInfo;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Yevdo Abramov
 * Created on 28/05/2020
 */
public class GetDomainsListJfEventServiceTest {

    @Mock
    private AddonsManager addonsManager;

    private GetDomainsListJfEventService getDomainsListJfEventService;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        getDomainsListJfEventService = new GetDomainsListJfEventService(addonsManager);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void testExecute() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse restResponse = new ArtifactoryRestResponse();

        JfEventAddon jfEventAddon = mock(JfEventAddon.class);
        List<JfEventDomainInfo> domainsList = Arrays.asList(new JfEventDomainInfo(), new JfEventDomainInfo());
        when(addonsManager.addonByType(JfEventAddon.class)).thenReturn(jfEventAddon);
        when(jfEventAddon.getDomainsList()).thenReturn(domainsList);

        getDomainsListJfEventService.execute(request, restResponse);
        verify(addonsManager, times(1)).addonByType(JfEventAddon.class);
        verify(jfEventAddon, times(1)).getDomainsList();

        List<JfEventDomainInfo> responseDomainsList = (List<JfEventDomainInfo>) restResponse.getIModel();
        assertEquals(responseDomainsList.size(), domainsList.size());
    }
}