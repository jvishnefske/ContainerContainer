package org.artifactory.ui.rest.service.subscriptions;

import org.artifactory.api.subscriptions.SubscriptionsService;
import org.artifactory.customer.updates.CustomerUpdatesModel;
import org.artifactory.customer.updates.CustomerUpdatesService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.subscriptions.BlockedUsage;
import org.artifactory.util.AolUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Omri Ziv
 */
@Component
public class GetBlockedUsages implements RestService {
    private static final Logger log = LoggerFactory.getLogger(GetBlockedUsages.class);

    private CustomerUpdatesService customerUpdatesService;
    private SubscriptionsService subscriptionsService;

    public GetBlockedUsages(CustomerUpdatesService customerUpdatesService,
            SubscriptionsService subscriptionsService) {
        this.customerUpdatesService = customerUpdatesService;
        this.subscriptionsService = subscriptionsService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {

        if (!AolUtils.isAol() || !subscriptionsService.isFreeSubscription()) {
            response.error("This request is only for Free Tier.");
            return;
        }
        CustomerUpdatesModel customerUpdatesModel = customerUpdatesService.getCustomerUpdatesFetchIfNeeded();
        if (customerUpdatesModel == null || customerUpdatesModel.getUsage() == null) {
            log.debug("No customer information was found.");
            response.iModel(new BlockedUsage());
            return;
        }
        BlockedUsage blockedUsage = BlockedUsage.fromUsageModel(customerUpdatesModel.getUsage());
        response.iModel(blockedUsage);
    }
}
