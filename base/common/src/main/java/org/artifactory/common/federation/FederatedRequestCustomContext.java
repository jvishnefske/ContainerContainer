package org.artifactory.common.federation;
import org.springframework.stereotype.Component;

@Component
public class FederatedRequestCustomContext {

    private static final ThreadLocal<Boolean> skipDisabledCheck = ThreadLocal.withInitial(() -> false);

    public void setSkipDisabledCheck(boolean skip) {
        skipDisabledCheck.set(skip);
    }

    public boolean isSkipDisabledCheck() {
        return skipDisabledCheck.get();
    }

    public void clear() {
        skipDisabledCheck.remove();
    }
}


