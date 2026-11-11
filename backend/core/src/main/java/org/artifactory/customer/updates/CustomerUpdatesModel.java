package org.artifactory.customer.updates;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Holds the customer's information we get from the store
 *
 * @author Omri Ziv
 */
@Data
@NoArgsConstructor
public class CustomerUpdatesModel {
    private UsageUpdateModel usage;
    private boolean freeEmailValidated = true;
}
