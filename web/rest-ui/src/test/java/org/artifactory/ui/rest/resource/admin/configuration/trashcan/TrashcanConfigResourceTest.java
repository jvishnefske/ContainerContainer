package org.artifactory.ui.rest.resource.admin.configuration.trashcan;

import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.service.admin.configuration.ConfigServiceFactory;
import org.artifactory.ui.rest.service.admin.configuration.trashcan.DisableTrashcanStrategyService;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrashcanConfigResourceTest {

    @Test
    void disableStrategy() {
        ConfigServiceFactory factory = mock(ConfigServiceFactory.class);
        TrashcanConfigResourceTestSpy spy = spy(new TrashcanConfigResourceTestSpy(factory));
        doReturn(null).when(spy).runService(any());
        doReturn(null).when(spy).runService(any(), any());
        DisableTrashcanStrategyService service = mock(DisableTrashcanStrategyService.class);
        when(factory.getDisableTrashcanStrategy()).thenReturn(service);

        spy.disableStrategy();

        verify(spy, times(1)).runService(service);
    }

    static class TrashcanConfigResourceTestSpy extends TrashcanConfigResource {

        public TrashcanConfigResourceTestSpy(ConfigServiceFactory factory) {
            super(factory);
        }

        @Override
        protected <Y> Response runService(RestService<Y> service, Y model) {
            return super.runService(service, model);
        }

        @Override
        protected <Y> Response runService(RestService<Y> service) {
            return super.runService(service);
        }
    }

}