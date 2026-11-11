package org.artifactory.customer.updates;

import org.artifactory.spring.ContextCreationListener;

/**
 * A service to fetch Customer updates from store.
 *
 * @author Omri Ziv
 */
public interface CustomerUpdatesService extends ContextCreationListener {

    String EMAIL_NOT_VALIDATED_MESSAGE = "Your email address has not been verified yet and your subscription might " +
            "get cancelled. Resend email verification";

    CustomerUpdatesModel getCustomerUpdates();

    CustomerUpdatesModel getCustomerUpdatesFetchIfNeeded();

    void fetchCustomerUpdatesAndStore();

    boolean isTrafficUsageBlocked();

    /**
     * Mark the email of a free tier account as validated
     */
    default void validateEmail() {
    }

    /**
     * Free tier accounts should validate their email address, else a warning message shall be displayed
     * @return true if the email has been validated in the store, or if we still have not fetched the data
     */
    boolean isEmailValidated();
}
