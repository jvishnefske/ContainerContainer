package org.artifactory.customer.updates;

import org.springframework.stereotype.Service;

/**
 * @author Omri Ziv
 */
@Service
public class DefaultCustomerUpdateServiceImpl implements CustomerUpdatesService {

    @Override
    public CustomerUpdatesModel getCustomerUpdates() {
        return null;
    }

    @Override
    public CustomerUpdatesModel getCustomerUpdatesFetchIfNeeded() {
        return null;
    }

    @Override
    public void fetchCustomerUpdatesAndStore() {

    }

    @Override
    public boolean isTrafficUsageBlocked() {
        return false;
    }

    @Override
    public boolean isEmailValidated() {
        return true;
    }

    @Override
    public void onContextCreated() {

    }
}
