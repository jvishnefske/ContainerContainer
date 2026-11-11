package org.artifactory.addon.observability;

import lombok.experimental.UtilityClass;

/**
 * @author Tomer Slutsky
 */
@UtilityClass
public class ObservabilityScopeToken {

    public final String USAGE_WRITE = "internal:usage:w";
    public final String USAGE_READ = "internal:usage:r";
    public final String LOG_SHIPPING_WRITE = "internal:logshipping:w";
    public final String BILLING_WRITE = "internal:billing:w";
}
